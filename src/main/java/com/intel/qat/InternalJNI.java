/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

/** Class with static native function declaration */
class InternalJNI {
  /** This class contains static native method interface declarations required by JNI. */
  private InternalJNI() {}

  /** loads libqatzip.so while loading through static block */
  static {
    Native.loadLibrary();
  }

  static native void initFieldIDs();

  static native void setup(QatZipper qzip, int algo, int level, int mode, int pmode);

  static native int maxCompressedSize(long session, long sourceSize);

  static native int compressByteArray(
      QatZipper qzip,
      long session,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressByteArray(
      QatZipper qzip,
      long session,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressByteBuffer(
      long session,
      ByteBuffer srcBuffer,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressByteBuffer(
      long session,
      ByteBuffer srcBuffer,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBuffer(
      long session,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBuffer(
      long session,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBufferSrc(
      long session,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      byte[] dstArr,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBufferSrc(
      long session,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      byte[] dstArr,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBufferDst(
      long session,
      ByteBuffer src,
      byte[] srcArr,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBufferDst(
      long session,
      ByteBuffer src,
      byte[] srcArr,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int teardown(long session);

  static native long zstdGetSeqProdFunction();

  static native int zstdStartDevice();

  static native void zstdStopDevice();

  static native long zstdCreateSeqProdState();

  static native void zstdFreeSeqProdState(long sequenceProducerState);
}
