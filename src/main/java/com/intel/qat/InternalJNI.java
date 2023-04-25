/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

public class InternalJNI {
  //static { System.loadLibrary("qat-java"); } //
  static {Native.loadLibrary();}

  // change to default
  static native void setup(Buffers internalBuffers, int softwareBackup, long internalBufferSizeInBytes, String compressionAlgo, int compressionLevel);

  static native int teardown();
  static native int maxCompressedSize(long qzSession, long sourceSize);

  //static native ByteBuffer[] nativeSrcDestByteBuff(long srcSize, long destSize);
  static native int freeNativesrcDestByteBuff(long qzSession, ByteBuffer srcbuff, ByteBuffer destbuff);
  static native int compressByteBuff(long qzSession, ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int destOffset, int retryCount);
  static native int compressByteArray(long qzSession, byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int retryCount);
  static native int decompressByteBuff(long qzSession, ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int destOffset, int retryCount);
  static native int decompressByteArray(long qzSession, byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int retryCount);
}