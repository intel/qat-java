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
   * This class contains static native method interface declarations required by
   * JNI.
   */
  private InternalJNI() {}

  /**
   * loads libqatzip.so while loading through static block
   */
  static {
    Native.loadLibrary();
  }

  static native void setup(QatZipper qatSessionObject, int softwareBackup,
      int compressionAlgo, int compressionLevel);

  static native int maxCompressedSize(long session, long sourceSize);

  static native int compressDirectByteBuffer(long session, ByteBuffer src,
      int srcOffset, int srcLen, ByteBuffer dest, int destOffset, int destLen,
      int retryCount);

  static native int compressArrayOrBuffer(long session, ByteBuffer srcBuffer,
      byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset,
      int destLen, int retryCount);

  static native int decompressDirectByteBuffer(long session, ByteBuffer src,
      int srcOffset, int srcLen, ByteBuffer dest, int destOffset, int destLen,
      int retryCount);

  static native int decompressArrayOrBuffer(long session, ByteBuffer srcBuffer,
      byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset,
      int destLen, int retryCount);

  static native int teardown(long session);
}
