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
  (JNIEnv *, jclass);

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
 * Method:    compress
 * Signature: ([BII[B)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteArray
  (JNIEnv *, jclass, jbyteArray, jint, jint, jbyteArray);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuff
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_com_intel_qat_InternalJNI_nativeByteBuff
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeSrcDestByteBuff
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobjectArray JNICALL Java_com_intel_qat_InternalJNI_nativeSrcDestByteBuff
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    nativeByteBuffNoRef
 * Signature: (J)Ljava/nio/ByteBuffer;
 */

JNIEXPORT jobject JNICALL Java_com_intel_qat_InternalJNI_nativeByteBuffThreadLocal
(JNIEnv*, jclass, jlong, jboolean);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativeByteBuff
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativeByteBuff
  (JNIEnv *, jclass, jobject);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativeByteBuff
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativesrcDestByteBuff
  (JNIEnv *, jclass, jobject,jobject);


/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    freeNativeByteBuffNoRef
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_freeNativeByteBuffThreadLocal
  (JNIEnv *, jclass, jobject, jboolean);
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuff
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuff
  (JNIEnv *, jclass, jobject, jint, jint, jobject);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuffNoRef
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteBuffThreadLocal
  (JNIEnv *, jclass, jint, jint,jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuff
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuff
  (JNIEnv *, jclass, jobject, jint, jint, jobject);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuffNoRef
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteBuffThreadLocal
  (JNIEnv *, jclass, jint, jint,jint);
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompress
 * Signature: ([BII[BI)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArray
  (JNIEnv *, jclass, jbyteArray, jint, jint, jbyteArray, jint);

#ifdef __cplusplus
}
#endif
#endif
