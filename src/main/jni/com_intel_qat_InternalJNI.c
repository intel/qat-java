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

static __thread QzSession_T qz_session;
static __thread QzSessionParams_T qz_params;
static __thread QzSessionParamsDeflate_T deflate_params;

static __thread int cpu_id;
static __thread int numa_id;
static __thread jobject sourceAddr;
static __thread jobject destAddr;
static QzPollingMode_T polling_mode = QZ_BUSY_POLLING;
static QzDataFormat_T data_fmt = QZ_DEFLATE_GZIP_EXT;
//static unsigned int compressionLevel = 6;

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * initializes QAT hardware and sets up a session
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env, jclass jc,jint executionPath) {
  int rc;
setbuf(stdout, NULL);
printf("session to be established..%d",executionPath);
  if(executionPath == 0){
    rc = qzInit(&qz_session, 0);
    printf("hardware session obtained..%d",rc);
  }
  else if(executionPath == 1)
    rc = qzInit(&qz_session, 1);

  if (rc != QZ_OK && rc != QZ_DUPLICATE)
    return rc;

  rc = qzGetDefaults(&qz_params);
  if (rc != QZ_OK)
    return rc;

  //qz_params.comm.mem_type = PINNED_MEM;
  //deflate_params.common_params.comp_algorithm = comp_algorithm;

  rc = qzGetDefaultsDeflate(&deflate_params);
  if (rc != QZ_OK)
    return rc;

  deflate_params.data_fmt = data_fmt;
  deflate_params.common_params.comp_lvl = 6;
  deflate_params.common_params.polling_mode = polling_mode;

  rc = qzSetupSessionDeflate(&qz_session, &deflate_params);
  if (rc != QZ_OK)
    return rc;

  //set NUMA id
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  return QZ_OK;
}


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Allocate new ByteBuffer using qzMalloc
 */
JNIEXPORT jobjectArray JNICALL Java_com_intel_qat_InternalJNI_nativeSrcDestByteBuff
(JNIEnv *env, jclass jc, jlong srcSize, jlong destSize) {
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
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuff(
    JNIEnv *env, jclass obj, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer, jboolean retry, jint retryCount) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  if(!src)
    return -1;

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(!dest_buff)
    return -1;

  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1);


  if(rc == QZ_NOSW_NO_INST_ATTACH && retry && retryCount > 0){
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
    JNIEnv *env, jclass obj, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer, jboolean retry, jint retryCount) {

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

    if(rc == QZ_NOSW_NO_INST_ATTACH && retry && retryCount > 0){
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
                                                               jint srcSize) {
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
