package com.intel.qat;

import java.nio.ByteBuffer;

public class InternalJNI {
  static { System.loadLibrary("qat-java"); }

  // change to default
  static native int setup();
  static native int teardown();
  static native int maxCompressedSize(int sourceSize);
  static native int compressByteArray(byte[] src, int srcOffset, int srcLen, byte[] dest);
  static native ByteBuffer nativeByteBuff(long size);
  static native ByteBuffer nativeSrcDestByteBuff(long srcSize, long destSize);
  static native ByteBuffer nativeByteBuffThreadLocal(long size, boolean ifSourceByteBuff);
  static native int freeNativeByteBuff(ByteBuffer buff);
  static native int freeNativesrcDestByteBuff(ByteBuffer srcbuff, ByteBuffer destbuff);
  static native int freeNativeByteBuffThreadLocal(ByteBuffer buff, boolean ifSourceByteBuff);
  static native int compressByteBuff(ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest);
  static native int compressByteBuffThreadLocal(int srcOffset, int srcLen, int compressedLength);
  static native int decompressByteBuff(ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest);
  static native int decompressByteBuffThreadLocal(int srcOffset, int srcLen, int uncompressedLength);
  static native int decompressByteArray(byte[] src, int srcOffset, int srcLen,byte[] dest, int uncompressedLength);
}