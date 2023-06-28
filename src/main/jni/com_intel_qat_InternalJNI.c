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
static const int LZ4 = 1;
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setupHardware
 * initializes QAT hardware and sets up a session
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
 * frees native allocated ByteBuffer
 */

void freePinnedMem(void* unSrcBuff, void *unDestBuff) {

  if(unSrcBuff != NULL)
    qzFree(unSrcBuff);

  if(unDestBuff != NULL)
    qzFree(unDestBuff);

}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Allocate new ByteBuffer using qzMalloc
 */
int allocatePinnedMem (JNIEnv *env, jclass jc, jclass qatSessionClass, jobject qatSessionObj,jint compressionAlgo, jlong srcSize, jlong destSize) {
  int rc;

  void* tempSrcAddr = qzMalloc(srcSize, numa_id, true);
  void* tempDestAddr = qzMalloc(destSize, numa_id, true);

  if(tempSrcAddr == NULL || tempDestAddr == NULL)
  {
     freePinnedMem(tempSrcAddr,tempDestAddr);
     if(compressionAlgo == 0){
        return 1;
     }
     tempSrcAddr = NULL;
     tempDestAddr = NULL;
  }

  jfieldID unCompressedBufferField = (*env)->GetFieldID(env,qatSessionClass,"unCompressedBuffer","Ljava/nio/ByteBuffer;");
  jfieldID compressedBufferField = (*env)->GetFieldID(env,qatSessionClass,"compressedBuffer","Ljava/nio/ByteBuffer;");

  if(tempSrcAddr != NULL)
    (*env)->SetObjectField(env, qatSessionObj,unCompressedBufferField, (*env)->NewDirectByteBuffer(env,tempSrcAddr,srcSize));

  if(tempDestAddr != NULL)
    (*env)->SetObjectField(env, qatSessionObj,compressedBufferField, (*env)->NewDirectByteBuffer(env,tempDestAddr,destSize));

  return QZ_OK;
}


JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env, jclass jc, jobject qatSessionObj
,jint softwareBackup, jlong internalBufferSizeInBytes, jint compressionAlgo, jint compressionLevel) {
  /*address sanitizer test
  void *p = malloc(1);
  free(p);
  free(p);
   throw_exception(env, " double free ",1);
  return;
  */
  int rc;
  QzSession_T* qz_session = (QzSession_T*)malloc(sizeof(QzSession_T));
  memset(qz_session,0,sizeof(QzSession_T));


  const unsigned char sw_backup = (unsigned char) softwareBackup;

  jclass qatSessionClass = (*env)->GetObjectClass(env, qatSessionObj);

  rc = qzInit(qz_session, sw_backup);

  if (rc != QZ_OK && rc != QZ_DUPLICATE){
    throw_exception(env, QZ_HW_INIT_ERROR, rc);
    return;
  }

  if(compressionAlgo == DEFLATE)
  {
    rc = setupDeflateSession(qz_session, compressionLevel);
  }
  else
  {
    rc = setupLZ4Session(qz_session);
  }

  if(compressionAlgo == DEFLATE && QZ_OK != rc){
    throw_exception(env, "LZ4 session not setup", rc);
    return;
  }
  if(QZ_OK != rc && QZ_DUPLICATE != rc){
    qzClose(qz_session);
    throw_exception(env, QZ_SETUP_SESSION_ERROR, rc);
    return;
  }
  cpu_id = sched_getcpu();
  numa_id = numa_node_of_cpu(cpu_id);

  jfieldID qzSessionBufferField = (*env)->GetFieldID(env,qatSessionClass,"qzSession","J");
  (*env)->SetLongField(env, qatSessionObj,qzSessionBufferField, (jlong)qz_session);

  if(QZ_OK != allocatePinnedMem(env,jc, qatSessionClass, qatSessionObj, compressionAlgo, internalBufferSizeInBytes, qzMaxCompressedLength(internalBufferSizeInBytes,qz_session)))
    throw_exception(env, QZ_HW_INIT_ERROR,INT_MIN);

}



/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuff
 * compress ByteBuffer
 */
 jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuff(
    JNIEnv *env, jclass obj,jlong qzSession, jobject srcBuffer,jint srcOffset, jint srcLen, jobject destBuffer,
    jint retryCount, jint last) {
  unsigned int srcSize = srcLen;
  unsigned int compressedLength = -1;

  QzSession_T* qz_session = (QzSession_T*) qzSession;

  unsigned char *src = (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  if(src == NULL){
    throw_exception(env, QZ_BUFFER_ERROR,INT_MAX);
    return -1;
  }
  src+= srcOffset;

  unsigned char *dest = (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(dest == NULL){
    throw_exception(env, QZ_BUFFER_ERROR,INT_MAX);
    return -1;
  }

  int rc = qzCompress(qz_session, src, &srcSize, dest,&compressedLength, (unsigned int) last);

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
    while(retryCount > 0 && QZ_OK != rc){
        rc = qzCompress(qz_session, src, &srcSize, dest, &compressedLength, (unsigned int) last);
        retryCount--;
    }
  }

  if (rc != QZ_OK){
    throw_exception(env,QZ_COMPRESS_ERROR,rc);
    return rc;
  }
  return compressedLength;
}
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * compress ByteArray
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteArray(
    JNIEnv *env, jclass obj, jlong qzSession, jbyteArray uncompressedArray, jint srcOffset,
    jint srcLen, jbyteArray compressedArray,jint destOffset, jint retryCount,int last) {

  unsigned int srcSize = srcLen;
  unsigned int compressedLength = 1;
  jboolean is_copy_src = false;

  QzSession_T* qz_session = (QzSession_T*) qzSession;

  unsigned char *src = (unsigned char *)(*env)->GetByteArrayElements(env, uncompressedArray, &is_copy_src);

  if(src == NULL){
    throw_exception(env, QZ_COMPRESS_ERROR,INT_MAX);
    return -1;
  }
  src += srcOffset;

  compressedLength = (unsigned int)(*env)->GetArrayLength(env, compressedArray);

  unsigned char *dest = (unsigned char *)(*env)->GetByteArrayElements(env, compressedArray, &is_copy_src);

   if(dest == NULL){
      throw_exception(env, QZ_COMPRESS_ERROR,INT_MAX);
      return -1;
   }

  dest += destOffset;

  int rc = qzCompress(qz_session, src, &srcSize, dest, &compressedLength, (unsigned int)last);

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
      while(retryCount > 0 && QZ_OK != rc){
        rc = qzCompress(qz_session, src, &srcSize, dest,
                            &compressedLength, (unsigned int)last);
        retryCount--;
      }
  }

  if (rc != QZ_OK){
    throw_exception(env, QZ_COMPRESS_ERROR, rc);
    return rc;
  }

  (*env)->ReleaseByteArrayElements(env, uncompressedArray, (signed char *)src, 0);
  (*env)->ReleaseByteArrayElements(env, compressedArray, (signed char *)dest, 0);

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
  QzSession_T* qz_session = (QzSession_T*) qzSession;

  unsigned char *src = (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);

  if(src == NULL){
    throw_exception(env, QZ_COMPRESS_ERROR,INT_MAX);
    return -1;
  }
  src += srcOffset;

  unsigned char *dest =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);

  if(dest == NULL){
    throw_exception(env, QZ_COMPRESS_ERROR,INT_MAX);
    return -1;
  }

  int rc = qzDecompress(qz_session, src, &srcSize, dest, &uncompressedLength);

    if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
          while(retryCount > 0 && QZ_OK != rc){
              rc = qzDecompress(qz_session, src, &srcSize, dest, &uncompressedLength);
              retryCount--;
          }
    }
  if (rc != QZ_OK){
  throw_exception(env, QZ_DECOMPRESS_ERROR, rc);
      return rc;
  }

  return uncompressedLength;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteArray
 * copies byte array from calling process and compress input, copies data back to out parameter
 */

 JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArray(
     JNIEnv *env, jclass obj, jlong qzSession, jbyteArray compressedArray, jint srcOffset,
     jint srcLen, jbyteArray destArray, jint destOffset,jint retryCount) {

   unsigned char *src = (unsigned char *)(*env)->GetByteArrayElements(env, compressedArray, 0); //label
   unsigned int destLen = UINT_MAX;

   if(src == NULL){
     throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
     return -1;
   }
   src += srcOffset;

   unsigned char* dest = (unsigned char *)(*env)->GetByteArrayElements(env, destArray, 0); // label

   if(dest == NULL){
      throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
      return -1;
   }
   dest += destOffset;

   unsigned int srcSize = srcLen;

   QzSession_T* qz_session = (QzSession_T*) qzSession;

   int rc = qzDecompress(qz_session, src, &srcSize, dest, &destLen);

   if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
     while(retryCount > 0 && QZ_OK != rc){
        rc = qzDecompress(qz_session, src, &srcSize, dest, &destLen);
        retryCount--;
     }
   }
   if (rc != QZ_OK){
       throw_exception(env, QZ_DECOMPRESS_ERROR, rc);
       return rc;
     }

   (*env)->ReleaseByteArrayElements(env, compressedArray, (signed char *)src, 0);
   (*env)->ReleaseByteArrayElements(env, destArray, (signed char *)dest,0);

   return destLen;
 }

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuffInLoop(
    JNIEnv *env, jclass obj, jlong qzSession,
    jobject srcBuffer, jint srcOffset, jint srcLen,
    jobject compressedBuffer,jint compressedBufferLength,
    jobject unCompressedBuffer, jint unCompressedBufferLength,
    jobject destBuffer, jint destOffset, jint destLen,
    jint retryCount) {
	  unsigned char *src = (unsigned char *)(*env)->GetDirectBufferAddress(env, srcBuffer);
	  if(src == NULL){
		throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
		return -1;
	  }
  	  unsigned char *dest = (unsigned char *)(*env)->GetDirectBufferAddress(env, destBuffer);
	  if(dest == NULL){
		throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
		return -1;
	  }
	  return decompressInLoop(env, obj,qzSession,src, srcOffset, srcLen,compressedBuffer, compressedBufferLength,
    				unCompressedBuffer, unCompressedBufferLength, dest, destOffset, destLen, retryCount);

}

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArrayInLoop(
    JNIEnv *env, jclass obj, jlong qzSession,
    jbyteArray srcBuffer, jint srcOffset, jint srcLen,
    jobject compressedBuffer,jint compressedBufferLength,
    jobject unCompressedBuffer, jint unCompressedBufferLength,
    jbyteArray destBuffer, jint destOffset, jint destLen,
    jint retryCount) {
      bool isCopy;
	  unsigned char *src = (unsigned char *)(*env)->GetByteArrayElements(env, srcBuffer, &isCopy);
	  if(src == NULL){
		throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
		return -1;
	  }
  	  unsigned char *dest = (unsigned char *)(*env)->GetByteArrayElements(env, destBuffer, &isCopy);
	  if(dest == NULL){
		throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
		return -1;
	  }
	  int decompressedSize = decompressInLoop(env, obj,qzSession,src, srcOffset, srcLen, compressedBuffer, compressedBufferLength,
    				 unCompressedBuffer, unCompressedBufferLength, dest, destOffset, destLen, retryCount);

      (*env)->ReleaseByteArrayElements(env, srcBuffer, (signed char *)src, 0);
      (*env)->ReleaseByteArrayElements(env, destBuffer, (signed char *)dest, 0);

       return decompressedSize;
    }


int decompressInLoop(JNIEnv *env, jclass obj,jlong qzSession,unsigned char *src, int srcOffset, int srcLen,jobject compressedBuffer,jint compressedBufferLength,
    jobject unCompressedBuffer, jint unCompressedBufferLength,  unsigned char *dest, int destOffset, jint destLen,jint retryCount){

  unsigned int totalSrcSize;
  unsigned int uncompressedLength = unCompressedBufferLength;
  unsigned int compressedBufferLengthRunning = compressedBufferLength;
  unsigned int uncompressedLengthRunning = uncompressedLength;

  unsigned char *comp = (unsigned char *)(*env)->GetDirectBufferAddress(env, compressedBuffer);
  unsigned char *uncomp = (unsigned char *)(*env)->GetDirectBufferAddress(env, unCompressedBuffer);

  QzSession_T* qz_session = (QzSession_T*) qzSession;
  int totalDecompressed = 0;
  int totalConsumed = 0;
  int srcLenRunning = srcLen;


    if(src == NULL){
        throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
        return -1;
    }

  src += srcOffset; // not needed to do here, can be done before this function call

  if(dest == NULL){
        throw_exception(env, QZ_DECOMPRESS_ERROR,INT_MAX);
    	return -1;
  }
  dest += destOffset; // not needed to do here, can be done before this function call

/*
  int minRemaining = compressedBufferLength > srcLen? srcLen: compressedBufferLength;
  memcpy(comp,src,minRemaining);

  int rc = qzDecompress(qz_session, comp, &compressedBufferLengthRunning, uncomp, &uncompressedLengthRunning); // merge it to loop

  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
        while(retryCount > 0 && QZ_OK != rc){
            rc = qzDecompress(qz_session, comp, &compressedBufferLength, uncomp, &uncompressedLengthRunning);
            retryCount--;
        }
  }
  if (rc != QZ_OK && rc!= QZ_BUF_ERROR && rc!= QZ_DATA_ERROR){
        throw_exception(env, QZ_DECOMPRESS_ERROR, rc);
        return rc;
  }

  memcpy(dest,uncomp,uncompressedLengthRunning);
  totalDecompressed += uncompressedLengthRunning;
  src += compressedBufferLengthRunning;
  dest += uncompressedLengthRunning;
  totalConsumed = compressedBufferLengthRunning;
  srcLenRunning -= compressedBufferLengthRunning;

  return totalDecompressed;
*/


	while(totalConsumed < srcLen){
	      int remaining = (srcLen - totalConsumed) < compressedBufferLength? (srcLen - totalConsumed): compressedBufferLength;
          memcpy(comp, src, remaining);

          compressedBufferLengthRunning = remaining;
		  uncompressedLengthRunning = uncompressedLength;

		  int rc = qzDecompress(qz_session, comp, &compressedBufferLengthRunning, uncomp, &uncompressedLengthRunning);

    	  if(rc == QZ_NOSW_NO_INST_ATTACH && retryCount > 0){
          	while(retryCount > 0 && QZ_OK != rc){
              	rc = qzDecompress(qz_session, comp, &compressedBufferLengthRunning, uncomp, &uncompressedLengthRunning);
                retryCount--;
          	}
    	  }

  		  if (rc != QZ_OK && rc!= QZ_BUF_ERROR && rc!= QZ_DATA_ERROR){
  			    throw_exception(env, QZ_DECOMPRESS_ERROR, rc);
      			return rc;
  	      }
		  totalConsumed += compressedBufferLengthRunning;
	      totalDecompressed += uncompressedLengthRunning;
		  src += compressedBufferLengthRunning;
		  memcpy(dest,uncomp,uncompressedLengthRunning);
	  	  dest += uncompressedLengthRunning;
		  uncompressedLengthRunning = uncompressedLength;
	}

  return totalDecompressed ; // should not be greater than destLen
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
  QzSession_T* qz_session = (QzSession_T*) qzSession;
  return qzMaxCompressedLength(srcSize, qz_session);
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * teardown QAT session and release QAT hardware resources
 */

JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env, jclass jobj, jlong qzSession, jobject srcBuff, jobject destBuff) {

  QzSession_T* qz_session = (QzSession_T*) qzSession;
  void *unSrcBuff = NULL;
  void *unDestBuff = NULL;

  if(srcBuff != NULL)
   unSrcBuff = (*env)->GetDirectBufferAddress(env, srcBuff);

  if(destBuff != NULL)
   unDestBuff = (*env)->GetDirectBufferAddress(env, destBuff);

  freePinnedMem(unSrcBuff, unDestBuff);

  if(qz_session == NULL)
    return QZ_OK;

  int rc = qzTeardownSession(qz_session);
  if (rc != QZ_OK){
    throw_exception(env,QZ_TEARDOWN_ERROR,rc);
    return 0;
   }

  qzClose(qz_session);

  if(qz_session != NULL){
    free(qz_session);
    qz_session = NULL;
   }

  return QZ_OK;
}