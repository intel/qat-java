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

// all this goes to QATSession
//static __thread QzSession_T qz_session;
//static __thread QzSessionParams_T qz_params;
//static __thread QzSessionParamsDeflate_T deflate_params;
static __thread QzSessionParamsLZ4_T lz4_params;

static __thread int cpu_id;
static __thread int numa_id;
// all this goes to QATSession

//static __thread jobject sourceAddr;
//static __thread jobject destAddr;
static QzPollingMode_T polling_mode = QZ_BUSY_POLLING;
static QzDataFormat_T data_fmt = QZ_DEFLATE_GZIP_EXT;
//static unsigned int compressionLevel = 6;

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupHardware
 * initializes QAT hardware and sets up a session
 */


int setupDeflateSession(QzSession_T* sess, int compressionLevel){

    QzSessionParamsDeflate_T deflate_params;

    int rc = qzGetDefaultsDeflate(&deflate_params);
    if (rc != QZ_OK)
        return rc;

    deflate_params.data_fmt = data_fmt;
    deflate_params.common_params.comp_lvl = compressionLevel;
    deflate_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionDeflate(sess, &deflate_params);
}

int setupLZ4Session(QzSession_T* sess, int compressionLevel){

    int rc = qzGetDefaultsLZ4(&lz4_params);

    if (rc != QZ_OK)
      return rc;

    lz4_params.common_params.comp_lvl = compressionLevel;
    lz4_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionLZ4(sess, &lz4_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Allocate new ByteBuffer using qzMalloc
 */
jobjectArray nativeSrcDestByteBuff
(JNIEnv *env, jclass jc, jobject buffersObj, jlong srcSize, jlong destSize) {
  jclass buffers_class;
  void* tempSrcAddr = qzMalloc(srcSize, numa_id, true);
  void* tempDestAddr = qzMalloc(destSize, numa_id, true);

  buffers_class = (*env)->GetObjectClass(env, buffersObj);

  if(NULL == tempSrcAddr || NULL == tempDestAddr)
  {
    tempSrcAddr = NULL;
    tempDestAddr = NULL;
      //throw std::runtime_error("failed to allocate native memory");

  }

  jfieldID unCompressedBufferField = (*env)->GetFieldID(env,buffers_class,"unCompressedBuffer","java/nio/ByteBuffer");
  jfieldID compressedBufferField = (*env)->GetFieldID(env,buffers_class,"compressedBuffer","java/nio/ByteBuffer");

  //jobjectArray ret = (jobjectArray)(*env)->NewObjectArray(env,2,(*env)->FindClass(env,"java/nio/ByteBuffer"),NULL);
  //(*env)->SetObjectArrayElement(env,ret,0,(*env)->NewDirectByteBuffer(env,tempSrcAddr,srcSize));
  //(*env)->SetObjectArrayElement(env,ret,1,(*env)->NewDirectByteBuffer(env,tempDestAddr,destSize));

  (*env)->SetObjectField(env, buffers_class,unCompressedBufferField, (*env)->NewDirectByteBuffer(env,tempSrcAddr,srcSize));
  (*env)->SetObjectField(env, buffers_class,compressedBufferField, (*env)->NewDirectByteBuffer(env,tempDestAddr,destSize));
}


JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env, jclass jc, jobject buffersObj,jint softwareBackup, jlong internalBufferSizeInBytes, jstring compressionAlgo, jint compressionLevel) {
  int rc;
  const char* nativeCompressionAlgo = (*env)->GetStringUTFChars(env, compressionAlgo, 0);
  const unsigned char sw_backup = (unsigned char) softwareBackup;
  QzSession_T qz_session;
  jclass buffers_class;

  buffers_class = (*env)->GetObjectClass(env, buffersObj);

  rc = qzInit(&qz_session, sw_backup);

  if (rc != QZ_OK || rc == QZ_DUPLICATE)
    throw_exception(env, QZ_HW_INIT_ERROR, rc);

  int qzSessionSize = sizeof(&qz_session);

  jfieldID qzSessionBufferField = (*env)->GetFieldID(env,buffers_class,"qzSession","L");
  (*env)->SetLongField(env, buffers_class,qzSessionBufferField, (jlong)&qz_session);

  if(strncmp(nativeCompressionAlgo , "deflate", strlen(nativeCompressionAlgo)))
  {
    rc = setupDeflateSession(&qz_session, compressionLevel);
  }
  else if(strncmp(nativeCompressionAlgo, "lz4", strlen(nativeCompressionAlgo)))
  {
    rc = setupLZ4Session(&qz_session, compressionLevel);
  }
  else{
    throw_exception(env,QZ_HW_INIT_ERROR,rc);
  }

  if(QZ_OK != rc)
    throw_exception(env, QZ_SETUP_SESSION_ERROR, rc);

  //set NUMA id
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  nativeSrcDestByteBuff(env,jc, buffersObj, internalBufferSizeInBytes, qzMaxCompressedLength(internalBufferSizeInBytes,&qz_session));


}


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupSoftware
 * initializes QAT hardware and sets up a software session
 *
JNIEXPORT jobjectArray JNICALL Java_com_intel_qat_InternalJNI_setupAUTO(JNIEnv *env, jclass jc, jlong internalBufferSizeInBytes, jstring compressionAlgo, jint compressionLevel) {

}*/



/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativesrcDestByteBuff
 * frees native allocated ByteBuffer
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativesrcDestByteBuff
(JNIEnv *env, jclass jc, jobject srcBuff, jobject destBuff) {

  unsigned char *unSrcBuff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuff);

  if(unSrcBuff)
    qzFree((void*)unSrcBuff);

  unsigned char *unDestBuff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuff);

  if(unDestBuff)
    qzFree((void*)unDestBuff);

  return 0;
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
  QzSession_T* sess = (QzSession_T*) qzSession;

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer); // process it even if it is not direct byte buffer

  if(!src)
    return -1; // throw exception

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(!dest_buff)
    return -1; // throw exception

  int rc = qzCompress(sess, src, &srcSize, dest_buff,&compressedLength, 1);

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
    while(retryCount > 0 && QZ_OK != rc){
        rc = qzCompress(sess, src, &srcSize, dest_buff, &compressedLength, 1);
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
    jint srcLen, jbyteArray compressedArray,jint retryCount) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;
  QzSession_T* sess = (QzSession_T*) qzSession;

  unsigned char *src =
      (unsigned char *)(*env)->GetByteArrayElements(env, uncompressedArray, 0);
  src += srcOffset;

  // QATZip max Compress length
  jint len = (*env)->GetArrayLength(env, compressedArray);
  unsigned char dest_buff[len];

  int rc = qzCompress(sess, src, &srcSize, dest_buff,
                      &compressedLength, 1);

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
      while(retryCount > 0 && QZ_OK != rc){
        rc = qzCompress(sess, src, &srcSize, dest_buff,
                            &compressedLength, 1);
        retryCount--;
      }
  }

  if (rc != QZ_OK)
    return rc;

  // throw error if srcSize != srcLen - srcOffset + 1, get the confirmation if this is due to destination not big enough

  (*env)->ReleaseByteArrayElements(env, uncompressedArray, (signed char *)src,
                                   0);
  (*env)->SetByteArrayRegion(env, compressedArray, 0, compressedLength,
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
  QzSession_T* sess = (QzSession_T*) qzSession;

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  if(!src)
    return -1;

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(!dest_buff)
    return -1;

  int rc = qzDecompress(sess, src, &srcSize, dest_buff, &uncompressedLength);

    if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
          while(retryCount > 0 && QZ_OK != rc){
              rc = qzDecompress(sess, src, &srcSize, dest_buff, &uncompressedLength);
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
   QzSession_T* sess = (QzSession_T*) qzSession;

   int rc = qzDecompress(sess, src, &srcSize, dest_buff, &dest_len);

   if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
     while(retryCount > 0 && QZ_OK != rc){
        rc = qzDecompress(sess, src, &srcSize, dest_buff, &dest_len);
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
                                                               jlong qzSession,
                                                               jlong srcSize
                                                               ) {
  QzSession_T* sess = (QzSession_T*) qzSession;
  return qzMaxCompressedLength(srcSize, sess);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * teardown QAT session and release QAT hardware resources
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                      jclass jobj,
                                                      jlong qzSession) {
  QzSession_T* sess = (QzSession_T*) qzSession;

  int rc = qzTeardownSession(sess);
  if (rc != QZ_OK)
    return rc;

  rc = qzClose(sess);

  if (rc != QZ_OK)
      return rc;

  free((void*)sess);
  return QZ_OK;
}
