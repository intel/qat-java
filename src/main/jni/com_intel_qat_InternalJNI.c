/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include "com_intel_qat_InternalJNI.h"

#include <stdlib.h>

#include "qatzip.h"
#include "util.h"

#define DEFLATE_ALGORITHM 0
#define POLLING_MODE QZ_BUSY_POLLING

/**
 * The fieldID for java.nio.ByteBuffer/position
 */
static jfieldID nio_bytebuffer_position_id;

/**
 * Setups a QAT session for DEFLATE.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param level the compression level to use.
 */
static int setup_deflate_session(QzSession_T *qz_session, int level) {
  QzSessionParamsDeflate_T deflate_params;

  int status = qzGetDefaultsDeflate(&deflate_params);
  if (status != QZ_OK) return status;

  deflate_params.data_fmt = QZ_DEFLATE_GZIP_EXT;
  deflate_params.common_params.comp_lvl = level;
  deflate_params.common_params.polling_mode = POLLING_MODE;

  return qzSetupSessionDeflate(qz_session, &deflate_params);
}

/**
 * Setups a QAT session for LZ4.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param level the compression level to use.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int setup_lz4_session(QzSession_T *qz_session, int level) {
  QzSessionParamsLZ4_T lz4_params;

  int status = qzGetDefaultsLZ4(&lz4_params);
  if (status != QZ_OK) return status;

  lz4_params.common_params.polling_mode = POLLING_MODE;
  lz4_params.common_params.comp_lvl = level;

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
  int status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);

  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status) {
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
  int status = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);
  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status && status != QZ_BUF_ERROR &&
           status != QZ_DATA_ERROR) {
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
 * Setups a QAT session.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (Lcom/intel/qat/QatZipper;IJII)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(
    JNIEnv *env, jobject obj, jobject qat_zipper, jint sw_backup, jint codec,
    jint level) {
  (void)obj;

  // save the fieldID of nio.ByteBuffer.position
  nio_bytebuffer_position_id = (*env)->GetFieldID(
      env, (*env)->FindClass(env, "java/nio/ByteBuffer"), "position", "I");

  QzSession_T *qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

  int status = qzInit(qz_session, (unsigned char)sw_backup);
  if (status != QZ_OK && status != QZ_DUPLICATE) {
    throw_exception(env, status, "Initializing QAT HW failed.");
    return;
  }

  if (codec == DEFLATE_ALGORITHM)
    status = setup_deflate_session(qz_session, level);
  else
    status = setup_lz4_session(qz_session, level);

  if (status != QZ_OK) {
    qzClose(qz_session);
    throw_exception(env, status, "Error occurred while setting up a session.");
    return;
  }

  jclass qz_clazz = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  jfieldID qz_session_field = (*env)->GetFieldID(env, qz_clazz, "session", "J");
  (*env)->SetLongField(env, qat_zipper, qz_session_field, (jlong)qz_session);
}

/*
 *  Compresses a direct byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
jint JNICALL Java_com_intel_qat_InternalJNI_compressDirectByteBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jint src_pos,
    jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

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
 * Compresses a byte array or an array backed byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressArrayOrBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  QzSession_T *qz_session = (QzSession_T *)sess;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
           dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  if (src_buf) {  // is indirect ByteBuffer
    (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                        src_pos + bytes_read);
  }

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
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jint src_pos,
    jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

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
 * Decompresses a byte array or an array backed byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressArrayOrBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  QzSession_T *qz_session = (QzSession_T *)sess;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
             dst_len, &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  // if indirect ByteBuffer, set its position
  if (src_buf)
    (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                        src_pos + bytes_read);

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
    JNIEnv *env, jclass obj, jlong sess, jlong src_size) {
  (void)env;
  (void)obj;

  return qzMaxCompressedLength(src_size, (QzSession_T *)sess);
}

/*
 * Tearsdown the given QAT session.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                               jclass obj,
                                                               jlong sess) {
  (void)obj;

  QzSession_T *qz_session = (QzSession_T *)sess;
  if (!qz_session) return QZ_OK;

  int status = qzTeardownSession(qz_session);
  if (status != QZ_OK) {
    throw_exception(env, status, "Error occurred while tearing down session.");
    return 0;
  }

  free(qz_session);

  return QZ_OK;
}
