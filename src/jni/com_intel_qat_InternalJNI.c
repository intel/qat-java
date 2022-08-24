#define _GNU_SOURCE
#include "com_intel_qat_InternalJNI.h"
#include "qatzip.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sched.h>
#include <numa.h>
#include <limits.h>

static __thread QzSession_T qz_session;
static __thread QzSessionParams_T qz_params;

static __thread int cpu_id;
static __thread int numa_id;
static __thread jobject sourceAddr;
static __thread jobject destAddr;
static PinMem_T mem_type = PINNED_MEM;


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * initializes QAT hardware and sets up a session
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env, jclass jc) {
  int rc = qzInit(&qz_session, 0);
  if (rc != QZ_OK && rc != QZ_DUPLICATE)
    return rc;

  rc = qzGetDefaults(&qz_params);
  if (rc != QZ_OK)
    return rc;

  qz_params.mem_type = PINNED_MEM;
  qz_params.comp_lvl = 6;
  qz_params.is_busy_polling = true;

  rc = qzSetupSession(&qz_session, &qz_params);
  if (rc != QZ_OK)
    return rc;

  //set NUMA id
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  return QZ_OK;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuffNoRef
 * Allocate new ByteBuffer using qzMalloc and store the memory address in thread local variable
 */
JNIEXPORT jobject JNICALL Java_com_intel_qat_InternalJNI_nativeByteBuffThreadLocal
(JNIEnv *env, jclass jc, jlong size, jboolean ifSource) {

  void* tempAddr = qzMalloc(size, numa_id, true);
  if(NULL == tempAddr)
  {
    printf("qzMalloc::PINNED memory is not available\n");

    if(NULL == (tempAddr = qzMalloc(size, numa_id, false))){
      printf("qzMalloc::not able to allocate memory\n");
      //throw std::runtime_error("failed to allocate native memory");
    }
  }

  jobject tempByteBuff = (*env)->NewDirectByteBuffer(env,tempAddr,size);
  if(ifSource == true)
    sourceAddr = tempByteBuff;
  else
    destAddr = tempByteBuff;

  return tempByteBuff;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Allocate new ByteBuffer using qzMalloc
 */
JNIEXPORT jobject JNICALL Java_com_intel_qat_InternalJNI_nativeByteBuff
(JNIEnv *env, jclass jc, jlong size) {
  void* tempAddr = qzMalloc(size, numa_id, true);
  if(NULL == tempAddr)
  {
    printf("qzMalloc::PINNED memory is not available\n");

    if(NULL == (tempAddr = qzMalloc(size, numa_id, false))){
      printf("qzMalloc::not able to allocate memory\n");
      //throw std::runtime_error("failed to allocate native memory");
    }
  } 
  return (*env)->NewDirectByteBuffer(env,tempAddr,size);
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

    if(NULL == (tempSrcAddr = qzMalloc(srcSize, numa_id, false)) || NULL == (tempDestAddr = qzMalloc(destSize, numa_id, false))){
      printf("qzMalloc::not able to allocate memory\n");
      return NULL;
      //throw std::runtime_error("failed to allocate native memory");
    }
  }
 
  jobjectArray ret = (jobjectArray)(*env)->NewObjectArray(env,2,(*env)->FindClass(env,"java/nio/ByteBuffer"),NULL);  
  (*env)->SetObjectArrayElement(env,ret,0,(*env)->NewDirectByteBuffer(env,tempSrcAddr,srcSize));
  (*env)->SetObjectArrayElement(env,ret,1,(*env)->NewDirectByteBuffer(env,tempDestAddr,destSize));

  return ret; 
}


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativeByteBuff
 * frees native allocated ByteBuffer
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativeByteBuff
(JNIEnv *env, jclass jc, jobject buff) {

  unsigned char *unBuff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, buff);
  qzFree((void*)unBuff);
  return 0;
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
  qzFree((void*)unSrcBuff);

  unsigned char *unDestBuff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuff);

  qzFree((void*)unDestBuff);
  return 0;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativeByteBuffThreadLocal
 * frees native allocated ByteBuffer and clears thread local memory address
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativeByteBuffThreadLocal
(JNIEnv *env, jclass jc, jobject buff, jboolean ifSource) {

  unsigned char *unBuff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, buff);

  qzFree((void*)unBuff);
  if(ifSource)
    sourceAddr = NULL;
  else
    destAddr = NULL;

  return 1;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * compress ByteArray
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteArray(
    JNIEnv *env, jclass obj, jbyteArray uncompressedArray, jint srcOffset,
    jint srcLen, jbyteArray compressedArray) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;
  jboolean isCopy;

  unsigned char *src =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, uncompressedArray, &isCopy);
      
  src += srcOffset;

  // QATZip max Compress length
  jint len = (*env)->GetArrayLength(env, compressedArray);

  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, compressedArray, 0);

  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1);
  if (rc != QZ_OK)
    return rc;

  (*env)->ReleasePrimitiveArrayCritical(env, uncompressedArray, (signed char *)src,0);

  (*env)->ReleasePrimitiveArrayCritical(env, compressedArray, (signed char *)dest_buff, 0);

  return compressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuff
 * compress ByteBuffer
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuff(
    JNIEnv *env, jclass obj, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);
  
  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1);

  if (rc != QZ_OK)
    return rc;

  return compressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuffNoRef
 * compress ByteBuffer using thread local memory address variables
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuffThreadLocal(
    JNIEnv *env, jclass obj, jint srcOffset, jint srcLen, jint destLen) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = destLen;

  unsigned char *src = (unsigned char *)sourceAddr;
  src+= srcOffset;
  unsigned char *dest_buff = (unsigned char *)destAddr;
  
  int rc = qzCompress(&qz_session, src, &srcSize, dest_buff,
                      &compressedLength, 1);

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
    JNIEnv *env, jclass obj, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer) {
  
  unsigned int srcSize = srcLen;
  unsigned int uncompressedLength = UINT_MAX;

  unsigned char *src =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);
  
  int rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &uncompressedLength);
  if (rc != QZ_OK)
    return rc;

  return uncompressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuffNoRef
 * decompress Bytearray using thread local variable which holds compressed ByteBuffer
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuffThreadLocal(
    JNIEnv *env, jclass obj, jint srcOffset, jint srcLen, jint destLen) {
  
  unsigned int srcSize = srcLen;
  unsigned int uncompressedLength = destLen;

  unsigned char *src =
      (unsigned char *)destAddr;

  src+= srcOffset;
  unsigned char *dest_buff =
      (unsigned char *)sourceAddr;
  
  int rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &uncompressedLength);
  if (rc != QZ_OK)
    return rc;

  return uncompressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompress
 * decompress Bytearray
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArray(
    JNIEnv *env, jclass obj, jbyteArray compressedArray, jint srcOffset,
    jint srcLen, jbyteArray destArray, jint uncompressedLength) {
  
  unsigned char *src =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, compressedArray, 0);

  src += srcOffset;
  unsigned int srcSize = srcLen;
  unsigned int dest_len = -1;

  unsigned char *dest_buff =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, destArray, 0);
      
  int rc = qzDecompress(&qz_session, src, &srcSize, dest_buff, &dest_len);
  if (rc != QZ_OK)
    return rc;

  (*env)->ReleasePrimitiveArrayCritical(env, compressedArray, (signed char *)src, 0);

  (*env)->ReleasePrimitiveArrayCritical(env, destArray, (signed char *)dest_buff, 0);

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
