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
public class QatZip {
  /**
   * Default compression is set to 6, this is to align with default compression
   * mode chosen as ZLIB
   */
  public static final int DEFAULT_COMPRESS_LEVEL = 6;

  /**
   * Default PINNED memory allocated is set as 480 KB. This accelerates HW based
   * compression/decompression
   */
  private final static long DEFAULT_PIN_MEM_SIZE = 491520L;

  /**
   * If retryCount is set as 0, it means no retries in compress/decompress,
   * non-zero value means there will be retries
   */
  public final static int DEFAULT_RETRY_COUNT = 0;

  private boolean isValid;
  private int retryCount;
  long session;

  /**
   * The mode of operation for QAT (hardware-only or hardware with a software failover).
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
  public QatZip() {
    this(CompressionAlgorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session, using deflate in the given operation mode.
   *
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   */
  public QatZip(Mode mode) {
    this(CompressionAlgorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session using the given compression algorithm.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   */
  public QatZip(CompressionAlgorithm compressionAlgorithm) {
    this(compressionAlgorithm, DEFAULT_COMPRESS_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session using the given compression algorithm and operation mode.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   */
  public QatZip(CompressionAlgorithm compressionAlgorithm, Mode mode) {
    this(compressionAlgorithm, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a new QAT session using the given compression algorithm and level.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   * @param compressionLevel the compression level.
   */
  public QatZip(CompressionAlgorithm compressionAlgorithm, int compressionLevel) {
    this(compressionAlgorithm, compressionLevel, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a QAT session  using the given compression algorithm, level, and mode of operation.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   * @param compressionLevel the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   */
  public QatZip(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode) {
    this(compressionAlgorithm, compressionLevel, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Constructs a QAT session using the given parameters.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   * @param compressionLevel the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   * @param retryCount how many times to seek for a hardware resources before giving up.
   */
  public QatZip(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode, int retryCount) {
    this(compressionAlgorithm, compressionLevel, mode, retryCount, DEFAULT_PIN_MEM_SIZE);
  }

  /**
   * Constructs a QAT session using the given parameters.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   * @param compressionLevel the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software failover.)
   * @param retryCount how many times to seek for a hardware resources before giving up.
   * @param pinnedMemorySize the size of the internal buffer used for compression/decompression (for advanced users).
   * @throws QatException if QAT session cannot be created.
   */
  public QatZip(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode, int retryCount,
      long pinnedMemorySize) throws QatException {
    if (!validateParams(compressionAlgorithm, compressionLevel, retryCount))
      throw new IllegalArgumentException("Invalid compression level or retry count.");

    this.retryCount = retryCount;
    InternalJNI.setup(this, mode.ordinal(), pinnedMemorySize, compressionAlgorithm.ordinal(), compressionLevel);
    isValid = true;
  }

  /**
   * Ends the current QAT session by freeing up resources. A new QAT session must be used after a successful call of
   * this method.
   *
   * @throws QatException if QAT session cannot be gracefully ended.
   */
  public void endSession() throws QatException {
    if (!isValid)
      throw new IllegalStateException();
    InternalJNI.teardown(session);
    isValid = false;
  }

  /**
   * Returns the maximum compression length for the given source length.
   *
   * @param  len the length of the source array or buffer.
   * @return the maximum compression length for the given length.
   */
  public int maxCompressedLength(long len) {
    if (!isValid)
      throw new IllegalStateException();

    return InternalJNI.maxCompressedSize(session, len);
  }

  /**
   * Compresses the source buffer and stores the result in the destination buffer. Returns actual number of bytes of
   * data compressed.
   *
   * On Success, the positions of both the source and destinations buffers are advanced by the number of bytes read from
   * the source and the number of bytes of compressed data written to the destination.
   *
   * @param src the source buffer holding the source data.
   * @param dst the destination array that will store the compressed data.
   * @return returns the size of the compressed data in bytes.
   */
  public int compress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid)
      throw new IllegalStateException();

    if ((src == null || dst == null) || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;
    if (src.isDirect() && dst.isDirect()) {
      compressedSize = InternalJNI.compressDirectByteBuffer(
          session, src, src.position(), src.limit(), dst, dst.position(), dst.limit(), retryCount);
    } else if (src.hasArray() && dst.hasArray()) {
      compressedSize = InternalJNI.compressArrayOrBuffer(
          session, src, src.array(), src.position(), src.limit(), dst.array(), dst.position(), dst.limit(), retryCount);
      dst.position(dst.position() + compressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dst.get(dstArr);

      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);

      compressedSize =
          InternalJNI.compressArrayOrBuffer(session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      dst.put(dstArr, 0, compressedSize);
    }

    if (compressedSize < 0)
      throw new QatException("QAT: Compression failed");

    return compressedSize;
  }

  /**
   * Compresses the source array and stores the result in the destination array. Returns the actual number of bytes of
   * data compressed.
   *
   * @param src the source array holding the source data.
   * @param srcOffset the start offset of the source data.
   * @param srcLen the length of source data to compress.
   * @param dst the destination array for the compressed data.
   * @param dstOffset the destination offset where to start storing the compressed data.
   * @param dstLen the maximum length that can be written to the destination array.
   * @return the size of the compressed data in bytes.
   */
  public int compress(byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid)
      throw new IllegalStateException();

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int compressedSize = InternalJNI.compressArrayOrBuffer(
        session, null, src, srcOffset, srcOffset + srcLen, dst, dstOffset, dstOffset + dstLen, retryCount);

    if (compressedSize < 0)
      throw new QatException("QAT: Compression failed");

    return compressedSize;
  }

  /**
   * Deompresses the source buffer and stores the result in the destination buffer. Returns actual number of bytes of
   * data decompressed.
   *
   * On Success, the positions of both the source and destinations buffers are advanced by the number of bytes of
   * compressed data read from the source and the number of bytes of decompressed data written to the destination.
   *
   * @param src the source buffer holding the compressed data.
   * @param dst the destination array that will store the decompressed data.
   * @return returns the size of the decompressed data in bytes.
   */
  public int decompress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid)
      throw new IllegalStateException();

    if ((src == null || dst == null) || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    if (src.isDirect() && dst.isDirect()) {
      decompressedSize = InternalJNI.decompressDirectByteBuffer(
          session, src, src.position(), src.limit(), dst, dst.position(), dst.limit(), retryCount);
    } else if (src.hasArray() && dst.hasArray()) {
      decompressedSize = InternalJNI.decompressArrayOrBuffer(
          session, src, src.array(), src.position(), src.limit(), dst.array(), dst.position(), dst.limit(), retryCount);
      dst.position(dst.position() + decompressedSize);
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
      decompressedSize =
          InternalJNI.decompressArrayOrBuffer(session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      dst.put(dstArr, 0, decompressedSize);
    }

    if (decompressedSize < 0)
      throw new QatException("QAT: Compression failed");

    return decompressedSize;
  }

  /**
   * Decompresses the source array and stores the result in the destination array. Returns the actual number of bytes of
   * data decompressed.
   *
   * @param src the source array holding the compressed data.
   * @param srcOffset the start offset of the source.
   * @param srcLen the length of source data to decompress.
   * @param dst the destination array for the decompressed data.
   * @param dstOffset the destination offset where to start storing the decompressed data.
   * @param dstLen the maximum length that can be written to the destination array.
   * @return the size of the decompressed data in bytes.
   */
  public int decompress(byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid)
      throw new IllegalStateException();

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Empty source or/and destination byte array(s).");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int decompressedSize = InternalJNI.decompressArrayOrBuffer(
        session, null, src, srcOffset, srcOffset + srcLen, dst, dstOffset, dstOffset + dstLen, retryCount);

    if (decompressedSize < 0)
      throw new QatException("QAT: decompression failed");

    return decompressedSize;
  }

  /**
   * Validates compression level and retry counts.
   *
   * @param compressionAlgorithm the compression algorithm (deflate or LZ4).
   * @param compressionLevel the compression level.
   * @param retryCount how many times to seek for a hardware resources before giving up.
   * @return true if validation was successful, false otherwise.
   */
  private boolean validateParams(CompressionAlgorithm compressionAlgorithm, int compressionLevel, int retryCount) {
    return !(retryCount < 0 || compressionLevel < 0 || (compressionAlgorithm.ordinal() == 0 && compressionLevel > 9));
  }

  /**
   * Cleans up the current QAT session by freeing up resources.
   *
   * @param qzSessionReference the reference to the C-level session object.
   */
  static void cleanUp(long qzSessionReference) {
    InternalJNI.teardown(qzSessionReference);
  }

  /**
   * Gets a cleaner object.
   */
  public Runnable getCleaner() {
    return new QatZipCleaner(session);
  }

  /**
   * A QAT session cleaner that cleans up QAT session.
   */
  static class QatZipCleaner implements Runnable {
    private long qzSession;

    /**
     * Constructs a Cleaner object to clean up QAT session.
     */
    public QatZipCleaner(long qzSession) {
      this.qzSession = qzSession;
    }

    @Override
    public void run() {
      if (qzSession != 0) {
        cleanUp(qzSession);
        qzSession = 0;
      } else {
        System.err.println("A bug in cleaning up session. Please report.");
      }
    }
  }
}
