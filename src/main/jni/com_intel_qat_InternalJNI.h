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
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_setup
  (JNIEnv *, jclass,jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    teardown
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown
  (JNIEnv *, jclass);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize
  (JNIEnv *, jclass, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeSrcDestByteBuff
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobjectArray JNICALL Java_com_intel_qat_InternalJNI_nativeSrcDestByteBuff
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativeByteBuff
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativesrcDestByteBuff
  (JNIEnv *, jclass, jobject,jobject);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuff
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuff
  (JNIEnv *, jclass, jobject, jint, jint, jobject, jboolean, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuff
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuff
  (JNIEnv *, jclass, jobject, jint, jint, jobject, jboolean, jint);

#ifdef __cplusplus
}
#endif
#endif
