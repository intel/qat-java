/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * Defines APIs for creation of setting up hardware based QAT session(with or
 * without software backup, compression and decompression APIs
 */
public class QatSession {

  /**
   * Default compression is set to 6, this is to align with default compression
   * mode chosen as ZLIB
   */
  public static final int DEFAULT_DEFLATE_COMP_LEVEL = 6;

  /**
   * Default PINNED memory allocated is set as 480 KB. This accelerates HW based
   * compression/decompression
   */
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;

  /**
   * If retryCount is set as 0, it means no retries in compress/decompress,
   * non-zero value means there will be retries
   */
  public final static int DEFAULT_RETRY_COUNT = 0;

  private boolean isValid;
  private int retryCount;
  long session;

  /**
   * The mode of operation for QAT -- hardware-only or hardware with a software failover.
   */
  public static enum Mode {
    /**
     * QAT session uses QAT hardware, fails if hardware resources cannot be acquired after retries.
     */
    HARDWARE,

    /**
     * QAT session uses QAT hardware, fails over to software if hardware resources cannot be acquired after retries.
     */
    AUTO;
  }

  /**
   * The compression algorithm -- either DEFLATE or LZ4.
   */
  public static enum CompressionAlgorithm {
    /**
     * ZLIB compression
     */
    DEFLATE,

    /**
     * LZ4 compression
     */
    LZ4
  }

  /**
   * Constructs a new QAT session object using deflate.
   */
  public QatSession() {
    this(CompressionAlgorithm.DEFLATE, DEFAULT_DEFLATE_COMP_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session, using deflate in the given operation mode.
   *
   * @param mode - the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   */
  public QatSession(Mode mode) {
    this(CompressionAlgorithm.DEFLATE, DEFAULT_DEFLATE_COMP_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session using the given compression algorithm.
   *
   * @param compressionAlgorithm - the compression algorithm (deflate or LZ4).
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm) {
    this(compressionAlgorithm, DEFAULT_DEFLATE_COMP_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session using the given compression algorithm and operation mode.
   *
   * @param compressionAlgorithm - the compression algorithm (deflate or LZ4).
   * @param mode - the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, Mode mode) {
    this(compressionAlgorithm, DEFAULT_DEFLATE_COMP_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session using the given compression algorithm and level.
   *
   * @param compressionAlgorithm - the compression algorithm (deflate or LZ4).
   * @param compressionLevel - the compression level.
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel) {
    this(compressionAlgorithm, compressionLevel, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a QAT session  using the given compression algorithm, level, and mode of operation.
   *
   * @param compressionAlgorithm - the compression algorithm (deflate or LZ4).
   * @param compressionLevel - the compression level.
   * @param mode - the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode) {
    this(compressionAlgorithm, compressionLevel, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a QAT session using the given parameters.
   *
   * @param compressionAlgorithm - the compression algorithm (deflate or LZ4).
   * @param compressionLevel - the compression level.
   * @param mode - the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   * @param retryCount - how many times to seek for a hardware resources before giving up.
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode, int retryCount) {
    this(compressionAlgorithm, compressionLevel, mode, retryCount, DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES);
  }

  /**
   * Constructs a QAT session using the given parameters. 
   *
   * @param compressionAlgorithm - the compression algorithm (deflate or LZ4).
   * @param compressionLevel - the compression level.
   * @param mode - the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   * @param retryCount - how many times to seek for a hardware resources before giving up.
   * @param pinnedMemorySize - the size of the internal buffer used for compression/decompression (for advanced users).
   * @throws QatException - if QAT session cannot be created.
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode, int retryCount,
      long pinnedMemorySize) throws QatException {
    if (!validateParams(compressionAlgorithm, compressionLevel, retryCount))
      throw new IllegalArgumentException("Invalid compression level or retry count.");

    this.retryCount = retryCount;
    InternalJNI.setup(this, mode.ordinal(), pinnedMemorySize, compressionAlgorithm.ordinal(), compressionLevel);
    isValid = true;
  }

  /**
   *
   * @throws QatException
   */
  public void endSession() throws QatException {
    if (!isValid)
      throw new IllegalStateException();
    InternalJNI.teardown(session);
    isValid = false;
  }

  /**
   *
   * @param srcLen
   * @return
   */
  public int maxCompressedLength(long srcLen) {
    if (!isValid)
      throw new IllegalStateException();

    return InternalJNI.maxCompressedSize(session, srcLen);
  }

  /**
   *
   * @param src
   * @param dst
   * @return
   */
  public int compress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid)
      throw new IllegalStateException();

    if ((src == null || dst == null) || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly())
      throw new ReadOnlyBufferException();

    int comSize = 0;
    if (src.isDirect() && dst.isDirect()) {
      comSize = InternalJNI.compressDirectByteBuffer(
          session, src, src.position(), src.limit(), dst, dst.position(), dst.limit(), retryCount);
    } else if (src.hasArray() && dst.hasArray()) {
      comSize = InternalJNI.compressArrayOrBuffer(
          session, src, src.array(), src.position(), src.limit(), dst.array(), dst.position(), dst.limit(), retryCount);
      dst.position(dst.position() + comSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dst.get(dstArr);

      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);

      comSize = InternalJNI.compressArrayOrBuffer(session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      dst.put(dstArr, 0, comSize);
    }

    if (comSize < 0) {
      throw new QatException("QAT: Compression failed");
    }
    return comSize;
  }

  /**
   *
   * @param src
   * @param srcOffset
   * @param srcLen
   * @param dst
   * @param dstOffset
   * @param dstLen
   * @return
   */
  public int compress(byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid)
      throw new IllegalStateException();

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int comSize = InternalJNI.compressArrayOrBuffer(
        session, null, src, srcOffset, srcOffset + srcLen, dst, dstOffset, dstOffset + dstLen, retryCount);

    if (comSize < 0) {
      throw new QatException("QAT: Compression failed");
    }
    return comSize;
  }

  /**
   *
   * @param src
   * @param dst
   * @return
   */
  public int decompress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid)
      throw new IllegalStateException();

    if ((src == null || dst == null) || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly())
      throw new ReadOnlyBufferException();

    int decSize = 0;
    if (src.isDirect() && dst.isDirect()) {
      decSize = InternalJNI.decompressDirectByteBuffer(
          session, src, src.position(), src.limit(), dst, dst.position(), dst.limit(), retryCount);
    } else if (src.hasArray() && dst.hasArray()) {
      decSize = InternalJNI.decompressArrayOrBuffer(
          session, src, src.array(), src.position(), src.limit(), dst.array(), dst.position(), dst.limit(), retryCount);
      dst.position(dst.position() + decSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      // read into arrays
      src.get(srcArr);
      dst.get(dstArr);

      // reset src and dst positions
      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);
      decSize = InternalJNI.decompressArrayOrBuffer(session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      dst.put(dstArr, 0, decSize);
    }

    if (decSize < 0) {
      throw new QatException("QAT: Compression failed");
    }
    return decSize;
  }

  /**
   *
   * @param src
   * @param srcOffset
   * @param srcLen
   * @param dst
   * @param dstOffset
   * @param dstLen
   * @return
   */
  public int decompress(byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid)
      throw new IllegalStateException();

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int decSize = 0;

    decSize = InternalJNI.decompressArrayOrBuffer(
        session, null, src, srcOffset, srcOffset + srcLen, dst, dstOffset, dstOffset + dstLen, retryCount);

    if (decSize < 0) {
      throw new QatException("QAT: decompression failed");
    }

    return decSize;
  }

  /**
   *
   * @param compressionAlgorithm
   * @param compressionLevel
   * @param retryCount
   * @return
   */
  private boolean validateParams(CompressionAlgorithm compressionAlgorithm, int compressionLevel, int retryCount) {
    return !(retryCount < 0 || compressionLevel < 0 || (compressionAlgorithm.ordinal() == 0 && compressionLevel > 9));
  }

  /**
   *
   * @param qzSessionReference
   */
  static void cleanUp(long qzSessionReference) {
    InternalJNI.teardown(qzSessionReference);
  }

  /**
   *
   * @return
   */
  public Runnable cleanUp() {
    return new QatSessionCleaner(session);
  }

  /**
   *
   */
  static class QatSessionCleaner implements Runnable {
    private long qzSession;

    public QatSessionCleaner(long qzSession) {
      this.qzSession = qzSession;
    }

    @Override
    public void run() {
      if (qzSession != 0) {
        cleanUp(qzSession);
        qzSession = 0;
      } else {
        System.err.println("BUG: This line should never be printed!");
      }
    }
  }
}
