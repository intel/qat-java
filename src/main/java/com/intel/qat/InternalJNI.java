/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

public class InternalJNI {
  static { System.loadLibrary("qat-java"); } //

  // change to default
  static native int setup(int executionPath, String compressionAlgo, int compressionLevel);
  static native int teardown();
  static native int maxCompressedSize(int sourceSize);

  static native ByteBuffer[] nativeSrcDestByteBuff(long srcSize, long destSize);
  static native int freeNativesrcDestByteBuff(ByteBuffer srcbuff, ByteBuffer destbuff);
  static native int compressByteBuff(ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int retryCount);
  static native int decompressByteBuff(ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int retryCount);
}
}