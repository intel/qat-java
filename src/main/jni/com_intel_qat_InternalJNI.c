/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
#define _GNU_SOURCE

#include "com_intel_qat_InternalJNI.h"

#include <numa.h>
#include <sched.h>
#include <stdlib.h>

#include "qatzip.h"
#include "util.h"

#define DEFLATE_ALGORITHM 0
#define POLLING_MODE QZ_BUSY_POLLING

static __thread int numa_id;

typedef int (*kernel_func)(JNIEnv *env, QzSession_T *sess,
                           unsigned char *src_ptr, unsigned int src_len,
                           unsigned char *dst_ptr, unsigned int dst_len,
                           int *src_read, int *dst_written, int retry_count,
                           int is_last);

/**
 * A type for storing QAT session and pinned memory buffers.
 */
struct Session_T {
  QzSession_T *qz_session;    /** A pointer to the QAT session. */
  unsigned char *pin_mem_src; /** A pointer to the source buffer. */
  unsigned char *pin_mem_dst; /** A pointer to the destination buffer. */
  int pin_mem_src_size;       /** The size of the source buffer. */
  int pin_mem_dst_size;       /** The size of the destination buffer. */
};

/**
 * The fieldID for java.nio.ByteBuffer/position
 */
static jfieldID nio_bytebuffer_position_id;

/**
 * Setups a QAT session for DEFLATE.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param compression_level the compression level to use.
 */
static int setup_deflate_session(QzSession_T *qz_session, int compression_level)
{
  QzSessionParamsDeflate_T deflate_params;

  int status = qzGetDefaultsDeflate(&deflate_params);
  if (status != QZ_OK) return status;

  deflate_params.data_fmt = QZ_DEFLATE_GZIP_EXT;
  deflate_params.common_params.comp_lvl = compression_level;
  deflate_params.common_params.polling_mode = POLLING_MODE;

  return qzSetupSessionDeflate(qz_session, &deflate_params);
}

/**
 * Setups a QAT session for LZ4.
 *
 * @param qz_session a pointer to the QzSession_T.
 * @param compression_level the compression level to use.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int setup_lz4_session(QzSession_T *qz_session, int compression_level)
{
  QzSessionParamsLZ4_T lz4_params;

  int status = qzGetDefaultsLZ4(&lz4_params);
  if (status != QZ_OK) return status;

  lz4_params.common_params.polling_mode = POLLING_MODE;
  lz4_params.common_params.comp_lvl = compression_level;

  return qzSetupSessionLZ4(qz_session, &lz4_params);
}

/**
 * Frees up source and destination pinned memory.
 *
 * @param src_buf a pointer to the source buffer.
 * @param dst_buf a pointer to the destination buffer.
 */
inline void free_pin_mem(void *src_buf, void *dst_buf)
{
  if (src_buf) qzFree(src_buf);
  if (dst_buf) qzFree(dst_buf);

  src_buf = NULL;
  dst_buf = NULL;
}

/**
 * Allocates pinned memory for source and destination buffers.
 *
 * @param env the pointer to the JNI environment.
 * @param qat_session a pointer to a Session_T object.
 * @param mode the operation mode (HARDWARE or AUTO).
 * @param src_size the size for the source buffer.
 * @param dst_size the size for the destination buffer.
 *
 * @return QZ_OK (0) on success, non-zero otherwise.
 */
static int allocate_pin_mem(JNIEnv *env, struct Session_T *qat_session,
                            jint mode, jlong src_size, jlong dst_size)
{
  void *tmp_src_addr = qzMalloc(src_size, numa_id, 1);
  void *tmp_dst_addr = qzMalloc(dst_size, numa_id, 1);

  if (!tmp_src_addr || !tmp_dst_addr) {
    free_pin_mem(tmp_src_addr, tmp_dst_addr);
    if (mode == 0) {
      // we are in hardware-only mode, so we throw an exception.
      throw_exception(env, QZ_FAIL, "Failed to allocate pinned memory.");
      return !QZ_OK;
    }
  }

  if (tmp_src_addr) {
    qat_session->pin_mem_src = (unsigned char *)tmp_src_addr;
    qat_session->pin_mem_src_size = src_size;
  }

  if (tmp_dst_addr) {
    qat_session->pin_mem_dst = (unsigned char *)tmp_dst_addr;
    qat_session->pin_mem_dst_size = dst_size;
  }

  return QZ_OK;
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
 * @param src_read an out parameter that stores the bytes read from the source
 * buffer.
 * @param dst_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of compression retries before we give up.
 * @param is_last a flag that indicates if the current buffer is the last one.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int compress(JNIEnv *env, QzSession_T *sess, unsigned char *src_ptr,
                    unsigned int src_len, unsigned char *dst_ptr,
                    unsigned int dst_len, int *src_read, int *dst_written,
                    int retry_count, int is_last)
{
  int status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, is_last);

  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status) {
      status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, is_last);
      retry_count--;
    }
  }

  if (status != QZ_OK) {
    throw_exception(env, status, "Error occurred while compressiong data.");
    return status;
  }

  *src_read = src_len;
  *dst_written = dst_len;

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
 * @param src_read an out parameter that stores the bytes read from the source
 * buffer.
 * @param dst_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of decompression retries before we give up.
 * @param is_last is ignored.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int decompress(JNIEnv *env, QzSession_T *sess, unsigned char *src_ptr,
                      unsigned int src_len, unsigned char *dst_ptr,
                      unsigned int dst_len, int *src_read, int *dst_written,
                      int retry_count, int is_last)
{
  (void)is_last;

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

  *src_read = src_len;
  *dst_written = dst_len;

  return QZ_OK;
}

/**
 * Compresses/decompresses a buffer pointed to by the given source pointer and
 * writes it to the destination buffer pointed to by the destination pointer.
 * The read and write of the source and destination buffers is bounded by the
 * source and destination lengths respectively.
 *
 * @param kf a pointer to either the compress or decompress functions.
 * @param env a pointer to the JNI environment.
 * @param sess a pointer to the QzSession_T object.
 * @param src_ptr the source buffer.
 * @param src_len the size of the source buffer.
 * @param dst_ptr the destination buffer.
 * @param dst_len the size of the destination buffer.
 * @param src_read an out parameter that stores the bytes read from the source
 * buffer.
 * @param dst_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of decompression retries before we give up.
 * @return the number of actual bytes compressed or decompressed.
 */
static int compress_or_decompress(kernel_func kf, JNIEnv *env,
                                  struct Session_T *qat_session,
                                  unsigned char *src_ptr, jint src_pos,
                                  jint src_lim, unsigned char *dst_ptr,
                                  jint dst_pos, jint dst_lim, int *src_read,
                                  int *dst_written, int retry_count)
{
  unsigned char *src_start = src_ptr + src_pos;
  unsigned char *src_end = src_ptr + src_lim;

  unsigned char *dst_start = dst_ptr + dst_pos;
  unsigned char *dst_end = dst_ptr + dst_lim;

  unsigned char *pin_src_ptr = qat_session->pin_mem_src;
  unsigned char *pin_dst_ptr = qat_session->pin_mem_dst;

  int bytes_read = 0;
  int bytes_written = 0;

  int status, src_len, dst_len, src_size, dst_size, is_last;
  while (src_start < src_end && dst_start < dst_end) {
    src_len = src_end - src_start;
    src_size = src_len < qat_session->pin_mem_src_size
                   ? src_len
                   : qat_session->pin_mem_src_size;
    dst_len = dst_end - dst_start;
    dst_size = dst_len < qat_session->pin_mem_dst_size
                   ? dst_len
                   : qat_session->pin_mem_dst_size;

    is_last = src_size != qat_session->pin_mem_src_size || src_size == src_len;

    memcpy(pin_src_ptr, src_start, src_size);
    status =
        kf(env, qat_session->qz_session, pin_src_ptr, src_size, pin_dst_ptr,
           dst_size, &bytes_read, &bytes_written, retry_count, is_last);

    if (status != QZ_OK || bytes_read == 0) break;

    memcpy(dst_start, pin_dst_ptr, bytes_written);

    src_start += bytes_read;
    dst_start += bytes_written;
  }

  *src_read = src_start - (src_ptr + src_pos);
  *dst_written = dst_start - (dst_ptr + dst_pos);

  return *dst_written;
}

/*
 * Setups a QAT session.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (Lcom/intel/qat/QatZipper;IJII)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(
    JNIEnv *env, jobject obj, jobject qat_zipper, jint sw_backup,
    jlong pin_mem_size, jint comp_alg, jint comp_level)
{
  (void)obj;

  // save the fieldID of nio.ByteBuffer.position
  nio_bytebuffer_position_id = (*env)->GetFieldID(
      env, (*env)->FindClass(env, "java/nio/ByteBuffer"), "position", "I");

  struct Session_T *qat_session =
      (struct Session_T *)calloc(1, sizeof(struct Session_T));
  qat_session->qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

  int status = qzInit(qat_session->qz_session, (unsigned char)sw_backup);
  if (status != QZ_OK && status != QZ_DUPLICATE) {
    throw_exception(env, status, "Initializing QAT HW failed.");
    return;
  }

  if (comp_alg == DEFLATE_ALGORITHM)
    status = setup_deflate_session(qat_session->qz_session, comp_level);
  else
    status = setup_lz4_session(qat_session->qz_session, comp_level);

  if (comp_alg == DEFLATE_ALGORITHM && QZ_OK != status) {
    throw_exception(env, status, "Error occurred while setting up a session.");
    return;
  }
  if (status != QZ_OK && status != QZ_DUPLICATE) {
    qzClose(qat_session->qz_session);
    throw_exception(env, status, "Error occurred while setting up a session.");
    return;
  }
  numa_id = numa_node_of_cpu(sched_getcpu());

  if (pin_mem_size != 0) {
    pin_mem_size = next_power_of_2(pin_mem_size);
    allocate_pin_mem(
        env, qat_session, sw_backup, pin_mem_size,
        qzMaxCompressedLength(pin_mem_size, qat_session->qz_session));
  } else {
    qat_session->pin_mem_src = NULL;
    qat_session->pin_mem_dst = NULL;
  }

  jclass qz_clazz = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  jfieldID qz_session_field = (*env)->GetFieldID(env, qz_clazz, "session", "J");
  (*env)->SetLongField(env, qat_zipper, qz_session_field, (jlong)qat_session);
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
    jint src_lim, jobject dst_buf, jint dst_pos, jint dst_lim, jint retry_count)
{
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)sess;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int src_read = 0;
  int dst_written = 0;

  if (qat_session->pin_mem_src && qat_session->pin_mem_dst)
    compress_or_decompress(&compress, env, qat_session, src_ptr, src_pos,
                           src_lim, dst_ptr, dst_pos, dst_lim, &src_read,
                           &dst_written, retry_count);
  else
    compress(env, qat_session->qz_session, src_ptr + src_pos, src_lim - src_pos,
             dst_ptr + dst_pos, dst_lim - dst_pos, &src_read, &dst_written,
             retry_count, 1);

  // set src and dest buffer positions
  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + src_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + dst_written);

  return dst_written;
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
    jint src_pos, jint src_lim, jbyteArray dst_arr, jint dst_pos, jint dst_lim,
    jint retry_count)
{
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)sess;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int src_read = 0;
  int dst_written = 0;

  if (qat_session->pin_mem_src && qat_session->pin_mem_dst)
    compress_or_decompress(&compress, env, qat_session, src_ptr, src_pos,
                           src_lim, dst_ptr, dst_pos, dst_lim, &src_read,
                           &dst_written, retry_count);
  else
    compress(env, qat_session->qz_session, src_ptr + src_pos, src_lim - src_pos,
             dst_ptr + dst_pos, dst_lim - dst_pos, &src_read, &dst_written,
             retry_count, 1);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  if (src_buf) {  // is indirect ByteBuffer
    (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                        src_pos + src_read);
  }

  return dst_written;
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
    jint src_lim, jobject dst_buf, jint dst_pos, jint dst_lim, jint retry_count)
{
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)sess;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int src_read = 0;
  int dst_written = 0;

  if (qat_session->pin_mem_src && qat_session->pin_mem_dst)
    compress_or_decompress(&decompress, env, qat_session, src_ptr, src_pos,
                           src_lim, dst_ptr, dst_pos, dst_lim, &src_read,
                           &dst_written, retry_count);
  else
    decompress(env, qat_session->qz_session, src_ptr + src_pos,
               src_lim - src_pos, dst_ptr + dst_pos, dst_lim - dst_pos,
               &src_read, &dst_written, retry_count, 0);

  // set src and dest buffer positions
  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + src_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + dst_written);

  return dst_written;
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
    jint src_pos, jint src_lim, jbyteArray dst_arr, jint dst_pos, jint dst_lim,
    jint retry_count)
{
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)sess;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int src_read = 0;
  int dst_written = 0;

  if (qat_session->pin_mem_src && qat_session->pin_mem_dst)
    compress_or_decompress(&decompress, env, qat_session, src_ptr, src_pos,
                           src_lim, dst_ptr, dst_pos, dst_lim, &src_read,
                           &dst_written, retry_count);
  else
    decompress(env, qat_session->qz_session, src_ptr + src_pos,
               src_lim - src_pos, dst_ptr + dst_pos, dst_lim - dst_pos,
               &src_read, &dst_written, retry_count, 0);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  // if indirect ByteBuffer, set its position
  if (src_buf)
    (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                        src_pos + src_read);

  return dst_written;
}

/*
 * Evaluates the maximum compressed size for the given buffer size.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(
    JNIEnv *env, jclass obj, jlong session, jlong src_size)
{
  (void)env;
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)session;
  return qzMaxCompressedLength(src_size, qat_session->qz_session);
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
                                                               jlong sess)
{
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)sess;
  free_pin_mem(qat_session->pin_mem_src, qat_session->pin_mem_dst);

  if (!qat_session->qz_session) return QZ_OK;

  int status = qzTeardownSession(qat_session->qz_session);
  if (status != QZ_OK) {
    throw_exception(env, status, "Error occurred while tearing down session.");
    return 0;
  }

  free(qat_session->qz_session);
  free(qat_session);

  return QZ_OK;
}
