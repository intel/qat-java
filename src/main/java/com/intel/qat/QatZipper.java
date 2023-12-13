/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * This class provides methods that can be used to compress and decompress data using {@link
 * Algorithm#DEFLATE} or {@link Algorithm#LZ4}.
 *
 * <p>The following code snippet demonstrates how to use the class to compress and decompress a
 * string.
 *
 * <blockquote>
 *
 * <pre>{@code
 * try {
 *   byte[] input = "Hello, world!".getBytes("UTF-8");
 *
 *   QatZipper qzip = new QatZipper();
 *
 *   // Create a buffer with enough size for compression
 *   byte[] output = new byte[qzip.maxCompressedLength(input.length)];
 *
 *   // Compress the bytes
 *   qzip.compress(input, output);
 *
 *   // Decompress the bytes into a String
 *   byte[] result = new byte[input.length];
 *   qzip.decompress(output, result);
 *
 *   // Release resources
 *   qzip.end();
 *
 *   // Convert the bytes into a String
 *   String outputStr = new String(result, "UTF-8");
 * } catch (java.io.UnsupportedEncodingException e) {
 * //
 * } catch (QatException e) {
 * //
 * }
 * }</pre>
 *
 * </blockquote>
 *
 * To release QAT resources used by this <code>QatZipper</code>, the <code>end()</code> method
 * should be called explicitly. If not, resources will stay alive until this <code>QatZipper</code>
 * becomes phantom reachable.
 */
public class QatZipper {
  /** The default compression level is 6. */
  public static final int DEFAULT_COMPRESS_LEVEL = 6;

  /**
   * The default number of times QatZipper attempts to acquire hardware resources is <code>0</code>.
   */
  public static final int DEFAULT_RETRY_COUNT = 0;

  /** The default execution mode. */
  public static final Mode DEFAULT_MODE = Mode.HARDWARE;

  /** The default polling mode. */
  public static final PollingMode DEFAULT_POLLING_MODE = PollingMode.PERIODICAL;

  private boolean isValid;

  private int retryCount;

  /** Number of bytes read from the source by the most recent call to a compress/decompress. */
  private int bytesRead;

  /**
   * Number of bytes written to the destination by the most recent call to a compress/decompress.
   */
  private int bytesWritten;

  /** A reference to a QAT session in C. */
  private long session;

  /** Cleaner instance associated with this object. */
  private static Cleaner cleaner;

  /** Cleaner.Cleanable instance representing QAT cleanup action. */
  private final Cleaner.Cleanable cleanable;

  static {
    InternalJNI.initFieldIDs();

    SecurityManager sm = System.getSecurityManager();
    if (sm == null) {
      cleaner = Cleaner.create();
    } else {
      java.security.PrivilegedAction<Void> pa =
          () -> {
            cleaner = Cleaner.create();
            return null;
          };
      java.security.AccessController.doPrivileged(pa);
    }
  }

  /** The mode of execution for QAT. */
  public static enum Mode {
    /**
     * A hardware-only execution mode. QatZipper would fail if hardware resources cannot be acquired
     * after finite retries.
     */
    HARDWARE,

    /**
     * A hardware execution mode with a software fail over. QatZipper would fail over to software
     * execution mode if hardware resources cannot be acquired after finite retries.
     */
    AUTO;
  }

  /** The polling mode to use. PERIODICAL and BUSY are supported. */
  public static enum PollingMode {
    /** The default polling mode. Preferred mode for throughput sensitive uses cases. */
    PERIODICAL,

    /** BUSY polling. Preferred for latency sensitive use cases. */
    BUSY
  }

  /** The compression algorithm to use. DEFLATE and LZ4 are supported. */
  public static enum Algorithm {
    /** The deflate compression algorithm. */
    DEFLATE,

    /** The LZ4 compression algorithm. */
    LZ4
  }

  /**
   * Creates a new QatZipper that uses {@link Algorithm#DEFLATE}, {@link DEFAULT_COMPRESS_LEVEL},
   * {@link DEFAULT_MODE}, {@link DEFAULT_RETRY_COUNT}, and {@link DEFAULT_POLLING_MODE}.
   */
  public QatZipper() {
    this(
        Algorithm.DEFLATE,
        DEFAULT_COMPRESS_LEVEL,
        DEFAULT_MODE,
        DEFAULT_RETRY_COUNT,
        DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified execution {@link Mode}. Uses {@link
   * Algorithm#DEFLATE}, {@link DEFAULT_COMPRESS_LEVEL}, {@link DEFAULT_RETRY_COUNT}, and {@link
   * DEFAULT_POLLING_MODE}.
   *
   * @param mode the {@link Mode} of QAT execution
   */
  public QatZipper(Mode mode) {
    this(
        Algorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT, DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified polling {@link PollingMode}. Uses {@link
   * Algorithm#DEFLATE}, {@link DEFAULT_COMPRESS_LEVEL}, and {@link DEFAULT_RETRY_COUNT}.
   *
   * @param pmode the {@link PollingMode}
   */
  public QatZipper(PollingMode pmode) {
    this(Algorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, DEFAULT_MODE, DEFAULT_RETRY_COUNT, pmode);
  }

  /**
   * Creates a new QatZipper with the specified compression {@link Algorithm}. Uses {@link
   * DEFAULT_COMPRESS_LEVEL}, {@link DEFAULT_MODE}, {@link DEFAULT_RETRY_COUNT}, and {@link
   * DEFAULT_POLLING_MODE}.
   *
   * @param algorithm the compression {@link Algorithm}
   */
  public QatZipper(Algorithm algorithm) {
    this(
        algorithm, DEFAULT_COMPRESS_LEVEL, DEFAULT_MODE, DEFAULT_RETRY_COUNT, DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm} and {@link Mode} of execution.
   * Uses {@link DEFAULT_COMPRESS_LEVEL}, {@link DEFAULT_RETRY_COUNT}, and {@link
   * DEFAULT_POLLING_MODE}.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param mode the {@link Mode} of QAT execution
   */
  public QatZipper(Algorithm algorithm, Mode mode) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT, DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm} and {@link PollingMode} of
   * execution. Uses {@link DEFAULT_COMPRESS_LEVEL}, {@link DEFAULT_MODE}, and {@link
   * DEFAULT_RETRY_COUNT}
   *
   * @param algorithm the compression {@link Algorithm}
   * @param pmode the {@link PollingMode}
   */
  public QatZipper(Algorithm algorithm, PollingMode pmode) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, DEFAULT_MODE, DEFAULT_RETRY_COUNT, pmode);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm} and {@link Mode} of execution.
   * Uses {@link DEFAULT_COMPRESS_LEVEL} and {@link DEFAULT_RETRY_COUNT}.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param mode the {@link Mode} of QAT execution
   * @param pmode the {@link PollingMode}
   */
  public QatZipper(Algorithm algorithm, Mode mode, PollingMode pmode) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT, pmode);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm} and compression level. Uses {@link
   * DEFAULT_MODE}, {@link DEFAULT_RETRY_COUNT}, and {@link DEFAULT_POLLING_MODE}.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   */
  public QatZipper(Algorithm algorithm, int level) {
    this(algorithm, level, DEFAULT_MODE, DEFAULT_RETRY_COUNT, DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm}, compression level, and {@link
   * Mode}. Uses {@link DEFAULT_RETRY_COUNT} and {@link DEFAULT_POLLING_MODE}.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatZipper(Algorithm algorithm, int level, Mode mode) {
    this(algorithm, level, mode, DEFAULT_RETRY_COUNT, DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm}, compression level, and {@link
   * PollingMode}. Uses {@link DEFAULT_MODE} and {@link DEFAULT_RETRY_COUNT}.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param pmode the {@link PollingMode}
   */
  public QatZipper(Algorithm algorithm, int level, PollingMode pmode) {
    this(algorithm, level, DEFAULT_MODE, DEFAULT_RETRY_COUNT, pmode);
  }

  /**
   * Creates a new QatZipper with the specified parameters and {@link DEFAULT_POLLING_MODE}.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param level the compression level.
   * @param mode the {@link Mode} of QAT execution
   * @param retryCount the number of attempts to acquire hardware resources
   * @throws QatException if QAT session cannot be created.
   */
  public QatZipper(Algorithm algorithm, int level, Mode mode, int retryCount) {
    this(algorithm, level, mode, retryCount, DEFAULT_POLLING_MODE);
  }

  /**
   * Creates a new QatZipper with the specified parameters and {@link DEFAULT_RETRY_COUNT}.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param level the compression level.
   * @param mode the {@link Mode} of QAT execution
   * @param pmode the {@link PollingMode}
   * @throws QatException if QAT session cannot be created.
   */
  public QatZipper(Algorithm algorithm, int level, Mode mode, PollingMode pmode) {
    this(algorithm, level, mode, DEFAULT_RETRY_COUNT, pmode);
  }

  /**
   * Creates a new QatZipper with the specified parameters.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param level the compression level.
   * @param mode the {@link Mode} of QAT execution
   * @param retryCount the number of attempts to acquire hardware resources
   * @param pmode {@link PollingMode}
   * @throws QatException if QAT session cannot be created.
   */
  public QatZipper(Algorithm algorithm, int level, Mode mode, int retryCount, PollingMode pmode)
      throws QatException {
    if (retryCount < 0) throw new IllegalArgumentException("Invalid value for retry count.");

    this.retryCount = retryCount;
    InternalJNI.setup(this, algorithm.ordinal(), level, mode.ordinal(), pmode.ordinal());

    // Register a QAT session cleaner for this object
    cleanable = cleaner.register(this, new QatCleaner(session));
    isValid = true;
  }

  /**
   * Returns the maximum compression length for the specified source length. Use this method to
   * estimate the size of a buffer for compression given the size of a source buffer.
   *
   * @param len the length of the source array or buffer.
   * @return the maximum compression length for the specified length.
   */
  public int maxCompressedLength(long len) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    return InternalJNI.maxCompressedSize(session, len);
  }

  /**
   * Compresses the source array and stores the result in the destination array. Returns the actual
   * number of bytes of the compressed data.
   *
   * @param src the source array holding the source data
   * @param dst the destination array for the compressed data
   * @return the size of the compressed data in bytes
   */
  public int compress(byte[] src, byte[] dst) {
    return compress(src, 0, src.length, dst, 0, dst.length);
  }

  /**
   * Compresses the source array, starting at the specified offset, and stores the result in the
   * destination array starting at the specified destination offset. Returns the actual number of
   * bytes of data compressed.
   *
   * @param src the source array holding the source data
   * @param srcOffset the start offset of the source data
   * @param srcLen the length of source data to compress
   * @param dst the destination array for the compressed data
   * @param dstOffset the destination offset where to start storing the compressed data
   * @param dstLen the maximum length that can be written to the destination array
   * @return the size of the compressed data in bytes
   */
  public int compress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException(
          "Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    bytesRead = bytesWritten = 0;

    int compressedSize =
        InternalJNI.compressByteArray(
            this, session, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);

    // bytesRead is updated by compressByteArray. We only need to update bytesWritten.
    bytesWritten = compressedSize;

    return compressedSize;
  }

  /**
   * Compresses the source buffer and stores the result in the destination buffer. Returns actual
   * number of bytes of compressed data.
   *
   * <p>On Success, the positions of both the source and destinations buffers are advanced by the
   * number of bytes read from the source and the number of bytes of compressed data written to the
   * destination.
   *
   * @param src the source buffer holding the source data
   * @param dst the destination array that will store the compressed data
   * @return the size of the compressed data in bytes
   */
  public int compress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if ((src == null || dst == null)
        || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly()) throw new ReadOnlyBufferException();

    final int srcPos = src.position();
    final int dstPos = dst.position();

    bytesRead = bytesWritten = 0;

    int compressedSize = 0;
    if (src.hasArray() && dst.hasArray()) {
      compressedSize =
          InternalJNI.compressByteBuffer(
              session,
              src,
              src.array(),
              srcPos,
              src.remaining(),
              dst.array(),
              dstPos,
              dst.remaining(),
              retryCount);
      dst.position(dstPos + compressedSize);
    } else if (src.isDirect() && dst.isDirect()) {
      compressedSize =
          InternalJNI.compressDirectByteBuffer(
              session, src, srcPos, src.remaining(), dst, dstPos, dst.remaining(), retryCount);
    } else if (src.hasArray() && dst.isDirect()) {
      compressedSize =
          InternalJNI.compressDirectByteBufferDst(
              session,
              src,
              src.array(),
              srcPos,
              src.remaining(),
              dst,
              dstPos,
              dst.remaining(),
              retryCount);
    } else if (src.isDirect() && dst.hasArray()) {
      compressedSize =
          InternalJNI.compressDirectByteBufferSrc(
              session,
              src,
              srcPos,
              src.remaining(),
              dst.array(),
              dstPos,
              dst.remaining(),
              retryCount);
      dst.position(dstPos + compressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dst.get(dstArr);

      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);

      int pos = src.position();
      compressedSize =
          InternalJNI.compressByteBuffer(
              session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      src.position(pos + src.position());
      dst.put(dstArr, 0, compressedSize);
    }

    bytesRead = src.position() - srcPos;
    bytesWritten = dst.position() - dstPos;

    return compressedSize;
  }

  /**
   * Decompresses the source array and stores the result in the destination array. Returns the
   * actual number of bytes of decompressed data.
   *
   * @param src the source array holding the compressed data
   * @param dst the destination array for the decompressed data
   * @return the size of the decompressed data in bytes
   */
  public int decompress(byte[] src, byte[] dst) {
    return decompress(src, 0, src.length, dst, 0, dst.length);
  }

  /**
   * Decompresses the source array, starting at the specified offset, and stores the result in the
   * destination array starting at the specified destination offset. Returns the actual number of
   * bytes of data decompressed.
   *
   * @param src the source array holding the compressed data
   * @param srcOffset the start offset of the source
   * @param srcLen the length of source data to decompress
   * @param dst the destination array for the decompressed data
   * @param dstOffset the destination offset where to start storing the decompressed data
   * @param dstLen the maximum length that can be written to the destination array
   * @return the size of the decompressed data in bytes
   */
  public int decompress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Empty source or/and destination byte array(s).");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    bytesRead = bytesWritten = 0;

    int decompressedSize =
        InternalJNI.decompressByteArray(
            this, session, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);

    // bytesRead is updated by decompressedByteArray. We only need to update bytesWritten.
    bytesWritten = decompressedSize;

    return decompressedSize;
  }

  /**
   * Deompresses the source buffer and stores the result in the destination buffer. Returns actual
   * number of bytes of decompressed data.
   *
   * <p>On Success, the positions of both the source and destinations buffers are advanced by the
   * number of bytes of compressed data read from the source and the number of bytes of decompressed
   * data written to the destination.
   *
   * @param src the source buffer holding the compressed data
   * @param dst the destination array that will store the decompressed data
   * @return the size of the decompressed data in bytes
   */
  public int decompress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if ((src == null || dst == null)
        || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly()) throw new ReadOnlyBufferException();

    final int srcPos = src.position();
    final int dstPos = dst.position();

    bytesRead = bytesWritten = 0;

    int decompressedSize = 0;
    if (src.hasArray() && dst.hasArray()) {
      decompressedSize =
          InternalJNI.decompressByteBuffer(
              session,
              src,
              src.array(),
              srcPos,
              src.remaining(),
              dst.array(),
              dstPos,
              dst.remaining(),
              retryCount);
      dst.position(dstPos + decompressedSize);
    } else if (src.isDirect() && dst.isDirect()) {
      decompressedSize =
          InternalJNI.decompressDirectByteBuffer(
              session, src, srcPos, src.remaining(), dst, dstPos, dst.remaining(), retryCount);
    } else if (src.hasArray() && dst.isDirect()) {
      decompressedSize =
          InternalJNI.decompressDirectByteBufferDst(
              session,
              src,
              src.array(),
              srcPos,
              src.remaining(),
              dst,
              dstPos,
              dst.remaining(),
              retryCount);
    } else if (src.isDirect() && dst.hasArray()) {
      decompressedSize =
          InternalJNI.decompressDirectByteBufferSrc(
              session,
              src,
              srcPos,
              src.remaining(),
              dst.array(),
              dstPos,
              dst.remaining(),
              retryCount);
      dst.position(dstPos + decompressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dst.get(dstArr);

      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);

      int pos = src.position();
      decompressedSize =
          InternalJNI.decompressByteBuffer(
              session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      src.position(pos + src.position());
      dst.put(dstArr, 0, decompressedSize);
    }

    if (decompressedSize < 0) throw new QatException("QAT: Compression failed");

    bytesRead = src.position() - srcPos;
    bytesWritten = dst.position() - dstPos;

    return decompressedSize;
  }

  /**
   * Returns the number of bytes read from the source array or buffer by the most recent call to
   * compress/decompress.
   *
   * @return the number of bytes read from source.
   */
  public int getBytesRead() {
    return bytesRead;
  }

  /**
   * Returns the number of bytes written to the destination array or buffer by the most recent call
   * to compress/decompress.
   *
   * @return the number of bytes written to destination.
   */
  public int getBytesWritten() {
    return bytesWritten;
  }

  /**
   * Ends the current QAT session by freeing up resources. A new session must be used after a
   * successful call of this method.
   *
   * @throws QatException if QAT session cannot be gracefully ended.
   */
  public void end() throws QatException {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");
    InternalJNI.teardown(session);
    isValid = false;
  }

  /** A class that represents a cleaner action for a QAT session. */
  static class QatCleaner implements Runnable {
    private long qzSession;

    /** Creates a new cleaner object that cleans up the specified session. */
    public QatCleaner(long session) {
      this.qzSession = session;
    }

    @Override
    public void run() {
      if (qzSession != 0) {
        InternalJNI.teardown(qzSession);
      }
    }
  }
}
