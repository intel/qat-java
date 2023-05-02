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

// all this goes to QATSession
static __thread QzSession_T qz_session;
//static __thread QzSessionParams_T qz_params;
static __thread QzSessionParamsDeflate_T deflate_params;
static __thread QzSessionParamsLZ4_T lz4_params;

static __thread int cpu_id;
static __thread int numa_id;
static QzPollingMode_T polling_mode = QZ_BUSY_POLLING;
static QzDataFormat_T data_fmt = QZ_DEFLATE_GZIP_EXT;

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupHardware
 * initializes QAT hardware and sets up a session
 */


int setupDeflateSession(QzSession_T* sess, int compressionLevel){

    //QzSessionParamsDeflate_T deflate_params;

    int rc = qzGetDefaultsDeflate(&deflate_params);
    if (rc != QZ_OK)
        return rc;

    deflate_params.data_fmt = data_fmt;
    deflate_params.common_params.comp_lvl = compressionLevel;
    deflate_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionDeflate(&qz_session, &deflate_params);
}

int setupLZ4Session(QzSession_T* sess, int compressionLevel){
    QzSessionParamsLZ4_T lz4_params;

    int rc = qzGetDefaultsLZ4(&lz4_params);
    if (rc != QZ_OK)
      return rc;

    lz4_params.common_params.comp_lvl = compressionLevel;
    lz4_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionLZ4(sess, &lz4_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freePinnedMem
 * frees native allocated ByteBuffer
 */

void freePinnedMem
(void* unSrcBuff, void *unDestBuff) {

  if(NULL != unSrcBuff)
    qzFree(unSrcBuff);

  if(NULL != unDestBuff)
    qzFree(unDestBuff);

}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Allocate new ByteBuffer using qzMalloc
 */
int allocatePinnedMem
(JNIEnv *env, jclass jc, jclass qatSessionClass, jobject qatSessionObj, jlong srcSize, jlong destSize) {
  int rc;

  void* tempSrcAddr = qzMalloc(srcSize, numa_id, true);
  void* tempDestAddr = qzMalloc(destSize, numa_id, true);

  if(NULL == tempSrcAddr || NULL == tempDestAddr)
  {
     freePinnedMem(tempSrcAddr,tempDestAddr);
     return 1;
  }
  else{
    jfieldID unCompressedBufferField = (*env)->GetFieldID(env,qatSessionClass,"unCompressedBuffer","Ljava/nio/ByteBuffer;");
    jfieldID compressedBufferField = (*env)->GetFieldID(env,qatSessionClass,"compressedBuffer","Ljava/nio/ByteBuffer;");

    (*env)->SetObjectField(env, qatSessionObj,unCompressedBufferField, (*env)->NewDirectByteBuffer(env,tempSrcAddr,srcSize));
    (*env)->SetObjectField(env, qatSessionObj,compressedBufferField, (*env)->NewDirectByteBuffer(env,tempDestAddr,destSize));
  }
  return QZ_OK;
}


JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env, jclass jc, jobject qatSessionObj
,jint softwareBackup, jlong internalBufferSizeInBytes, jint compressionAlgo, jint compressionLevel) {
  int rc;
  //QzSession_T* qz_session = (QzSession_T*)malloc(sizeof(QzSession_T));
  const unsigned char sw_backup = (unsigned char) softwareBackup;

  jclass qatSessionClass = (*env)->GetObjectClass(env, qatSessionObj);

  rc = qzInit(&qz_session, sw_backup);

  //int qzSessionSize = sizeof(qz_session);
  if (rc != QZ_OK && rc != QZ_DUPLICATE){
    throw_exception(env, QZ_HW_INIT_ERROR, rc);
    return;
  }

  if(0 == compressionAlgo)
  {
    rc = setupDeflateSession(&qz_session, compressionLevel);
    //QzSessionParamsDeflate_T* deflate_params = (QzSessionParamsDeflate_T*) malloc(sizeof(QzSessionParamsDeflate_T));
  }
  else
  {
    rc = setupLZ4Session(&qz_session, compressionLevel);
  }

  if(QZ_OK != rc && QZ_DUPLICATE != rc)
    throw_exception(env, QZ_SETUP_SESSION_ERROR, rc);

  //jfieldID qzSessionBufferField = (*env)->GetFieldID(env,qatSessionClass,"qzSession","J");
  //(*env)->SetLongField(env, qatSessionObj,qzSessionBufferField, (jlong)qz_session);
  //set NUMA id
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  allocatePinnedMem(env,jc, qatSessionClass, qatSessionObj, internalBufferSizeInBytes, qzMaxCompressedLength(internalBufferSizeInBytes,&qz_session));
}



/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuff
 * compress ByteBuffer
 */
 jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuff(
    JNIEnv *env, jclass obj,jlong qzSession, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer, jint retryCount) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;
  //QzSession_T* qz_session = (QzSession_T*) qzSession;
  //QzSession_T* qz_session = (QzSession_T*)(*env)->GetDirectBufferAddress(env, qzSession);

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer); // process it even if it is not direct byte buffer

  if(!src)
    throw_exception(env, QZ_BUFFER_ERROR,-1);

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(!dest_buff)
    throw_exception(env, QZ_BUFFER_ERROR,-1);

  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,&compressedLength, 1);

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
    while(retryCount > 0 && QZ_OK != rc){
        rc = qzCompress(&qz_session, src, &srcSize, dest_buff, &compressedLength, 1);
        retryCount--;
    }
  }

  if (rc != QZ_OK)
    return rc;

  return compressedLength;
}
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * compress ByteArray
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteArray(
    JNIEnv *env, jclass obj, jlong qzSession, jbyteArray uncompressedArray, jint srcOffset,
    jint srcLen, jbyteArray compressedArray,jint destOffset, jint retryCount) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;
  //QzSession_T* qz_session = (QzSession_T*) qzSession;
  //QzSession_T* qz_session = (QzSession_T*)(*env)->GetDirectBufferAddress(env, qzSession);

  unsigned char *src =
      (unsigned char *)(*env)->GetByteArrayElements(env, uncompressedArray, 0);
  src += srcOffset;

  // QATZip max Compress length
  jint len = (*env)->GetArrayLength(env, compressedArray);
  unsigned char dest_buff[len];

  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1);

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
      while(retryCount > 0 && QZ_OK != rc){
        rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                            &compressedLength, 1);
        retryCount--;
      }
  }

  if (rc != QZ_OK){
    throw_exception(env, QZ_COMPRESS_ERROR, rc);
    return rc;
  }

  (*env)->ReleaseByteArrayElements(env, uncompressedArray, (signed char *)src,
                                   0);
  (*env)->SetByteArrayRegion(env, compressedArray, destOffset, compressedLength,
                             (signed char *)dest_buff);

  return compressedLength;
}


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuff
 * copies byte array from calling process and compress input, copies data back to out parameter
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuff(
    JNIEnv *env, jclass obj, jlong qzSession, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer, jint retryCount) {

  unsigned int srcSize = srcLen;
  unsigned int uncompressedLength = UINT_MAX;
  //QzSession_T* qz_session = (QzSession_T*) qzSession;
  //QzSession_T* qz_session = (QzSession_T*)(*env)->GetDirectBufferAddress(env, qzSession);

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  if(!src)
    return -1;

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(!dest_buff)
    return -1;

  int rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &uncompressedLength);

    if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
          while(retryCount > 0 && QZ_OK != rc){
              rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &uncompressedLength);
              retryCount--;
          }
    }
  if (rc != QZ_OK)
    return rc;

  return uncompressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteArray
 * copies byte array from calling process and compress input, copies data back to out parameter
 */

 JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArray(
     JNIEnv *env, jclass obj, jlong qzSession, jbyteArray compressedArray, jint srcOffset,
     jint srcLen, jbyteArray destArray, jint uncompressedLength,jint retryCount) {
   unsigned char *src =
       (unsigned char *)(*env)->GetByteArrayElements(env, compressedArray, 0);
   src += srcOffset;

   unsigned char dest_buff[uncompressedLength];
   unsigned int srcSize = srcLen;
   unsigned int dest_len = -1;
   //QzSession_T* qz_session = (QzSession_T*) qzSession;
   //QzSession_T* qz_session = (QzSession_T*)(*env)->GetDirectBufferAddress(env, qzSession);

   int rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &dest_len);

   if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
     while(retryCount > 0 && QZ_OK != rc){
        rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &dest_len);
        retryCount--;
     }
   }
   if (rc != QZ_OK)
     return rc;

   (*env)->ReleaseByteArrayElements(env, compressedArray, (signed char *)src, 0);
   (*env)->SetByteArrayRegion(env, destArray, 0, uncompressedLength,
                              (signed char *)dest_buff);

   return uncompressedLength;
 }

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * returns maximum compressed size for a given source size
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(JNIEnv *env,
                                                               jclass jobj,
                                                               jobject qzSession,
                                                               jlong srcSize
                                                               ) {
  //QzSession_T* qz_session = (QzSession_T*) qzSession;
  //QzSession_T* sess = (QzSession_T*)(*env)->GetDirectBufferAddress(env, qzSession);
  return qzMaxCompressedLength(srcSize, &qz_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * teardown QAT session and release QAT hardware resources
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env, jclass jobj, jlong qzSession, jobject srcBuff, jobject destBuff) {


  //QzSession_T* qz_session = (QzSession_T*) qzSession;
  //QzSession_T* qz_session = (QzSession_T*)(*env)->GetDirectBufferAddress(env, qzSession);
  void *unSrcBuff = (void *)(*env)->GetDirectBufferAddress(env, srcBuff);
  void *unDestBuff = (void *)(*env)->GetDirectBufferAddress(env, destBuff);
  //setbuf(stdout, NULL);
  //printf("c: address %ld",qzSession);
  freePinnedMem(unSrcBuff, unDestBuff);

  int rc = qzTeardownSession(&qz_session);
  if (rc != QZ_OK){
    throw_exception(env,QZ_TEARDOWN_ERROR,rc);
    return 0;
   }
  //free(qz_session);
  qzClose(&qz_session);

  if (rc != QZ_OK)
      return rc;

  //free((void*)sess);
  return QZ_OK;
}