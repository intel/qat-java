/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

/**
 * Class with static native function declaration
 */
public class InternalJNI {
  /**
   * This class contains static native method interface declarations required by JNI.
   */
  private InternalJNI(){}
  /**
   * loads libqatzip.so while loading through static block
   */
  static {Native.loadLibrary();}

  static native void setup(QATSession qatSessionObject, int softwareBackup, long internalBufferSizeInBytes, int compressionAlgo, int compressionLevel);

  static native int teardown(long qzSession, ByteBuffer unCompressedBuffer, ByteBuffer compressedBuffer);
  static native int maxCompressedSize(long qzSession, long sourceSize);
  static native int compressByteBuff(long qzSession, ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int retryCount, int last);
  static native int compressByteArray(long qzSession, byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int retryCount, int last);
  static native int decompressByteBuff(long qzSession, ByteBuffer src, int srcOffset, int srcLen, ByteBuffer dest, int retryCount);
  static native int decompressByteArray(long qzSession, byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int retryCount);
}