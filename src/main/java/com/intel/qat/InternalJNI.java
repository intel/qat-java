/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

/** JNI wrapper, for internal use. */
enum InternalJNI {
  ;

  static {
    Native.loadLibrary();
    initFieldIDs();
  }

  static native void initFieldIDs();

  static native int setup(
      QatZipper qzip,
      int algo,
      int level,
      int mode,
      int pmode,
      int dataFormat,
      int hwBufferSize,
      int logLevel);

  static native int maxCompressedSize(int qzKey, long sourceSize);

  static native int compressByteArray(
      QatZipper qzip,
      int qzKey,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressByteArray(
      QatZipper qzip,
      int qzKey,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressByteBuffer(
      int qzKey,
      ByteBuffer srcBuffer,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressByteBuffer(
      int qzKey,
      ByteBuffer srcBuffer,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBuffer(
      int qzKey,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBuffer(
      int qzKey,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBufferSrc(
      int qzKey,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      byte[] dstArr,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBufferSrc(
      int qzKey,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      byte[] dstArr,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBufferDst(
      int qzKey,
      ByteBuffer src,
      byte[] srcArr,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBufferDst(
      int qzKey,
      ByteBuffer src,
      byte[] srcArr,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native long zstdGetSeqProdFunction();

  static native long zstdCreateSeqProdState();

  static native void zstdFreeSeqProdState(long sequenceProdState);

  static native int teardown(int qzKey);

  static native int setLogLevel(int logLevel);
}
