/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
#define _GNU_SOURCE

#include "com_intel_qat_InternalJNI.h"

#include <limits.h>
#include <numa.h>
#include <sched.h>
#include <stdlib.h>

#include "qatzip.h"
#include "util.h"

// doxygen for C documentation

#define QZ_HW_INIT_ERROR "An error occured while initializing QAT hardware"
#define QZ_SETUP_SESSION_ERROR "An error occured while setting up session"
#define QZ_MEMFREE_ERROR "An error occured while freeing up pinned memory"
#define QZ_BUFFER_ERROR "An error occured while reading the buffer"
#define QZ_COMPRESS_ERROR "An error occured while compression"
#define QZ_DECOMPRESS_ERROR "An error occured while decompression"
#define QZ_TEARDOWN_ERROR "An error occured while tearing down session"

#define DEFLATE 0

static __thread int cpu_id;
static __thread int numa_id;
static QzPollingMode_T polling_mode = QZ_BUSY_POLLING;
static QzDataFormat_T data_fmt = QZ_DEFLATE_GZIP_EXT;

typedef int (*kernel_func)(JNIEnv *env, QzSession_T *sess,
                           unsigned char *src_ptr, unsigned int src_len,
                           unsigned char *dst_ptr, unsigned int dst_len,
                           int *src_read, int *dst_written, int retry_count,
                           int is_last);
/*
 * Structure which contains
 * pointer to QAT hardware
 * Pinned mem for source
 * pinned mem size for source
 * Pinned mem for destination
 * pinned mem size for destination
 */
struct Session_T {
  QzSession_T *qz_session;
  unsigned char *pin_mem_src;
  unsigned char *pin_mem_dst;
  int pin_mem_src_size;
  int pin_mem_dst_size;
};

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup_deflate_session
 * params:    QAT session pointer, compression level for deflate
 * Sets up a deflate(ZLIB) session
 */
static int setup_deflate_session(QzSession_T *qz_session,
                                 int compression_level) {
  QzSessionParamsDeflate_T deflate_params;

  int status = qzGetDefaultsDeflate(&deflate_params);
  if (status != QZ_OK) return status;

  deflate_params.data_fmt = data_fmt;
  deflate_params.common_params.comp_lvl = compression_level;
  deflate_params.common_params.polling_mode = polling_mode;

  return qzSetupSessionDeflate(qz_session, &deflate_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup_lz4_session
 * params:    QAT session pointer, compression level for LZ4
 * Sets up a LZ4 session
 */
static int setup_lz4_session(QzSession_T *qz_session, int compression_level) {
  QzSessionParamsLZ4_T lz4_params;

  int status = qzGetDefaultsLZ4(&lz4_params);
  if (status != QZ_OK) return status;

  lz4_params.common_params.polling_mode = polling_mode;
  lz4_params.common_params.comp_lvl = compression_level;

  return qzSetupSessionLZ4(qz_session, &lz4_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    free_pin_mem
 * frees natively allocated pinned memory through freeing up associated pointers
 */
inline void free_pin_mem(void *src_buf, void *dst_buf) {
  if (src_buf != NULL) qzFree(src_buf);

  if (dst_buf != NULL) qzFree(dst_buf);

  src_buf = NULL;
  dst_buf = NULL;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    allocate_pin_mem
 * Allocate new ByteBuffer using qzMalloc for the source and destination pinned
 * buffers
 */
static int allocate_pin_mem(struct Session_T *qat_session, jint mode,
                            jlong src_size, jlong dest_size) {
  void *tmp_src_addr = qzMalloc(src_size, numa_id, 1);
  void *tmp_dst_addr = qzMalloc(dest_size, numa_id, 1);

  if (tmp_src_addr == NULL || tmp_dst_addr == NULL) {
    free_pin_mem(tmp_src_addr, tmp_dst_addr);
    if (mode == 0) {
      return 1;
    }
  }

  if (tmp_src_addr != NULL) {
    qat_session->pin_mem_src = (unsigned char *)tmp_src_addr;
    qat_session->pin_mem_src_size = src_size;
  }

  if (tmp_dst_addr != NULL) {
    qat_session->pin_mem_dst = (unsigned char *)tmp_dst_addr;
    qat_session->pin_mem_dst_size = dest_size;
  }

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compress source at a given offset upto a given length and stores
 * into destination at given offset of a particular size compresses data stored
 * at source
 */
static int compress(JNIEnv *env, QzSession_T *sess, unsigned char *src_ptr,
                    unsigned int src_len, unsigned char *dst_ptr,
                    unsigned int dst_len, int *src_read, int *dst_written,
                    int retry_count, int is_last) {
  int status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, is_last);

  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status) {
      status = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, is_last);
      retry_count--;
    }
  }

  if (status != QZ_OK) {
    throw_exception(env, status, QZ_COMPRESS_ERROR);
    return status;
  }

  *src_read = src_len;
  *dst_written = dst_len;

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompress source at a given offset upto a given length and stores
 * into destination at given offset of a particular size compresses data stored
 * at source
 */
static int decompress(JNIEnv *env, QzSession_T *sess, unsigned char *src_ptr,
                      unsigned int src_len, unsigned char *dst_ptr,
                      unsigned int dst_len, int *src_read, int *dst_written,
                      int retry_count, int is_last) {
  (void)is_last;

  int status = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);
  if (status == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    while (retry_count > 0 && QZ_OK != status && status != QZ_BUF_ERROR &&
           status != QZ_DATA_ERROR) {
      status = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);
      retry_count--;
    }
  }
  if (src_len == 0 ||
      (status != QZ_OK && status != QZ_BUF_ERROR && status != QZ_DATA_ERROR)) {
    throw_exception(env, status, QZ_DECOMPRESS_ERROR);
    return status;
  }

  *src_read = src_len;
  *dst_written = dst_len;

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compress/decompress source at a given offset upto a given length
 * and stores into destination at given offset of a particular size compresses
 * data stored at source
 */
static int compress_or_decompress(kernel_func kf, JNIEnv *env,
                                  struct Session_T *qat_session,
                                  unsigned char *src_ptr, jint src_pos,
                                  jint src_lim, unsigned char *dst_ptr,
                                  jint dst_pos, jint dst_lim, int *src_read,
                                  int *dst_written, int retry_count) {
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

    if (status != QZ_OK) break;

    memcpy(dst_start, pin_dst_ptr, bytes_written);

    src_start += bytes_read;
    dst_start += bytes_written;
  }

  *src_read = src_start - (src_ptr + src_pos);
  *dst_written = dst_start - (dst_ptr + dst_pos);

  return *dst_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (IJII)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(
    JNIEnv *env, jobject obj, jobject qat_session_obj, jint software_backup,
    jlong internal_buffer_size, jint comp_alg, jint comp_level) {
  (void)obj;

  struct Session_T *qat_session =
      (struct Session_T *)calloc(1, sizeof(struct Session_T));
  qat_session->qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

  const unsigned char sw_backup = (unsigned char)software_backup;

  int status = qzInit(qat_session->qz_session, sw_backup);

  if (status != QZ_OK && status != QZ_DUPLICATE) {
    throw_exception(env, status, QZ_HW_INIT_ERROR);
    return;
  }

  if (comp_alg == DEFLATE) {
    status = setup_deflate_session(qat_session->qz_session, comp_level);
  } else {
    status = setup_lz4_session(qat_session->qz_session, comp_level);
  }

  if (comp_alg == DEFLATE && QZ_OK != status) {
    throw_exception(env, status, "Error while trying to setup a session.");
    return;
  }
  if (QZ_OK != status && QZ_DUPLICATE != status) {
    qzClose(qat_session->qz_session);
    throw_exception(env, status, QZ_SETUP_SESSION_ERROR);
    return;
  }
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  if (internal_buffer_size != 0) {
    if (QZ_OK !=
        allocate_pin_mem(qat_session, software_backup, internal_buffer_size,
                         qzMaxCompressedLength(internal_buffer_size,
                                               qat_session->qz_session)))
      throw_exception(env, INT_MIN, QZ_HW_INIT_ERROR);
  } else {
    qat_session->pin_mem_src = NULL;
    qat_session->pin_mem_dst = NULL;
  }

  jclass qat_session_class = (*env)->GetObjectClass(env, qat_session_obj);
  jfieldID qatSessionField =
      (*env)->GetFieldID(env, qat_session_class, "session", "J");
  (*env)->SetLongField(env, qat_session_obj, qatSessionField,
                       (jlong)qat_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
jint JNICALL Java_com_intel_qat_InternalJNI_compressDirectByteBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jint src_pos,
    jint src_lim, jobject dst_buf, jint dst_pos, jint dst_lim,
    jint retry_count) {
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
  jclass src_clazz = (*env)->GetObjectClass(env, src_buf);
  jclass dst_clazz = (*env)->GetObjectClass(env, dst_buf);

  (*env)->SetIntField(env, src_buf,
                      (*env)->GetFieldID(env, src_clazz, "position", "I"),
                      src_pos + src_read);
  (*env)->SetIntField(env, dst_buf,
                      (*env)->GetFieldID(env, dst_clazz, "position", "I"),
                      dst_pos + dst_written);

  return dst_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressArrayOrBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_lim, jbyteArray dst_arr, jint dst_pos, jint dst_lim,
    jint retry_count) {
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

  if (src_buf != NULL) {  // is indirect ByteBuffer
    jclass src_clazz = (*env)->GetObjectClass(env, src_buf);
    (*env)->SetIntField(env, src_buf,
                        (*env)->GetFieldID(env, src_clazz, "position", "I"),
                        src_pos + src_read);
  }

  return dst_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jint src_pos,
    jint src_lim, jobject dst_buf, jint dst_pos, jint dst_lim,
    jint retry_count) {
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
  jclass src_clazz = (*env)->GetObjectClass(env, src_buf);
  jclass dst_clazz = (*env)->GetObjectClass(env, dst_buf);

  (*env)->SetIntField(env, src_buf,
                      (*env)->GetFieldID(env, src_clazz, "position", "I"),
                      src_pos + src_read);
  (*env)->SetIntField(env, dst_buf,
                      (*env)->GetFieldID(env, dst_clazz, "position", "I"),
                      dst_pos + dst_written);

  return dst_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressArrayOrBuffer(
    JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jbyteArray src_arr,
    jint src_pos, jint src_lim, jbyteArray dst_arr, jint dst_pos, jint dst_lim,
    jint retry_count) {
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

  if (src_buf != NULL) {  // is indirect ByteBuffer
    jclass src_clazz = (*env)->GetObjectClass(env, src_buf);
    (*env)->SetIntField(env, src_buf,
                        (*env)->GetFieldID(env, src_clazz, "position", "I"),
                        src_pos + src_read);
  }

  return dst_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (JJ)I
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(
    JNIEnv *env, jclass obj, jlong session, jlong src_size) {
  (void)env;
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)session;
  return qzMaxCompressedLength(src_size, qat_session->qz_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * Signature: (J)I
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                               jclass obj,
                                                               jlong sess) {
  (void)obj;

  struct Session_T *qat_session = (struct Session_T *)sess;

  free_pin_mem(qat_session->pin_mem_src, qat_session->pin_mem_dst);

  if (qat_session->qz_session == NULL) return QZ_OK;

  int status = qzTeardownSession(qat_session->qz_session);
  if (status != QZ_OK) {
    throw_exception(env, status, QZ_TEARDOWN_ERROR);
    return 0;
  }

  free(qat_session->qz_session);
  free(qat_session);

  return QZ_OK;
}
