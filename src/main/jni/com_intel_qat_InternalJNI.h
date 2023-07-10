/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_intel_qat_InternalJNI */

#ifndef _Included_com_intel_qat_InternalJNI
#define _Included_com_intel_qat_InternalJNI
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (IJII)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setup
  (JNIEnv *, jclass, jobject, jint, jlong, jint, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressDirectByteBuffer
  (JNIEnv *, jclass, jlong, jobject, jint, jint, jobject, jint, jint, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressArrayOrBuffer
  (JNIEnv *, jclass, jlong, jobject, jbyteArray, jint, jint, jbyteArray, jint, jint, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressDirectByteBuffer
  (JNIEnv *, jclass, jlong, jobject, jint, jint, jobject, jint, jint, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressArrayOrBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressArrayOrBuffer
  (JNIEnv *, jclass, jlong, jobject, jbyteArray, jint, jint, jbyteArray, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
