/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include "com_intel_qat_InternalJNI.h"

#include <qatzip.h>
#include <stdint.h>
#include <stdlib.h>
#include <threads.h>
#include <zstd.h>

#include "qatseqprod.h"
#include "util.h"

#ifndef CPA_DC_API_VERSION_AT_LEAST
#define CPA_DC_API_VERSION_AT_LEAST(major, minor) \
  (CPA_DC_API_VERSION_NUM_MAJOR > major ||        \
   (CPA_DC_API_VERSION_NUM_MAJOR == major &&      \
    CPA_DC_API_VERSION_NUM_MINOR >= minor))
#endif

#if CPA_DC_API_VERSION_AT_LEAST(3, 1)
#define COMP_LVL_MAXIMUM QZ_LZS_COMP_LVL_MAXIMUM
#else
#define COMP_LVL_MAXIMUM QZ_DEFLATE_COMP_LVL_MAXIMUM
#endif

#define DEFLATE_ALGORITHM 0
#define LZ4_ALGORITHM 1
#define ZSTD_ALGORITHM 2

/**
 * ZLIB data_format ordinal QatZipper.DataFormat.
 */
#define ZLIB_DATA_FORMAT 4

/**
 * The fieldID for java.nio.ByteBuffer/position
 */
static jfieldID nio_bytebuffer_position_id;

/**
 * The fieldID for com.intel.qat.QatZipper/bytesRead
 */
static jfieldID qzip_bytes_read_id;

/**
 * Flag to indicate that Zstandard algorithm is set.
 */
static _Thread_local int g_algorithm_is_zstd;

/**
 * Refers to the current sequence producer state.
 */
static _Thread_local void *g_zstd_seqprod_state;

/**
 * QZSTD_startQatDevice() must be called only once!
 */
static once_flag init_qzstd_flag = ONCE_FLAG_INIT;
static int g_zstd_is_device_available;
static void initialize_qzstd_once(void) {
  g_zstd_is_device_available = QZSTD_startQatDevice();
}

/**
 * A type representing a unique QAT session for specific compression params.
 */
typedef struct {
  int key;
  int reference_count;
  QzSession_T *qz_session;
} Session_T;

#define MAX_SESSIONS_PER_THREAD 32
static _Thread_local Session_T session_cache[MAX_SESSIONS_PER_THREAD];
static _Thread_local int session_cache_counter;

static Session_T *get_session(int key) {
  for (int i = 0; i < session_cache_counter; ++i) {
    if (session_cache[i].key == key) {
      return &session_cache[i];
    }
  }
  return NULL;
}

/**
 * Constructs a unique key for a session from the compression params.
 */
static int hash_params(int algorithm, int level, int sw_backup,
                       int polling_mode, int data_format, int hw_buff_sz) {
  int key = 0;

  // 4 bits for all except hw_buff_size
  key ^= algorithm;
  key ^= level << 4;
  key ^= sw_backup << 8;
  key ^= polling_mode << 12;
  key ^= data_format << 16;
  key ^= (hw_buff_sz >> 10) << 20;  // remove the KB

  return key;
}

/**
 * Sets up a QAT session for DEFLATE.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param level the compression level to use.
 */
static int setup_deflate_session(QzSession_T *qz_session, int level,
                                 unsigned char sw_backup, int polling_mode,
                                 int data_format, int hw_buff_sz) {
  QzSessionParamsDeflate_T deflate_params;

  int status = qzGetDefaultsDeflate(&deflate_params);
  if (status != QZ_OK) {
    return status;
  }

  deflate_params.data_fmt = data_format;
  deflate_params.common_params.hw_buff_sz = hw_buff_sz;
  deflate_params.common_params.comp_lvl = level;
  deflate_params.common_params.sw_backup = sw_backup;
  deflate_params.common_params.polling_mode =
      polling_mode == 0 ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;

  return qzSetupSessionDeflate(qz_session, &deflate_params);
}

/**
 * Sets up a QAT session for DEFLATE zlib.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param level the compression level to use.
 */
static int setup_deflate_zlib_session(QzSession_T *qz_session, int level,
                                      unsigned char sw_backup,
                                      int polling_mode) {
  QzSessionParamsDeflateExt_T deflate_params;

  int rc = qzGetDefaultsDeflateExt(&deflate_params);
  if (rc != QZ_OK) {
    return rc;
  }

  deflate_params.deflate_params.common_params.comp_lvl = level;
  deflate_params.deflate_params.common_params.sw_backup = sw_backup;
  deflate_params.deflate_params.common_params.polling_mode =
      polling_mode == 0 ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;
  deflate_params.zlib_format = 1;
  deflate_params.stop_decompression_stream_end = 1;

  return qzSetupSessionDeflateExt(qz_session, &deflate_params);
}

/**
 * Sets up a QAT session for LZ4.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param level the compression level to use.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int setup_lz4_session(QzSession_T *qz_session, int level,
                             unsigned char sw_backup, int polling_mode) {
  QzSessionParamsLZ4_T lz4_params;

  int rc = qzGetDefaultsLZ4(&lz4_params);
  if (rc != QZ_OK) {
    return rc;
  }

  lz4_params.common_params.comp_lvl = level;
  lz4_params.common_params.sw_backup = sw_backup;
  lz4_params.common_params.polling_mode =
      polling_mode == 0 ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;

  return qzSetupSessionLZ4(qz_session, &lz4_params);
}

/**
 * Compresses a buffer pointed to by the given source pointer and writes it to
 * the destination buffer pointed to by the destination pointer. The read and
 * write of the source and destination buffers is bounded by the source and
 * destination lengths respectively.
 *
 * @param env a pointer to the JNI environment.
 * @param sess a pointer to the QzSession_T object.
 * @param src_ptr the source buffer.
 * @param src_len the size of the source buffer.
 * @param dst_ptr the destination buffer.
 * @param dst_len the size of the destination buffer.
 * @param bytes_read an out parameter that stores the bytes read from the
 * source buffer.
 * @param bytes_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of compression retries before we give up.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int compress(JNIEnv *env, QzSession_T *sess, unsigned char *src_ptr,
                    unsigned int src_len, unsigned char *dst_ptr,
                    unsigned int dst_len, int *bytes_read, int *bytes_written,
                    int retry_count) {
  // Save src_len and dst_len
  int src_len_l = src_len;
  int dst_len_l = dst_len;
  int rc = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);

  if (rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != rc) {
      src_len = src_len_l;
      dst_len = dst_len_l;
      rc = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);
      retry_count--;
    }
  }

  if (rc != QZ_OK) {
    throw_exception(env, rc, "Error occurred while compressing data.");
    return rc;
  }

  *bytes_read = src_len;
  *bytes_written = dst_len;

  return QZ_OK;
}

/**
 * Decmpresses a buffer pointed to by the given source pointer and writes it
 * to the destination buffer pointed to by the destination pointer. The read
 * and write of the source and destination buffers is bounded by the source
 * and destination lengths respectively.
 *
 * @param env a pointer to the JNI environment.
 * @param sess a pointer to the QzSession_T object.
 * @param src_ptr the source buffer.
 * @param src_len the size of the source buffer.
 * @param dst_ptr the destination buffer.
 * @param dst_len the size of the destination buffer.
 * @param bytes_read an out parameter that stores the bytes read from the
 * source buffer.
 * @param bytes_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of decompression retries before we give up.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int decompress(JNIEnv *env, QzSession_T *sess, unsigned char *src_ptr,
                      unsigned int src_len, unsigned char *dst_ptr,
                      unsigned int dst_len, int *bytes_read, int *bytes_written,
                      int retry_count) {
  // Save src_len and dst_len
  int src_len_l = src_len;
  int dst_len_l = dst_len;
  int rc = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);

  if (rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != rc && rc != QZ_BUF_ERROR &&
           rc != QZ_DATA_ERROR) {
      src_len = src_len_l;
      dst_len = dst_len_l;
      rc = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);
      retry_count--;
    }
  }

  if (rc != QZ_OK && rc != QZ_BUF_ERROR && rc != QZ_DATA_ERROR) {
    throw_exception(env, rc, "Error occurred while decompressing data.");
    return rc;
  }

  *bytes_read = src_len;
  *bytes_written = dst_len;

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    initFieldIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_initFieldIDs(JNIEnv *env,
                                                                   jclass clz) {
  (void)clz;

  nio_bytebuffer_position_id = (*env)->GetFieldID(
      env, (*env)->FindClass(env, "java/nio/ByteBuffer"), "position", "I");

  qzip_bytes_read_id = (*env)->GetFieldID(
      env, (*env)->FindClass(env, "com/intel/qat/QatZipper"), "bytesRead", "I");
}

/*
 * Sets up a QAT session.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (Lcom/intel/qat/QatZipper;IIIIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_setup(
    JNIEnv *env, jclass clz, jobject qat_zipper, jint comp_algorithm,
    jint level, jint sw_backup, jint polling_mode, jint data_format,
    jint hw_buff_sz) {
  (void)clz;

  // Check if compression level is valid
  if (level < 1 || level > COMP_LVL_MAXIMUM) {
    throw_exception(env, QZ_PARAMS, "Invalid compression level given.");
    return QZ_FAIL;
  }

  // If the algorithm is ZSTD, initialize device and return
  if (comp_algorithm == ZSTD_ALGORITHM) {
    call_once(&init_qzstd_flag, initialize_qzstd_once);
    g_zstd_is_device_available = QZSTD_startQatDevice();
    if (g_zstd_is_device_available == -1) {
      if (sw_backup == 0) {
        throw_exception(env, g_zstd_is_device_available,
                        "Initializing QAT HW failed.");
        return QZ_FAIL;
      }
      // NO HW but sw_backup is on, use software!
      return QZ_NO_HW;
    }
    g_algorithm_is_zstd = 1;
    return QZ_OK;
  }

  int rc = QZ_OK;
  int key = hash_params(comp_algorithm, level, sw_backup, polling_mode,
                        data_format, hw_buff_sz);
  Session_T *session_ptr = get_session(key);
  if (!session_ptr || !session_ptr->qz_session) {
    if (session_cache_counter == MAX_SESSIONS_PER_THREAD) {
      throw_exception(env, QZ_FAIL,
                      "Number of active QAT session exceeded limit.");
      return QZ_FAIL;
    }

    session_ptr = &session_cache[session_cache_counter];
    ++session_cache_counter;

    session_ptr->key = key;
    session_ptr->qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

    rc = qzInit(session_ptr->qz_session, (unsigned char)sw_backup);
    if (rc != QZ_OK && rc != QZ_DUPLICATE) {
      throw_exception(env, rc, "Initializing QAT HW failed.");
      return rc;
    }

    if (comp_algorithm == DEFLATE_ALGORITHM) {
      if (data_format != ZLIB_DATA_FORMAT) {
        rc = setup_deflate_session(session_ptr->qz_session, level,
                                   (unsigned char)sw_backup, polling_mode,
                                   data_format, hw_buff_sz);
      } else {
        rc = setup_deflate_zlib_session(session_ptr->qz_session, level,
                                        (unsigned char)sw_backup, polling_mode);
      }
    } else {
      rc = setup_lz4_session(session_ptr->qz_session, level,
                             (unsigned char)sw_backup, polling_mode);
    }
  }
  session_ptr->reference_count++;

  if (rc != QZ_OK) {
    qzClose(session_ptr->qz_session);
    session_ptr->key = 0;
    session_ptr->qz_session = NULL;
    throw_exception(env, rc, "Error occurred while setting up a session.");
    return rc;
  }

  jclass qz_clz = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  jfieldID qz_session_field = (*env)->GetFieldID(env, qz_clz, "session", "J");
  (*env)->SetLongField(env, qat_zipper, qz_session_field, (jlong)session_ptr);

  return QZ_OK;
}

/*
 * Compresses a byte array.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * Signature: (Lcom/intel/qat/QatZipper;J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteArray(
    JNIEnv *env, jclass clz, jobject qat_zipper, jlong sess, jbyteArray src_arr,
    jint src_pos, jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
           dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Update qatZipper.bytesRead
  (*env)->SetIntField(env, qat_zipper, qzip_bytes_read_id, (jint)bytes_read);

  return bytes_written;
}

/*
 * Decompresses a byte array.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteArray
 * Signature: (Lcom/intel/qat/QatZipper;J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArray(
    JNIEnv *env, jclass clz, jobject qat_zipper, jlong sess, jbyteArray src_arr,
    jint src_pos, jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
             dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Update qat_zipper.bytesRead
  (*env)->SetIntField(env, qat_zipper, qzip_bytes_read_id, (jint)bytes_read);

  return bytes_written;
}

/*
 * Compresses a byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuffer(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
           dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 * Decompresses a byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuffer(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
             dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 *  Compresses a direct byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
jint JNICALL Java_com_intel_qat_InternalJNI_compressDirectByteBuffer(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jint src_pos,
    jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
           dst_len, &bytes_read, &bytes_written, retry_count);

  // set src and dest buffer positions
  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 *  Decompresses a direct byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBuffer(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jint src_pos,
    jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
             dst_len, &bytes_read, &bytes_written, retry_count);

  // set src and dest buffer positions
  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBufferSrc
 * Signature: (JLjava/nio/ByteBuffer;II[BIII)I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_compressDirectByteBufferSrc(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
           dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBufferSrc
 * Signature: (JLjava/nio/ByteBuffer;II[BIII)I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBufferSrc(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
             dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBufferDst
 * Signature: (JLjava/nio/ByteBuffer;[BIILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_compressDirectByteBufferDst(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
           dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBufferDst
 * Signature: (JLjava/nio/ByteBuffer;[BIILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBufferDst(
    JNIEnv *env, jclass clz, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
             dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 * Evaluates the maximum compressed size for the given buffer size.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(
    JNIEnv *env, jclass clz, jlong sess, jlong src_size) {
  (void)env;
  (void)clz;

  return qzMaxCompressedLength(src_size, ((Session_T *)sess)->qz_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdGetSeqProdFunction
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_intel_qat_InternalJNI_zstdGetSeqProdFunction(JNIEnv *env, jclass clz) {
  (void)env;
  (void)clz;

  return (jlong)qatSequenceProducer;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdCreateSeqProdState
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_intel_qat_InternalJNI_zstdCreateSeqProdState(JNIEnv *env, jclass clz) {
  (void)env;
  (void)clz;
  if (g_zstd_is_device_available != -1) {
    g_zstd_seqprod_state = QZSTD_createSeqProdState();
    return (jlong)g_zstd_seqprod_state;
  }
  return 0;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdFreeSeqProdState
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_zstdFreeSeqProdState(
    JNIEnv *env, jclass clz, jlong seqprod_state) {
  (void)env;
  (void)clz;
  if (g_zstd_is_device_available != -1) {
    // Note: seqprod_state == g_zstd_seqprod_state
    QZSTD_freeSeqProdState((void *)(seqprod_state));
  }
  g_zstd_seqprod_state = NULL;
}

/*
 * Tear downs the given QAT session.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                               jclass clz,
                                                               jlong sess) {
  (void)clz;

  Session_T *session_ptr = (Session_T *)sess;
  if (!session_ptr || !session_ptr->qz_session) {
    return QZ_OK;
  }

  QzSession_T *qz_session = ((Session_T *)session_ptr)->qz_session;
  if (session_ptr->reference_count > 1) {
    session_ptr->reference_count--;
    return QZ_OK;
  }

  int rc = qzTeardownSession(qz_session);
  if (rc != QZ_OK) {
    throw_exception(env, rc, "Error occurred while tearing down session.");
    return 0;
  }

  --session_cache_counter;
  session_ptr->reference_count = 0;
  session_ptr->qz_session = NULL;

  return QZ_OK;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;

  if (g_algorithm_is_zstd && g_zstd_is_device_available != -1) {
    if (g_zstd_seqprod_state) QZSTD_freeSeqProdState(g_zstd_seqprod_state);
    g_zstd_seqprod_state = NULL;
    QZSTD_stopQatDevice();
  }
}
