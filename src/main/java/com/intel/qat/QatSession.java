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
   * code paths for library. HARDWARE ONLY(HARDWARE) and HARDWARE with SOFTWARE
   * fallback(AUTO) are supported
   */
  public static enum Mode {
    /**
     * Hardware ONLY Path, QAT session fails if this is chosen and HW is not
     * available
     */
    HARDWARE,

    /**
     * Hardware based QAT session optionally switches to software in case of HW
     * failure
     */
    AUTO;
  }

  /**
   * Compression Algorithm for library.
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
   * default constructor to assign default code path as AUTO(with software
   * fallback option), no retries, ZLIB as default compression algo and
   * compression level 6 which is ZLIB default compression level
   */
  public QatSession() {
    this(CompressionAlgorithm.DEFLATE, DEFAULT_DEFLATE_COMP_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Sets defined compression algroithm, others are set to their respective
   * default value
   * @param compressionAlgorithm CompressionAlgorithm enum value
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm) {
    this(compressionAlgorithm, DEFAULT_DEFLATE_COMP_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Sets defined Compression algorithm along with compression level, others are
   * set to their default value
   * @param compressionAlgorithm CompressionAlgorithm enum value
   * @param compressionLevel Compression Level
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel) {
    this(compressionAlgorithm, compressionLevel, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Sets defined Compression algorithm along with compression level and chosen
   * code path mode from Mode enum, others are set to their default value
   * @param compressionAlgorithm CompressionAlgorithm enum value
   * @param compressionLevel Compression Level
   * @param mode Mode enum value
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode) {
    this(compressionAlgorithm, compressionLevel, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * sets the parameter supplied by user or through other constructor with
   * varying params, Pinned mem is set to predefined size
   * @param compressionAlgorithm compression algorithm like LZ4, ZLIB,etc which
   *     are supported
   * @param compressionLevel compression level as per compression algorithm
   *     chosen
   * @param mode HARDWARE and auto
   * @param retryCount how many times a Hardware based compress/decompress call
   *     should be tried before failing compression/decompression
   */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode, int retryCount) {
    this(compressionAlgorithm, compressionLevel, mode, retryCount, DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES);
  }

  /**
   * sets the parameter supplied by user or through other constructor with
   * varying params
   * @param compressionAlgorithm compression algorithm like LZ4, ZLIB,etc which
   *     are supported
   * @param compressionLevel compression level as per compression algorithm
   *     chosen
   * @param mode HARDWARE and auto
   * @param retryCount how many times a Hardware based compress/decompress call
   *     should be tried before failing compression/decompression
   * @param pinnedMemorySize To be used by Advanced developer to define the size
   *     of pinned memory
   * */
  public QatSession(CompressionAlgorithm compressionAlgorithm, int compressionLevel, Mode mode, int retryCount,
      long pinnedMemorySize) throws QatException {
    if (!validateParams(compressionAlgorithm, compressionLevel, retryCount))
      throw new IllegalArgumentException("Invalid parameters");

    this.retryCount = retryCount;
    InternalJNI.setup(this, mode.ordinal(), pinnedMemorySize, compressionAlgorithm.ordinal(), compressionLevel);
    isValid = true;
  }

  /**
   * endSession API destroys the QAT hardware session and free up resources and
   * PINNED memory allocated with setup API call
   */
  public void endSession() throws QatException {
    if (!isValid)
      throw new IllegalStateException();
    InternalJNI.teardown(session);
    isValid = false;
  }

  /**
   * Provides maximum compression length (probable, not exact as this is decided
   * after successful compression) for a given source length
   * @param srcLen source length
   * @return maximum compressed length
   */
  public int maxCompressedLength(long srcLen) {
    if (!isValid)
      throw new IllegalStateException();

    return InternalJNI.maxCompressedSize(session, srcLen);
  }

  /**
   * compresses source bytebuffer into destination bytebuffer
   * @param src source bytebuffer. Offsets is defined as source bytebuffer
   *     position and limit to be used for total bytes that needs to be
   *     compressed
   * @param dest destination bytebuffer. starting position to store compressed
   *     data is defined as destination bytebuffer position and limit to be used
   *     as total size of destination buffer which is enough to store compressed
   *     data
   * @return non-zero compressed size or throw QatException
   */
  public int compress(ByteBuffer src, ByteBuffer dest) {
    if (!isValid)
      throw new IllegalStateException();

    if ((src == null || dest == null) || (src.position() == src.limit() || dest.position() == dest.limit()))
      throw new IllegalArgumentException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;

    if (src.isDirect() && dest.isDirect()) {
      compressedSize = InternalJNI.compressDirectByteBuffer(
          session, src, src.position(), src.limit(), dest, dest.position(), dest.limit(), retryCount);
    } else if (src.hasArray() && dest.hasArray()) {
      compressedSize = InternalJNI.compressArrayOrBuffer(session, src, src.array(), src.position(), src.limit(),
          dest.array(), dest.position(), dest.limit(), retryCount);
      dest.position(dest.position() + compressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dest.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dest.get(dstArr);

      src.position(src.position() - srcLen);
      dest.position(dest.position() - dstLen);

      compressedSize =
          InternalJNI.compressArrayOrBuffer(session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      dest.put(dstArr, 0, compressedSize);
    }

    if (compressedSize < 0) {
      throw new QatException("QAT: Compression failed");
    }
    return compressedSize;
  }

  /**
   * compresses source byte array into destination bytearray
   * @param src source bytearray
   * @param srcOffset Offsets is defined as source bytearray position
   * @param srcLen to be used for total bytes that needs to be compressed
   * @param dest destination bytearray
   * @param destOffset  starting position to store compressed data
   * @param destLen  limit to be used as total size of destination buffer which
   *     is enough to store compressed data
   * @return non-zero compressed size or throws QatException
   */
  public int compress(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int destLen) {
    if (!isValid)
      throw new IllegalStateException();

    if (src == null || dest == null || srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Invalid byte array index");

    int compressedSize = InternalJNI.compressArrayOrBuffer(
        session, null, src, srcOffset, srcOffset + srcLen, dest, destOffset, destOffset + destLen, retryCount);

    if (compressedSize < 0) {
      throw new QatException("QAT: Compression failed");
    }
    return compressedSize;
  }

  /**
   * decompresses source bytebuffer into destination bytebuffer
   * @param src source bytebuffer. Offsets is defined as source bytebuffer
   *     position and limit to be used for total bytes that needs to be
   *     decompressed
   * @param dest destination bytebuffer. starting position to store decompressed
   *     data is defined as destination bytebuffer position and limit to be used
   *     as total size of destination buffer which is enough to store
   *     decompressed data
   * @return non-zero decompressed size or throw QatException
   */
  public int decompress(ByteBuffer src, ByteBuffer dest) {
    if (!isValid)
      throw new IllegalStateException();

    if ((src == null || dest == null) || (src.position() == src.limit() || dest.position() == dest.limit()))
      throw new IllegalArgumentException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;

    // NEW LOGIC
    if (src.isDirect() && dest.isDirect()) {
      decompressedSize = InternalJNI.decompressDirectByteBuffer(
          session, src, src.position(), src.limit(), dest, dest.position(), dest.limit(), retryCount);
    } else if (src.hasArray() && dest.hasArray()) {
      decompressedSize = InternalJNI.decompressArrayOrBuffer(session, src, src.array(), src.position(), src.limit(),
          dest.array(), dest.position(), dest.limit(), retryCount);
      dest.position(dest.position() + decompressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dest.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      // read into arrays
      src.get(srcArr);
      dest.get(dstArr);

      // reset src and dst positions
      src.position(src.position() - srcLen);
      dest.position(dest.position() - dstLen);
      decompressedSize =
          InternalJNI.decompressArrayOrBuffer(session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      dest.put(dstArr, 0, decompressedSize);
    }

    if (decompressedSize < 0) {
      throw new QatException("QAT: Compression failed");
    }
    return decompressedSize;
  }

  /**
   * decompresses source byte array into destination bytearray
   * @param src source bytearray
   * @param srcOffset Offsets is defined as source bytearray position
   * @param srcLen to be used for total bytes that needs to be decompressed
   * @param dest destination bytearray
   * @param destOffset  starting position to store decompressed data
   * @param destLen  limit to be used as total size of destination buffer which
   *     is enough to store decompressed data
   * @return non-zero decompressed size or throws QatException
   */
  public int decompress(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int destLen) {
    if (!isValid)
      throw new IllegalStateException();

    if (src == null || dest == null || srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Invalid byte array index");

    int decompressedSize = 0;

    decompressedSize = InternalJNI.decompressArrayOrBuffer(
        session, null, src, srcOffset, srcOffset + srcLen, dest, destOffset, destOffset + destLen, retryCount);

    if (decompressedSize < 0) {
      throw new QatException("QAT: decompression failed");
    }

    return decompressedSize;
  }

  /**
   * validate compressionAlgorithm, compression level and retry count with their
   * possible values
   * @param compressionAlgorithm
   * @param compressionLevel
   * @param retryCount
   * @return
   */
  private boolean validateParams(CompressionAlgorithm compressionAlgorithm, int compressionLevel, int retryCount) {
    return !(retryCount < 0 || compressionLevel < 0 || (compressionAlgorithm.ordinal() == 0 && compressionLevel > 9));
  }

  /**
   * static method which will be called by java ref cleaner when this current
   * context object becomes phantom reachable
   * @param qzSessionReference
   */
  static void cleanUp(long qzSessionReference) {
    InternalJNI.teardown(qzSessionReference);
  }

  /**
   * This method is called by GC when doing cleaning action
   * @return Runnable to be used in cleaner register
   */

  public Runnable cleanUp() {
    return new QatSessionCleaner(session);
  }

  /**
   * internal static class to provide cleaning action to the calling application
   * while registering cleaning action callback
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
        System.out.println("DEBUGGING : Cleaner called more than once");
      }
    }
  }
}
