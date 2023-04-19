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

static __thread QzSession_T qz_session;
static __thread QzSessionParams_T qz_params;
static __thread QzSessionParamsDeflate_T deflate_params;
static __thread QzSessionParamsLZ4_T lz4_params;

static __thread int cpu_id;
static __thread int numa_id;
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


int setupDeflateSession(int compressionLevel){

    int rc = qzGetDefaultsDeflate(&deflate_params);
    if (rc != QZ_OK)
        return rc;

    deflate_params.data_fmt = data_fmt;
    deflate_params.common_params.comp_lvl = compressionLevel;
    deflate_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionDeflate(&qz_session, &deflate_params);
}

int setupLZ4Session(int compressionLevel){

    int rc = qzGetDefaultsLZ4(&lz4_params);

    if (rc != QZ_OK)
      return rc;

    lz4_params.common_params.comp_lvl = compressionLevel;
    lz4_params.common_params.polling_mode = polling_mode;

    return qzSetupSessionLZ4(&qz_session, &lz4_params);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Allocate new ByteBuffer using qzMalloc
 */
jobjectArray nativeSrcDestByteBuff
(JNIEnv *env, jclass jc,jlong srcSize, jlong destSize) {
  void* tempSrcAddr = qzMalloc(srcSize, numa_id, true);
  void* tempDestAddr = qzMalloc(destSize, numa_id, true);

  if(NULL == tempSrcAddr || NULL == tempDestAddr)
  {
    printf("qzMalloc::PINNED memory is not available\n");
    tempSrcAddr = NULL;
    tempDestAddr = NULL;
      //throw std::runtime_error("failed to allocate native memory");

  }

  jobjectArray ret = (jobjectArray)(*env)->NewObjectArray(env,2,(*env)->FindClass(env,"java/nio/ByteBuffer"),NULL);
  (*env)->SetObjectArrayElement(env,ret,0,(*env)->NewDirectByteBuffer(env,tempSrcAddr,srcSize));
  (*env)->SetObjectArrayElement(env,ret,1,(*env)->NewDirectByteBuffer(env,tempDestAddr,destSize));

  return ret;
}


JNIEXPORT jobjectArray JNICALL Java_com_intel_qat_InternalJNI_setupHardware(JNIEnv *env, jclass jc, jlong internalBufferSizeInBytes, jstring compressionAlgo, jint compressionLevel) {
  int rc;
  const char* nativeCompressionAlgo = (*env)->GetStringUTFChars(env, compressionAlgo, 0);

  rc = qzInit(&qz_session, 0);

  if (rc != QZ_OK && rc != QZ_DUPLICATE)
    throw_exception(env, QZ_HW_INIT_ERROR, rc);

  if(strncmp(nativeCompressionAlgo , "deflate", strlen(nativeCompressionAlgo)))
  {
    rc = setupDeflateSession(compressionLevel);
  }
  else if(strncmp(nativeCompressionAlgo, "lz4", strlen(nativeCompressionAlgo)))
  {
    rc = setupLZ4Session(compressionLevel);
  }

  if(QZ_OK != rc)
    throw_exception(env, QZ_SETUP_SESSION_ERROR, rc);
  //set NUMA id
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  return nativeSrcDestByteBuff(env,jc,internalBufferSizeInBytes, qzMaxCompressedLength(internalBufferSizeInBytes,&qz_session));
}


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupSoftware
 * initializes QAT hardware and sets up a software session
 */
JNIEXPORT jobjectArray JNICALL Java_com_intel_qat_InternalJNI_setupAUTO(JNIEnv *env, jclass jc, jlong internalBufferSizeInBytes, jstring compressionAlgo, jint compressionLevel) {
  int rc;
  const char* nativeCompressionAlgo = (*env)->GetStringUTFChars(env, compressionAlgo, 0);

  rc = qzInit(&qz_session, 1);

/*
  if(QZ_NOSW_NO_HW == rc){
    rc = qzInit(&qz_session, 1);
    softwareSession = 1;
  }
  */
  if (rc != QZ_OK && rc != QZ_DUPLICATE)
    throw_exception(env, QZ_HW_INIT_ERROR, rc);

 if(strncmp(nativeCompressionAlgo , "deflate", strlen(nativeCompressionAlgo)))
  {
    rc = setupDeflateSession(compressionLevel);
  }
  else if(strncmp(nativeCompressionAlgo, "lz4", strlen(nativeCompressionAlgo)))
  {
    rc = setupLZ4Session(compressionLevel);
  }

  if (rc != QZ_OK)
      throw_exception(env, QZ_SETUP_SESSION_ERROR, rc);

  //set NUMA id
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  return nativeSrcDestByteBuff(env,jc,internalBufferSizeInBytes, qzMaxCompressedLength(internalBufferSizeInBytes,&qz_session));
}



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
    JNIEnv *env, jclass obj, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer, jint retryCount) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer); // process it even if it is not direct byte buffer

  if(!src)
    return -1; // throw exception

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(!dest_buff)
    return -1; // throw exception

  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1);


  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
    while(retryCount > 0 && qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1) != QZ_OK){
        retryCount--;
    }
  }

  if (rc != QZ_OK)
    return rc;

  return compressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuff
 * copies byte array from calling process and compress input, copies data back to out parameter
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuff(
    JNIEnv *env, jclass obj, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer, jint retryCount) {

  unsigned int srcSize = srcLen;
  unsigned int uncompressedLength = UINT_MAX;

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
          while(retryCount > 0 && qzDecompress(&qz_session, src, &srcSize, dest_buff, &uncompressedLength) != QZ_OK){
              retryCount--;
          }
    }
  if (rc != QZ_OK)
    return rc;

  return uncompressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * returns maximum compressed size for a given source size
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(JNIEnv *env,
                                                               jclass jobj,
                                                               jlong srcSize) {
  return qzMaxCompressedLength(srcSize, &qz_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * teardown QAT session and release QAT hardware resources
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                      jclass jobj) {
  int rc = qzTeardownSession(&qz_session);
  if (rc != QZ_OK)
    return rc;

  return qzClose(&qz_session);
}
