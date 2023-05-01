/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

public class InternalJNI {
  static {Native.loadLibrary();}

  static native void setup(QATSession qatSessionObject, int softwareBackup, long internalBufferSizeInBytes, int compressionAlgo, int compressionLevel);

  static native int teardown(long qzSession, ByteBuffer unCompressedBuffer, ByteBuffer compressedBuffer);
  static native int maxCompressedSize(long qzSession, long sourceSize);
  static native int compressByteBuff(long qzSession, ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int destOffset, int retryCount);
  static native int compressByteArray(long qzSession, byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int retryCount);
  static native int decompressByteBuff(long qzSession, ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int destOffset, int retryCount);
  static native int decompressByteArray(long qzSession, byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int retryCount);
}