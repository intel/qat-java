/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

/**
 * JNI wrapper, for internal use.
 *
 * <p>The four compress entry points (and their decompress equivalents) are named by the kind of
 * source and destination they take, matching the convention used by OpenJDK's {@code
 * java.util.zip.Deflater}:
 *
 * <ul>
 *   <li>{@code Bytes...}: a {@code byte[]} + offset + length on that side
 *   <li>{@code ...Buffer}: a {@code long} absolute direct-buffer address (extracted in Java via
 *       {@code ((DirectBuffer)buf).address() + buf.position()}, which is a HotSpot intrinsic) +
 *       length
 * </ul>
 *
 * <p>Every op returns a packed {@code long}:
 *
 * <pre>{@code
 * bits  0..30  bytes_read    (uncompressed bytes consumed from src)
 * bits 31..61  bytes_written (bytes produced into dst)
 * }</pre>
 *
 * <p>or a negative value on error (matching the qatzip {@code QZ_*} error codes). Callers should
 * check {@code result < 0} before unpacking.
 *
 * <p>Note that direct-buffer entry points take an absolute address, so callers
 * <strong>must</strong> wrap calls in {@code try { ... } finally {
 * Reference.reachabilityFence(buf); }} to keep the Cleaner from freeing the buffer mid-call.
 */
enum InternalJNI {
  ;

  static {
    Native.loadLibrary();
    initFieldIDs();
  }

  static native void initFieldIDs();

  static native int setupSession(
      QatZipper qzip,
      int algo,
      int level,
      int mode,
      int pmode,
      int dataFormat,
      int hwBufferSize,
      int logLevel);

  static native int maxCompressedLength(int qzKey, long sourceSize);

  // ---- compress ----

  static native long compressBytesBytes(
      int qzKey,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native long compressBytesBuffer(
      int qzKey, byte[] src, int srcOff, int srcLen, long dstAddr, int dstLen, int retryCount);

  static native long compressBufferBytes(
      int qzKey, long srcAddr, int srcLen, byte[] dst, int dstOff, int dstLen, int retryCount);

  static native long compressBufferBuffer(
      int qzKey, long srcAddr, int srcLen, long dstAddr, int dstLen, int retryCount);

  // ---- decompress ----

  static native long decompressBytesBytes(
      int qzKey,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native long decompressBytesBuffer(
      int qzKey, byte[] src, int srcOff, int srcLen, long dstAddr, int dstLen, int retryCount);

  static native long decompressBufferBytes(
      int qzKey, long srcAddr, int srcLen, byte[] dst, int dstOff, int dstLen, int retryCount);

  static native long decompressBufferBuffer(
      int qzKey, long srcAddr, int srcLen, long dstAddr, int dstLen, int retryCount);

  // ---- other ----

  static native long zstdGetSeqProdFunction();

  static native long zstdCreateSeqProdState();

  static native void zstdFreeSeqProdState(long sequenceProdState);

  static native int teardown(int qzKey);

  static native void setLogLevel(int logLevel);

  /** Extract bytes_read from a packed result. Caller must check {@code r >= 0} first. */
  static int bytesRead(long r) {
    return (int) (r & 0x7FFFFFFFL);
  }

  /** Extract bytes_written from a packed result. Caller must check {@code r >= 0} first. */
  static int bytesWritten(long r) {
    return (int) ((r >>> 31) & 0x7FFFFFFFL);
  }
}
