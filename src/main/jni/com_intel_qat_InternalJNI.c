/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include "com_intel_qat_InternalJNI.h"

#include <stdlib.h>

#include "qatseqprod.h"
#include "qatzip.h"
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

/**
 * The fieldID for java.nio.ByteBuffer/position
 */
static jfieldID nio_bytebuffer_position_id;

/**
 * The fieldID for com.intel.qat.QatZipper/bytesRead
 */
static jfieldID qzip_bytes_read_id;

/**
 * Sets up a QAT session for DEFLATE.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param level the compression level to use.
 */
static int setup_deflate_session(QzSession_T *qz_session, int level,
                                 unsigned char sw_backup, int polling_mode) {
  QzSessionParamsDeflate_T deflate_params;

  int status = qzGetDefaultsDeflate(&deflate_params);
  if (status != QZ_OK) return status;

  deflate_params.data_fmt = QZ_DEFLATE_GZIP_EXT;
  deflate_params.common_params.comp_lvl = level;
  deflate_params.common_params.sw_backup = sw_backup;
  deflate_params.common_params.polling_mode =
      polling_mode ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;

  return qzSetupSessionDeflate(qz_session, &deflate_params);
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

  int status = qzGetDefaultsLZ4(&lz4_params);
  if (status != QZ_OK) return status;

  lz4_params.common_params.comp_lvl = level;
  lz4_params.common_params.sw_backup = sw_backup;
  lz4_params.common_params.polling_mode =
      polling_mode ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;

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
 * @param bytes_read an out parameter that stores the bytes read from the source
 * buffer.
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
  int status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);

  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status) {
      src_len = src_len_l;
      dst_len = dst_len_l;
      status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);
      retry_count--;
    }
  }

  if (status != QZ_OK) {
    throw_exception(env, status, "Error occurred while compressing data.");
    return status;
  }

  *bytes_read = src_len;
  *bytes_written = dst_len;

  return QZ_OK;
}

/**
 * Decmpresses a buffer pointed to by the given source pointer and writes it to
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
 * @param bytes_read an out parameter that stores the bytes read from the source
 * buffer.
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
  int status = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);

  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status && status != QZ_BUF_ERROR &&
           status != QZ_DATA_ERROR) {
      src_len = src_len_l;
      dst_len = dst_len_l;
      status = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);
      retry_count--;
    }
  }

  if (status != QZ_OK && status != QZ_BUF_ERROR && status != QZ_DATA_ERROR) {
    throw_exception(env, status, "Error occurred while decompressing data.");
    return status;
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
 * Signature: (Lcom/intel/qat/QatZipper;IIII)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(
    JNIEnv *env, jclass clz, jobject qat_zipper, jint comp_algorithm,
    jint level, jint sw_backup, jint polling_mode) {
  (void)clz;
  // Check if compression level is valid
  if (level < 1 || level > COMP_LVL_MAXIMUM) {
    throw_exception(env, QZ_PARAMS, "Invalid compression level given.");
    return;
  }

  QzSession_T *qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

  int status = qzInit(qz_session, (unsigned char)sw_backup);
  if (status != QZ_OK && status != QZ_DUPLICATE) {
    throw_exception(env, status, "Initializing QAT HW failed.");
    return;
  }

  if (comp_algorithm == DEFLATE_ALGORITHM)
    status = setup_deflate_session(qz_session, level, (unsigned char)sw_backup,
                                   polling_mode);
  else
    status = setup_lz4_session(qz_session, level, (unsigned char)sw_backup,
                               polling_mode);

  if (status != QZ_OK) {
    qzClose(qz_session);
    throw_exception(env, status, "Error occurred while setting up a session.");
    return;
  }

  jclass qz_clz = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  jfieldID qz_session_field = (*env)->GetFieldID(env, qz_clz, "session", "J");
  (*env)->SetLongField(env, qat_zipper, qz_session_field, (jlong)qz_session);
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

  QzSession_T *qz_session = (QzSession_T *)sess;

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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;

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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  QzSession_T *qz_session = (QzSession_T *)sess;
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

  return qzMaxCompressedLength(src_size, (QzSession_T *)sess);
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

  QzSession_T *qz_session = (QzSession_T *)sess;
  if (!qz_session) return QZ_OK;

  int status = qzTeardownSession(qz_session);
  if (status != QZ_OK) {
    throw_exception(env, status, "Error occurred while tearing down session.");
    return 0;
  }

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdGetSeqProdFunction
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_intel_qat_InternalJNI_zstdGetSeqProdFunction(JNIEnv *env, jclass obj) {
  (void)env;
  (void)obj;

  return (jlong)qatSequenceProducer;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdStartDevice
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_zstdStartDevice(JNIEnv *env, jclass obj) {
  (void)env;
  (void)obj;

  return QZSTD_startQatDevice();
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdStopDevice
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_intel_qat_InternalJNI_zstdStopDevice(JNIEnv *env, jclass obj) {
  (void)env;
  (void)obj;

  QZSTD_stopQatDevice();
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdCreateSeqProdState
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_intel_qat_InternalJNI_zstdCreateSeqProdState(JNIEnv *env, jclass obj) {
  (void)env;
  (void)obj;

  return (jlong)QZSTD_createSeqProdState();
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    zstdFreeSeqProdState
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_zstdFreeSeqProdState(
    JNIEnv *env, jclass obj, jlong sequenceProducerState) {
  (void)env;
  (void)obj;

  QZSTD_freeSeqProdState((void *)sequenceProducerState);
}
