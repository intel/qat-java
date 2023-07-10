/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#define _GNU_SOURCE
#include "com_intel_qat_InternalJNI.h"
#include "qatzip.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sched.h>
#include <numa.h>
#include <limits.h>
#include <stdbool.h>
#include "util.h"


#define QZ_HW_INIT_ERROR "An error occured while initializing QAT hardware"
#define QZ_SETUP_SESSION_ERROR "An error occured while setting up session"
#define QZ_MEMFREE_ERROR "An error occured while freeing up pinned memory"
#define QZ_BUFFER_ERROR "An error occured while reading the buffer"
#define QZ_COMPRESS_ERROR "An error occured while compression"
#define QZ_DECOMPRESS_ERROR "An error occured while decompression"
#define QZ_TEARDOWN_ERROR "An error occured while tearing down session"

static __thread int cpu_id;
static __thread int numa_id;
static QzPollingMode_T polling_mode = QZ_BUSY_POLLING;
static QzDataFormat_T data_fmt = QZ_DEFLATE_GZIP_EXT;

static const int DEFLATE = 0;

typedef int (*kernel_func)(JNIEnv *env, QzSession_T *sess, char *src_ptr, int src_len,
                           char *dst_ptr, int dst_len, int *src_read,
                           int *dst_written, int retry_count);
/*
 * Structure which contains
 * pointer to QAT hardware
 * Pinned mem for source
 * pinned mem size for source
 * Pinned mem for destination
 * pinned mem size for destination
 */
typedef struct Session_S{
    QzSession_T* qz_session;
    unsigned char* pin_mem_src;
    unsigned char* pin_mem_dst;
    int pin_mem_src_size;
    int pin_mem_dst_size;
}Session_T;

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupDeflateSession
 * Sets up a deflate(ZLIB) session
 */

int setupDeflateSession(QzSession_T* qz_session, int compressionLevel){

    QzSessionParamsDeflate_T deflate_params;

    int rc = qzGetDefaultsDeflate(&deflate_params);
    if (rc != QZ_OK)
        return rc;

    deflate_params.data_fmt = data_fmt;
    deflate_params.common_params.comp_lvl = compressionLevel;
    deflate_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionDeflate(qz_session, &deflate_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupLZ4Session
 * Sets up a LZ4 session
 */

int setupLZ4Session(QzSession_T* qz_session){
    QzSessionParamsLZ4_T lz4_params;

    int rc = qzGetDefaultsLZ4(&lz4_params);
    if (rc != QZ_OK)
      return rc;

    lz4_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionLZ4(qz_session, &lz4_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freePinnedMem
 * frees natively allocated ByteBuffer
 */

void freePinnedMem(void* unSrcBuff, void *unDestBuff) {

  if(unSrcBuff != NULL)
    qzFree(unSrcBuff);

  if(unDestBuff != NULL)
    qzFree(unDestBuff);

}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    allocatePinnedMem
 * Allocate new ByteBuffer using qzMalloc for the source and destination pinned buffers
 */

int allocatePinnedMem (Session_T* qat_session,jint mode, jlong srcSize, jlong destSize) {

  void* tempSrcAddr = qzMalloc(srcSize, numa_id, true);
  void* tempDestAddr = qzMalloc(destSize, numa_id, true);

  if(tempSrcAddr == NULL || tempDestAddr == NULL)
  {
     freePinnedMem(tempSrcAddr,tempDestAddr);
     if(mode == 0){
        return 1;
     }
     tempSrcAddr = NULL;
     tempDestAddr = NULL;
  }

  if(tempSrcAddr != NULL){
    qat_session->pin_mem_src = (unsigned char*)tempSrcAddr;
    qat_session->pin_mem_src_size = srcSize;
   }

  if(tempDestAddr != NULL){
    qat_session->pin_mem_dst = (unsigned char*)tempDestAddr;
    qat_session->pin_mem_dst_size = destSize;
  }

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compress source at a given offset upto a given length and stores into destination at given offset of a particular size
 * compresses data stored at source
 */

static int compress(JNIEnv *env,QzSession_T *sess, char *src_ptr, int src_len,
                    char *dst_ptr, int dst_len, int *src_read,
                    int *dst_written, int retry_count) {
  int rc = QZ_OK;
  int src_offset = 0;
  int dst_offset = 0;
  int in_len = src_len;
  int out_len = dst_len;
  while (in_len > 0 && dst_len > 0) {
    rc = qzCompress(sess, src_ptr + src_offset, &in_len, dst_ptr + dst_offset,&out_len, 0);

    if(rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0){
        while(retry_count > 0 && QZ_OK != rc){
            rc = qzCompress(sess, src_ptr + src_offset, &in_len, dst_ptr + dst_offset, &out_len, 0);
            retry_count--;
        }
    }

    if (rc != QZ_OK) {
      throw_exception(env,QZ_COMPRESS_ERROR,rc);
      return rc;
    }
    src_offset += in_len;
    dst_offset += out_len;
    in_len = src_len - in_len;
    out_len = dst_len - out_len;
  }

  *src_read = src_offset;
  *dst_written = dst_offset;

  return rc;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compress/decompress source at a given offset upto a given length and stores into destination at given offset of a particular size
 * compresses data stored at source
 */

static int compress_or_decompress(kernel_func kf, JNIEnv *env, jobject obj,
                                  Session_T* qat_session, char *src_ptr,
                                  jint src_pos, jint src_lim, char *dst_ptr,
                                  jint dst_pos, jint dst_lim, int *src_read,
                                  int *dst_written, int retry_count) {

  char *src_start = src_ptr + src_pos;
  char *src_end = src_start + src_lim;

  char *dst_start = dst_ptr + dst_pos;
  char *dst_end = dst_start + dst_lim;

  char *pin_src_ptr = (char *)qat_session->pin_mem_src;
  char *pin_dst_ptr = (char *)qat_session->pin_mem_dst;

  int bytes_read = 0;
  int bytes_written = 0;
  while (src_start < src_end && dst_start < dst_end) {
    int src_len = src_end - src_start;
    int src_size = src_len < qat_session->pin_mem_src_size ? src_len : qat_session->pin_mem_src_size;
    int dst_len = dst_end - dst_start;
    int dst_size = dst_len < qat_session->pin_mem_dst_size ? dst_len : qat_session->pin_mem_dst_size;

    memcpy(pin_src_ptr, src_start, src_size);
    kf(env,qat_session->qz_session, pin_src_ptr, src_size, pin_dst_ptr, dst_size, &bytes_read,
       &bytes_written,retry_count);
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
 * Method:    decompress source at a given offset upto a given length and stores into destination at given offset of a particular size
 * compresses data stored at source
 */
static int decompress(JNIEnv *env, QzSession_T *sess, char *src_ptr, int src_len,
                      char *dst_ptr, int dst_len, int *src_read,
                      int *dst_written, jint retry_count) {
  int rc = QZ_OK;

  int src_offset = 0;
  int dst_offset = 0;
  int in_len = src_len;
  int out_len = dst_len;
  while (in_len > 0 && dst_len > 0) {
    rc = qzDecompress(sess, src_ptr + src_offset, &in_len, dst_ptr + dst_offset,
                      &out_len);

    if(rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0){
        while(retry_count > 0 && QZ_OK != rc){
            rc = qzDecompress(sess, src_ptr + src_offset, &in_len, dst_ptr + dst_offset, &out_len);
            retry_count--;
        }
    }
    if (rc != QZ_OK && rc != QZ_BUF_ERROR && rc != QZ_DATA_ERROR) {
      throw_exception(env, QZ_DECOMPRESS_ERROR,rc);
      return rc;
    }
    src_offset += in_len;
    dst_offset += out_len;
    in_len = src_len - in_len;
    out_len = dst_len - out_len;
  }

  *src_read = src_offset;
  *dst_written = dst_offset;

  return rc;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (IJII)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env, jclass jc, jobject qatSessionObj
,jint softwareBackup, jlong internalBufferSizeInBytes, jint compressionAlgo, jint compressionLevel) {

  int rc;
  Session_T* qat_session = (Session_T*)malloc(sizeof(Session_T));
  memset(qat_session,0,sizeof(Session_T));
  qat_session->qz_session = (QzSession_T*)malloc(sizeof(QzSession_T));
  memset(qat_session->qz_session,0,sizeof(QzSession_T));

  const unsigned char sw_backup = (unsigned char) softwareBackup;

  rc = qzInit(qat_session->qz_session, sw_backup);

  if (rc != QZ_OK && rc != QZ_DUPLICATE){
    throw_exception(env, QZ_HW_INIT_ERROR, rc);
    return;
  }

  if(compressionAlgo == DEFLATE)
  {
    rc = setupDeflateSession(qat_session->qz_session, compressionLevel);
  }
  else
  {
    rc = setupLZ4Session(qat_session->qz_session);
  }

  if(compressionAlgo == DEFLATE && QZ_OK != rc){
    throw_exception(env, "LZ4 session not setup", rc);
    return;
  }
  if(QZ_OK != rc && QZ_DUPLICATE != rc){
    qzClose(qat_session->qz_session);
    throw_exception(env, QZ_SETUP_SESSION_ERROR, rc);
    return;
  }
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  if(QZ_OK != allocatePinnedMem(qat_session,softwareBackup, internalBufferSizeInBytes, qzMaxCompressedLength(internalBufferSizeInBytes,qat_session->qz_session)))
    throw_exception(env, QZ_HW_INIT_ERROR,INT_MIN);

  jclass qatSessionClass = (*env)->GetObjectClass(env, qatSessionObj);
  jfieldID qatSessionField = (*env)->GetFieldID(env,qatSessionClass,"session","J");
  (*env)->SetLongField(env, qatSessionObj,qatSessionField, (jlong)qat_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
 jint JNICALL Java_com_intel_qat_InternalJNI_compressDirectByteBuffer(
     JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jint src_pos,
     jint src_lim, jobject dst_buf, jint dst_pos, jint dst_lim,jint retry_count) {

   Session_T *qat_session = (Session_T *)sess;
   char *src_ptr = (char *)(*env)->GetDirectBufferAddress(env, src_buf);
   char *dst_ptr = (char *)(*env)->GetDirectBufferAddress(env, dst_buf);

   int src_read = 0;
   int dst_written = 0;

   if (!qat_session->pin_mem_src || !qat_session->pin_mem_dst)
     compress(env,qat_session->qz_session, src_ptr + src_pos, src_lim, dst_ptr + dst_pos, dst_lim,
              &src_read, &dst_written,retry_count);
   else
     compress_or_decompress(&compress, env, obj, qat_session, src_ptr, src_pos,
                           src_lim, dst_ptr, dst_pos, dst_lim,
                            &src_read, &dst_written,retry_count);

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
      jint src_pos, jint src_lim, jbyteArray dst_arr, jint dst_pos, jint dst_lim, jint retry_count) {

    Session_T *qat_session = (Session_T *)sess;

    char *src_ptr = (*env)->GetByteArrayElements(env, src_arr, NULL);
    char *dst_ptr = (*env)->GetByteArrayElements(env, dst_arr, NULL);

    int src_read = 0;
    int dst_written = 0;

    if (!qat_session->pin_mem_src || !qat_session->pin_mem_dst)
      compress(env,qat_session->qz_session, src_ptr + src_pos, src_lim, dst_ptr + dst_pos, dst_lim,
               &src_read, &dst_written, retry_count);
    else
      compress_or_decompress(&compress, env, obj, qat_session, src_ptr, src_pos,
                             src_lim, dst_ptr, dst_pos, dst_lim,
                             &src_read, &dst_written, retry_count);

    (*env)->ReleaseByteArrayElements(env, src_arr, src_ptr, 0);
    (*env)->ReleaseByteArrayElements(env, dst_arr, dst_ptr, 0);

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
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressDirectByteBuffer(
      JNIEnv *env, jobject obj, jlong sess, jobject src_buf, jint src_pos,
      jint src_lim, jobject dst_buf, jint dst_pos, jint dst_lim, jint retry_count) {

    Session_T *qat_session = (Session_T *)sess;
    char *src_ptr = (char *)(*env)->GetDirectBufferAddress(env, src_buf);
    char *dst_ptr = (char *)(*env)->GetDirectBufferAddress(env, dst_buf);

    int src_read = 0;
    int dst_written = 0;

    if (!qat_session->pin_mem_src || !qat_session->pin_mem_dst)
      decompress(env,qat_session->qz_session, src_ptr + src_pos, src_lim, dst_ptr + dst_pos, dst_lim,
                 &src_read, &dst_written, retry_count);
    else
      compress_or_decompress(&decompress, env, obj, qat_session, src_ptr, src_pos,
                             src_lim, dst_ptr, dst_pos, dst_lim,
                             &src_read, &dst_written,retry_count);

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
       jint src_pos, jint src_lim, jbyteArray dst_arr, jint dst_pos, jint dst_lim, jint retry_count) {
     Session_T *qat_session = (Session_T *)sess;
     char *src_ptr = (*env)->GetByteArrayElements(env, src_arr, NULL);
     char *dst_ptr = (*env)->GetByteArrayElements(env, dst_arr, NULL);

     int src_read = 0;
     int dst_written = 0;

     if (!qat_session->pin_mem_src || !qat_session->pin_mem_dst)
       decompress(env,qat_session->qz_session, src_ptr + src_pos, src_lim, dst_ptr + dst_pos, dst_lim,
                  &src_read, &dst_written, retry_count);
     else
       compress_or_decompress(&decompress, env, obj, qat_session, src_ptr, src_pos,
                              src_lim, dst_ptr, dst_pos, dst_lim,
                              &src_read, &dst_written, retry_count);

     (*env)->ReleaseByteArrayElements(env, src_arr, src_ptr, 0);
     (*env)->ReleaseByteArrayElements(env, dst_arr, dst_ptr, 0);

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

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(JNIEnv *env,
                                                               jclass jobj,
                                                               jlong qzSession,
                                                               jlong srcSize
                                                               ) {
  QzSession_T* qz_session = (QzSession_T*) qzSession;
  return qzMaxCompressedLength(srcSize, qz_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env, jclass jobj, jlong sess) {

  Session_T* qat_session = (Session_T*) sess;
  void *unSrcBuff = NULL;
  void *unDestBuff = NULL;

  freePinnedMem(unSrcBuff, unDestBuff);

  if(qat_session->qz_session == NULL)
    return QZ_OK;

  int rc = qzTeardownSession(qat_session->qz_session);
  if (rc != QZ_OK){
    throw_exception(env,QZ_TEARDOWN_ERROR,rc);
    return 0;
   }

  free(qat_session->qz_session);
  free(qat_session);

  return QZ_OK;
}