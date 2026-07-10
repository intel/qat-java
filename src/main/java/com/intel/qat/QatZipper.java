/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Objects;

/**
 * QatZipper provides high-performance compression and decompression using Intel QAT hardware
 * acceleration or software fallback. Supports DEFLATE, LZ4, and ZSTD algorithms.
 *
 * <h2>Thread Safety</h2>
 *
 * This class is NOT thread-safe. Each thread must use its own QatZipper instance. The bytesRead and
 * bytesWritten fields are updated during compress/decompress operations and reflect the state of
 * the most recent operation only.
 *
 * <h2>Resource Management</h2>
 *
 * Resources are released by explicitly calling the <code>end()</code> method.
 *
 * <blockquote>
 *
 * <pre>{@code
 * try {
 *   byte[] input = "Hello, world!".getBytes("UTF-8");
 *
 *   QatZipper qzip = new QatZipper.Builder().build();
 *
 *   // Create a buffer with enough size for compression
 *   byte[] output = new byte[qzip.maxCompressedLength(input.length)];
 *
 *   // Compress the bytes
 *   int clen = qzip.compress(input, output);
 *
 *   // Decompress the bytes into a String
 *   byte[] result = new byte[input.length];
 *   qzip.decompress(output, 0, clen, result, 0, result.length);
 *
 *   // Release resources
 *   qzip.end();
 *
 *   // Convert the bytes into a String
 *   String outputStr = new String(result, "UTF-8");
 * } catch (java.io.UnsupportedEncodingException e) {
 * //
 * } catch (RuntimeException e) {
 * //
 * }
 * }</pre>
 *
 * </blockquote>
 *
 * <h2>Buffer Position Behavior</h2>
 *
 * For ByteBuffer operations:
 *
 * <ul>
 *   <li>Buffer positions mark the start of data to process
 *   <li>Buffer limits mark the end of available data
 *   <li>After successful operations, positions are advanced
 *   <li>On error, positions are restored to their initial values
 * </ul>
 *
 * @see Algorithm
 * @see Mode
 * @see PollingMode
 * @see DataFormat
 */
public class QatZipper {
  /** The default compression algorithm. */
  public static final Algorithm DEFAULT_ALGORITHM = Algorithm.DEFLATE;

  /** The default compression level for DEFLATE is 6. */
  public static final int DEFAULT_COMPRESSION_LEVEL_DEFLATE = 6;

  /** The default compression level for ZSTD is 3. */
  public static final int DEFAULT_COMPRESSION_LEVEL_ZSTD = 3;

  /**
   * The maximum compression level supported for ZSTD is 12. QAT-accelerated ZSTD is based on the <a
   * href="https://github.com/intel/QAT-ZSTD-Plugin">QAT-ZSTD-Plugin</a>, which supports only levels
   * 1 to 12.
   */
  public static final int MAX_COMPRESSION_LEVEL_ZSTD = 12;

  /** The default execution mode. */
  public static final Mode DEFAULT_MODE = Mode.AUTO;

  /** The default polling mode. */
  public static final PollingMode DEFAULT_POLLING_MODE = PollingMode.BUSY;

  /** The default data format. */
  public static final DataFormat DEFAULT_DATA_FORMAT = DataFormat.DEFLATE_GZIP_EXT;

  /** The default hardware buffer size. */
  public static final HardwareBufferSize DEFAULT_HW_BUFFER_SIZE =
      HardwareBufferSize.DEFAULT_BUFFER_SIZE;

  /** The default log level. */
  public static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.NONE;

  /**
   * Thread-local staging buffer for mixed-mode ByteBuffer paths. Avoids allocating a new direct
   * ByteBuffer on every compress/decompress call when staging is required. The buffer grows as
   * needed and is never shrunk.
   */
  private static final ThreadLocal<ByteBuffer> STAGING_BUFFER =
      ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(64 * 1024));

  /** Returns a thread-local direct buffer of at least {@code minCapacity} bytes, cleared. */
  private static ByteBuffer getStagingBuffer(int minCapacity) {
    ByteBuffer buf = STAGING_BUFFER.get();
    if (buf.capacity() < minCapacity) {
      buf = ByteBuffer.allocateDirect(minCapacity);
      STAGING_BUFFER.set(buf);
    }
    buf.clear();
    return buf;
  }

  // Configuration Fields
  private final Algorithm algorithm;
  private final int level;
  private final Mode mode;
  private final PollingMode pollingMode;
  private final DataFormat dataFormat;
  private final HardwareBufferSize hwBufferSize;
  private final LogLevel logLevel;

  /** Indicates if this QatZipper session is valid. */
  private boolean isValid;

  /** Number of bytes read from the source by the most recent compress/decompress call. */
  private int bytesRead;

  /** Number of bytes written to the destination by the most recent compress/decompress call. */
  private int bytesWritten;

  /** QAT session key set by JNI code. */
  private int qzKey;

  /** ZSTD compression context (null if algorithm is not ZSTD). */
  private final ZstdCompressCtx zstdCompressCtx;

  /** ZSTD decompression context (null if algorithm is not ZSTD). */
  private final ZstdDecompressCtx zstdDecompressCtx;

  /** Checksum flag for ZSTD (if enabled). */
  private boolean checksumFlag;

  /** Supported compression algorithms. */
  public static enum Algorithm {
    /** The DEFLATE compression algorithm. */
    DEFLATE,

    /** The LZ4 compression algorithm. */
    LZ4,

    /** The Zstandard compression algorithm. */
    ZSTD
  }

  /** Execution modes for QAT operations. */
  public static enum Mode {
    /** Hardware-only mode. Fails if hardware resources unavailable. */
    HARDWARE,

    /** Hybrid mode. Falls back to software if hardware unavailable. */
    AUTO
  }

  /**
   * Polling modes affect how QAT processes requests. BUSY polling is lower latency but uses more
   * CPU. PERIODICAL polling is better for high CPU utilization scenarios.
   */
  public static enum PollingMode {
    /** Low-latency polling. Use for latency-sensitive workloads. */
    BUSY,

    /** CPU-efficient polling. Use for high CPU utilization scenarios. */
    PERIODICAL
  }

  /** Data format options for compression/decompression. */
  public static enum DataFormat {
    /** Raw DEFLATE with 4-byte header. */
    DEFLATE_4B,

    /** DEFLATE with GZip wrapper. */
    DEFLATE_GZIP,

    /** DEFLATE with extended GZip wrapper. */
    DEFLATE_GZIP_EXT,

    /** Raw DEFLATE format. */
    DEFLATE_RAW,

    /** ZLIB format. */
    ZLIB
  }

  /** Hardware buffer size options for QAT device. */
  public static enum HardwareBufferSize {
    /** Default buffer size (64 KB). */
    DEFAULT_BUFFER_SIZE(64 * 1024),

    /** Maximum buffer size (512 KB). */
    MAX_BUFFER_SIZE(512 * 1024);

    private final int value;

    HardwareBufferSize(int size) {
      this.value = size;
    }

    /**
     * Gets the value of the hw buffer size.
     *
     * @return the value of the buffer size in bytes.
     */
    public int getValue() {
      return value;
    }
  }

  /** QAT logging levels. */
  public static enum LogLevel {
    /** None. */
    NONE,
    /** Fatal errors. */
    FATAL,
    /** Errors. */
    ERROR,
    /** Warning. */
    WARNING,
    /** Info. */
    INFO,
    /** Debug messages. */
    DEBUG1,
    /** Test messages. */
    DEBUG2,
    /** Memory related messages. */
    DEBUG3
  }

  /**
   * Checks if QAT hardware is available.
   *
   * @return true if QAT is available, false otherwise.
   */
  public static boolean isQatAvailable() {
    return QatAvailableHolder.IS_QAT_AVAILABLE;
  }

  /** Defers static initialization until {@link #isQatAvailable()} is invoked. */
  static class QatAvailableHolder {
    static final boolean IS_QAT_AVAILABLE;

    static {
      boolean isQatAvailable;
      try {
        final QatZipper qzip = new QatZipper.Builder().mode(Mode.HARDWARE).build();
        qzip.end();
        isQatAvailable = true;
      } catch (UnsatisfiedLinkError
          | ExceptionInInitializerError
          | NoClassDefFoundError
          | RuntimeException e) {
        isQatAvailable = false;
      }
      IS_QAT_AVAILABLE = isQatAvailable;
    }
  }

  /**
   * Builder for QatZipper configuration. Provides fluent API for creating configured instances.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * QatZipper zipper = new QatZipper.Builder()
   *     .algorithm(Algorithm.ZSTD)
   *     .level(6)
   *     .mode(Mode.AUTO)
   *     .build();
   * }</pre>
   */
  public static class Builder {
    private Algorithm algorithm = DEFAULT_ALGORITHM;
    // null means "not set"; the algorithm-specific default is resolved in QatZipper(Builder).
    private Integer level = null;
    private Mode mode = DEFAULT_MODE;
    private PollingMode pollingMode = DEFAULT_POLLING_MODE;
    private DataFormat dataFormat = DEFAULT_DATA_FORMAT;
    private HardwareBufferSize hwBufferSize = DEFAULT_HW_BUFFER_SIZE;
    private LogLevel logLevel = DEFAULT_LOG_LEVEL;

    /** Constructs a builder that has default values for QatZipper. */
    public Builder() {}

    /**
     * Sets the compression {@link Algorithm}.
     *
     * @param algorithm the {@link Algorithm}.
     * @return This Builder.
     */
    public Builder algorithm(Algorithm algorithm) {
      this.algorithm = Objects.requireNonNull(algorithm, "algorithm cannot be null");
      return this;
    }

    /**
     * Gets the compression {@link Algorithm}.
     *
     * @return The Algorithm.
     */
    public Algorithm getAlgorithm() {
      return this.algorithm;
    }

    /**
     * Sets the compression level. Must be valid for the selected algorithm.
     *
     * @param level the compression level.
     * @return This Builder.
     */
    public Builder level(int level) {
      if (level < 1) {
        throw new IllegalArgumentException("Compression level must be at least 1, got: " + level);
      }
      this.level = level;
      return this;
    }

    /**
     * Sets the execution mode.
     *
     * @param mode the {@link Mode}.
     * @return This Builder.
     */
    public Builder mode(Mode mode) {
      this.mode = Objects.requireNonNull(mode, "mode cannot be null");
      return this;
    }

    /**
     * Sets the {@link PollingMode}.
     *
     * @param pollingMode the {@link PollingMode}.
     * @return This Builder.
     */
    public Builder pollingMode(PollingMode pollingMode) {
      this.pollingMode = Objects.requireNonNull(pollingMode, "pollingMode cannot be null");
      return this;
    }

    /**
     * Sets the {@link DataFormat}.
     *
     * @param dataFormat the {@link DataFormat}.
     * @return This Builder.
     */
    public Builder dataFormat(DataFormat dataFormat) {
      this.dataFormat = Objects.requireNonNull(dataFormat, "dataFormat cannot be null");
      return this;
    }

    /**
     * Sets the {@link HardwareBufferSize} QAT uses.
     *
     * @param hwBufferSize the {@link HardwareBufferSize} for QAT.
     * @return This Builder.
     */
    public Builder hardwareBufferSize(HardwareBufferSize hwBufferSize) {
      this.hwBufferSize = Objects.requireNonNull(hwBufferSize, "hwBufferSize cannot be null");
      return this;
    }

    /**
     * Sets the {@link LogLevel}.
     *
     * @param logLevel the {@link LogLevel}.
     * @return This Builder.
     */
    public Builder logLevel(LogLevel logLevel) {
      this.logLevel = Objects.requireNonNull(logLevel, "logLevel cannot be null");
      return this;
    }

    /**
     * Returns an instance of {@link QatZipper} created from the fields set on this builder.
     *
     * @return A QatZipper.
     */
    public QatZipper build() {
      return new QatZipper(this);
    }
  }

  /**
   * Resolves the effective compression level for the given algorithm, applying the
   * algorithm-specific default when unset and validating the range. Every constructor path goes
   * through this, so convenience constructors get the same defaults and validation as build().
   *
   * @throws IllegalArgumentException if the level is out of range for the algorithm
   */
  private static int resolveLevel(Algorithm algorithm, Integer level) {
    if (level == null) {
      return algorithm == Algorithm.ZSTD
          ? DEFAULT_COMPRESSION_LEVEL_ZSTD
          : DEFAULT_COMPRESSION_LEVEL_DEFLATE;
    }
    if (algorithm == Algorithm.ZSTD) {
      // QAT-accelerated ZSTD is based on https://github.com/intel/QAT-ZSTD-Plugin,
      // which supports only levels 1-12; the native session setup rejects anything higher.
      if (level < 1 || level > MAX_COMPRESSION_LEVEL_ZSTD) {
        throw new IllegalArgumentException(
            "ZSTD compression level must be between 1 and "
                + MAX_COMPRESSION_LEVEL_ZSTD
                + ", got: "
                + level);
      }
    } else {
      // DEFLATE and LZ4 have more restrictive limits
      if (level < 1 || level > 9) {
        throw new IllegalArgumentException(
            "Compression level for " + algorithm + " must be between 1 and 9, got: " + level);
      }
    }
    return level;
  }

  /**
   * Constructs a QatZipper using the provided Builder configuration.
   *
   * @param builder the Builder with configuration
   * @throws RuntimeException if QAT session initialization fails
   */
  private QatZipper(Builder builder) throws RuntimeException {
    this.algorithm = builder.algorithm;
    this.level = resolveLevel(builder.algorithm, builder.level);
    this.mode = builder.mode;
    this.pollingMode = builder.pollingMode;
    this.dataFormat = builder.dataFormat;
    this.hwBufferSize = builder.hwBufferSize;
    this.logLevel = builder.logLevel;

    // Initialize QAT session via JNI
    int status =
        InternalJNI.setupSession(
            this,
            algorithm.ordinal(),
            level,
            mode.ordinal(),
            pollingMode.ordinal(),
            dataFormat.ordinal(),
            hwBufferSize.getValue(),
            logLevel.ordinal());

    if (logLevel != LogLevel.NONE) {
      InternalJNI.setLogLevel(logLevel.ordinal());
    }

    this.isValid = true;
    this.bytesRead = 0;
    this.bytesWritten = 0;

    // Initialize ZSTD contexts if needed
    if (algorithm == Algorithm.ZSTD) {
      this.zstdCompressCtx = new ZstdCompressCtx();
      this.zstdCompressCtx.setLevel(level);
      if (mode == Mode.HARDWARE || (mode == Mode.AUTO && status == 0)) {
        zstdCompressCtx.registerSequenceProducer(new QatZstdSequenceProducer());
        zstdCompressCtx.setSequenceProducerFallback(true);
      }
      this.zstdDecompressCtx = new ZstdDecompressCtx();
    } else {
      this.zstdCompressCtx = null;
      this.zstdDecompressCtx = null;
    }
  }

  /** Constructs a QatZipper that uses default values for all parameters. */
  public QatZipper() {
    this(new Builder());
  }

  /**
   * Constructs a QatZipper that uses the provided algorithm. Default values are used for all the
   * other parameters.
   *
   * @param algorithm the {@link Algorithm}.
   */
  public QatZipper(Algorithm algorithm) {
    this(new Builder().algorithm(algorithm));
  }

  /**
   * Constructs a QatZipper that uses the provided algorithm and compression level. Default values
   * are used for all the other parameters.
   *
   * @param algorithm the {@link Algorithm}.
   * @param level the compression level.
   */
  public QatZipper(Algorithm algorithm, int level) {
    this(new Builder().algorithm(algorithm).level(level));
  }

  /**
   * Compresses the entire source array. Convenience method equivalent to compress(src, 0,
   * src.length, dst, 0, dst.length).
   *
   * @param src the source array holding uncompressed data
   * @param dst the destination array for compressed data
   * @return the number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if arrays are null or empty
   * @throws RuntimeException if compression fails
   */
  public int compress(byte[] src, byte[] dst) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateArraysNotNull(src, dst);
    return compress(src, 0, src.length, dst, 0, dst.length);
  }

  /**
   * Compresses source array data into destination array.
   *
   * <p>After successful compression:
   *
   * <ul>
   *   <li>bytesRead will equal srcLen
   *   <li>bytesWritten will equal returned compressed size
   * </ul>
   *
   * @param src the source array holding uncompressed data
   * @param srcOffset the offset in source array where data starts
   * @param srcLen the number of bytes to compress from source
   * @param dst the destination array for compressed data
   * @param dstOffset the offset in destination array where compressed data is written
   * @param dstLen the maximum number of bytes that can be written to destination
   * @return the number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if arrays are null or empty
   * @throws ArrayIndexOutOfBoundsException if offsets are invalid
   * @throws RuntimeException if compression fails
   */
  public int compress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateByteArrays(src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (algorithm == Algorithm.ZSTD) {
      return compressZSTDByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    } else {
      return compressQATByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    }
  }

  /** Internal compression using QAT for non-ZSTD algorithms. */
  private int compressQATByteArray(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    long result =
        InternalJNI.compressBytesBytes(qzKey, src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (result < 0) {
      bytesRead = 0;
      bytesWritten = 0;
      throw new RuntimeException("QAT compression failed with error code: " + result);
    }

    bytesRead = unpackBytesRead(result);
    bytesWritten = unpackBytesWritten(result);
    return bytesWritten;
  }

  /** Internal compression using ZSTD for byte arrays. */
  private int compressZSTDByteArray(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    try {
      bytesRead = bytesWritten = 0;
      int compressedSize =
          zstdCompressCtx.compressByteArray(dst, dstOffset, dstLen, src, srcOffset, srcLen);

      if (compressedSize < 0) {
        throw new RuntimeException("ZSTD compression failed with error code: " + compressedSize);
      }

      bytesRead = srcLen;
      bytesWritten = compressedSize;
      return compressedSize;
    } catch (Exception e) {
      bytesRead = 0;
      bytesWritten = 0;
      throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
    }
  }

  /**
   * Compresses source buffer to destination buffer. Advances both buffer positions.
   *
   * <p>Buffer positions semantics:
   *
   * <ul>
   *   <li>On input: position marks start of data, limit marks end
   *   <li>On success: source position is advanced by bytesRead, destination position is advanced by
   *       bytesWritten
   *   <li>On error: positions are restored to their initial values and bytesRead/bytesWritten are
   *       reset to 0
   * </ul>
   *
   * @param src the source buffer holding uncompressed data (position to limit)
   * @param dst the destination buffer for compressed data (position to limit)
   * @return the number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if buffers are invalid
   * @throws ReadOnlyBufferException if destination buffer is read-only
   * @throws RuntimeException if compression fails
   */
  public int compress(ByteBuffer src, ByteBuffer dst) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateByteBuffers(src, dst);
    if (dst.isReadOnly()) {
      throw new ReadOnlyBufferException();
    }

    if (algorithm == Algorithm.ZSTD) {
      return compressZSTDByteBuffer(src, dst);
    } else {
      return compressQATByteBuffer(src, dst);
    }
  }

  /**
   * Internal compression using QAT for ByteBuffers, handling every combination of
   * direct/heap/read-only on each side in a single method.
   *
   * <p>The structure mirrors {@code java.util.zip.Deflater.deflate(ByteBuffer, int)} in OpenJDK:
   *
   * <ul>
   *   <li>For a direct buffer, pass the {@code ByteBuffer} reference along with its position to the
   *       {@code ...Buffer...} JNI variant. The JNI layer obtains the native address via {@code
   *       GetDirectBufferAddress}.
   *   <li>For a heap buffer, prefer {@link ByteBuffer#array()} when {@link ByteBuffer#hasArray()}
   *       is true (which excludes read-only buffers); add {@link ByteBuffer#arrayOffset()} to the
   *       position to get the true backing-array index.
   *   <li>For a buffer that is neither direct nor array-backed (read-only heap is the canonical
   *       case), fall through to a staging copy through a direct ByteBuffer -- two unavoidable
   *       copies, no more.
   *   <li>Every call into JNI with a direct buffer is wrapped in {@code try { ... } finally {
   *       Reference.reachabilityFence(buf); }} so the {@link java.lang.ref.Cleaner} cannot free the
   *       buffer's native memory while JNI is reading or writing it.
   * </ul>
   */
  private int compressQATByteBuffer(ByteBuffer src, ByteBuffer dst) {
    final int initialSrcPos = src.position();
    final int initialDstPos = dst.position();
    final int srcRem = src.remaining();
    final int dstRem = dst.remaining();

    try {
      long result;
      if (src.isDirect()) {
        if (dst.isDirect()) {
          result = compressBufferBuffer(src, initialSrcPos, srcRem, dst, initialDstPos, dstRem);
        } else if (dst.hasArray()) {
          result =
              compressBufferBytes(
                  src,
                  initialSrcPos,
                  srcRem,
                  dst.array(),
                  dst.arrayOffset() + initialDstPos,
                  dstRem);
        } else {
          // direct src, mixed-mode dst -- stage dst through a direct buffer.
          return compressViaMixedDst(src, initialSrcPos, dst, initialDstPos);
        }
      } else if (src.hasArray()) {
        if (dst.isDirect()) {
          result =
              compressBytesBuffer(
                  src.array(),
                  src.arrayOffset() + initialSrcPos,
                  srcRem,
                  dst,
                  initialDstPos,
                  dstRem);
        } else if (dst.hasArray()) {
          result =
              InternalJNI.compressBytesBytes(
                  qzKey,
                  src.array(),
                  src.arrayOffset() + initialSrcPos,
                  srcRem,
                  dst.array(),
                  dst.arrayOffset() + initialDstPos,
                  dstRem);
        } else {
          return compressViaMixedDst(src, initialSrcPos, dst, initialDstPos);
        }
      } else {
        // mixed-mode src (e.g. read-only heap); stage src.
        return compressViaMixedSrc(src, initialSrcPos, dst, initialDstPos);
      }

      if (result < 0) {
        bytesRead = 0;
        bytesWritten = 0;
        throw new RuntimeException("QAT compression failed with error code: " + result);
      }
      int br = unpackBytesRead(result);
      int bw = unpackBytesWritten(result);
      bytesRead = br;
      bytesWritten = bw;
      src.position(initialSrcPos + br);
      dst.position(initialDstPos + bw);
      return bw;
    } catch (RuntimeException e) {
      src.position(initialSrcPos);
      dst.position(initialDstPos);
      bytesRead = 0;
      bytesWritten = 0;
      throw e;
    }
  }

  /** Bytes -> direct buffer, with a reachabilityFence around the JNI call. */
  private long compressBytesBuffer(
      byte[] srcArr, int srcOff, int srcLen, ByteBuffer dst, int dstPos, int dstLen) {
    try {
      return InternalJNI.compressBytesBuffer(qzKey, srcArr, srcOff, srcLen, dst, dstPos, dstLen);
    } finally {
      Reference.reachabilityFence(dst);
    }
  }

  /** Direct buffer -> bytes, with a reachabilityFence around the JNI call. */
  private long compressBufferBytes(
      ByteBuffer src, int srcPos, int srcLen, byte[] dstArr, int dstOff, int dstLen) {
    try {
      return InternalJNI.compressBufferBytes(qzKey, src, srcPos, srcLen, dstArr, dstOff, dstLen);
    } finally {
      Reference.reachabilityFence(src);
    }
  }

  /** Direct -> direct, with reachabilityFences around the JNI call. */
  private long compressBufferBuffer(
      ByteBuffer src, int srcPos, int srcLen, ByteBuffer dst, int dstPos, int dstLen) {
    try {
      return InternalJNI.compressBufferBuffer(qzKey, src, srcPos, srcLen, dst, dstPos, dstLen);
    } finally {
      Reference.reachabilityFence(src);
      Reference.reachabilityFence(dst);
    }
  }

  /**
   * Rare-mode path where the destination is neither direct nor array-backed (read-only heap buffer
   * is the canonical case). Stage the destination through a direct ByteBuffer and copy back at the
   * end -- one mandatory copy out.
   */
  private int compressViaMixedDst(
      ByteBuffer src, int initialSrcPos, ByteBuffer dst, int initialDstPos) {
    final int dstLen = dst.remaining();
    ByteBuffer dstStaging = getStagingBuffer(dstLen);
    dstStaging.limit(dstLen);
    int compressed;
    if (src.isDirect()) {
      long result =
          compressBufferBuffer(src, initialSrcPos, src.remaining(), dstStaging, 0, dstLen);
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT compression failed with error code: " + result);
      }
      compressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
    } else {
      // src is array-backed
      long result =
          compressBytesBuffer(
              src.array(),
              src.arrayOffset() + initialSrcPos,
              src.remaining(),
              dstStaging,
              0,
              dstLen);
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT compression failed with error code: " + result);
      }
      compressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
    }
    dstStaging.position(0);
    dstStaging.limit(compressed);
    dst.put(dstStaging);
    bytesWritten = compressed;
    src.position(initialSrcPos + bytesRead);
    return compressed;
  }

  /**
   * Rare-mode path where the source is neither direct nor array-backed. Stage the source through a
   * direct ByteBuffer -- one mandatory copy in.
   */
  private int compressViaMixedSrc(
      ByteBuffer src, int initialSrcPos, ByteBuffer dst, int initialDstPos) {
    final int srcLen = src.remaining();
    ByteBuffer srcStaging = getStagingBuffer(srcLen);
    srcStaging.put(src);
    srcStaging.flip();
    src.position(initialSrcPos);

    int compressed;
    if (dst.isDirect()) {
      long result =
          compressBufferBuffer(srcStaging, 0, srcLen, dst, initialDstPos, dst.remaining());
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT compression failed with error code: " + result);
      }
      compressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
      dst.position(initialDstPos + compressed);
    } else if (dst.hasArray()) {
      long result =
          compressBufferBytes(
              srcStaging,
              0,
              srcLen,
              dst.array(),
              dst.arrayOffset() + initialDstPos,
              dst.remaining());
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT compression failed with error code: " + result);
      }
      compressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
      dst.position(initialDstPos + compressed);
    } else {
      // both mixed-mode -- stage both sides
      return compressViaBothMixed(srcStaging, initialSrcPos, dst, initialDstPos);
    }
    bytesWritten = compressed;
    src.position(initialSrcPos + bytesRead);
    return compressed;
  }

  /**
   * Both src and dst are mixed-mode (read-only heap on both sides). Both sides stage through direct
   * buffers. srcStaging is already filled by the caller.
   */
  private int compressViaBothMixed(
      ByteBuffer srcStaging, int initialSrcPos, ByteBuffer dst, int initialDstPos) {
    final int dstLen = dst.remaining();
    ByteBuffer dstStaging = ByteBuffer.allocateDirect(dstLen);
    long result =
        compressBufferBuffer(srcStaging, 0, srcStaging.remaining(), dstStaging, 0, dstLen);
    if (result < 0) {
      bytesRead = bytesWritten = 0;
      throw new RuntimeException("QAT compression failed with error code: " + result);
    }
    int compressed = unpackBytesWritten(result);
    bytesRead = unpackBytesRead(result);
    dstStaging.position(0);
    dstStaging.limit(compressed);
    dst.put(dstStaging);
    bytesWritten = compressed;
    return compressed;
  }

  /** Internal compression using ZSTD for ByteBuffers. Handles all combinations of buffer types. */
  private int compressZSTDByteBuffer(ByteBuffer src, ByteBuffer dst) {
    final int initialSrcPosition = src.position();
    final int initialDstPosition = dst.position();
    bytesRead = bytesWritten = 0;
    int compressedSize;

    try {
      if (src.hasArray() && dst.hasArray()) {
        compressedSize = compressZSTDArrayBuffers(src, dst, initialSrcPosition, initialDstPosition);
      } else if (src.isDirect() && dst.isDirect()) {
        compressedSize =
            compressZSTDDirectBuffers(src, dst, initialSrcPosition, initialDstPosition);
      } else if (src.hasArray() && dst.isDirect()) {
        compressedSize =
            compressZSTDArrayToDirect(src, dst, initialSrcPosition, initialDstPosition);
      } else if (src.isDirect() && dst.hasArray()) {
        compressedSize =
            compressZSTDDirectToArray(src, dst, initialSrcPosition, initialDstPosition);
      } else {
        // Mixed buffers
        compressedSize = compressZSTDMixedBuffers(src, dst, initialSrcPosition, initialDstPosition);
      }
    } catch (RuntimeException e) {
      src.position(initialSrcPosition);
      dst.position(initialDstPosition);
      bytesRead = 0;
      bytesWritten = 0;
      throw e;
    }

    bytesRead = src.position() - initialSrcPosition;
    bytesWritten = compressedSize;
    return compressedSize;
  }

  private int compressZSTDArrayBuffers(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    byte[] srcArr = src.array();
    byte[] dstArr = dst.array();
    int srcOffset = src.arrayOffset() + srcPos;
    int dstOffset = dst.arrayOffset() + dstPos;
    int srcLen = src.remaining();
    int dstLen = dst.remaining();

    int compressedSize =
        zstdCompressCtx.compressByteArray(dstArr, dstOffset, dstLen, srcArr, srcOffset, srcLen);

    if (compressedSize < 0) {
      throw new RuntimeException("ZSTD compression failed with error code: " + compressedSize);
    }

    src.position(src.position() + srcLen);
    dst.position(dstPos + compressedSize);
    return compressedSize;
  }

  private int compressZSTDDirectBuffers(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    int srcLen = src.remaining();
    int dstLen = dst.remaining();
    int compressedSize =
        zstdCompressCtx.compressDirectByteBuffer(dst, dstPos, dstLen, src, srcPos, srcLen);

    if (compressedSize < 0) {
      throw new RuntimeException("ZSTD compression failed with error code: " + compressedSize);
    }

    src.position(src.position() + srcLen);
    dst.position(dst.position() + compressedSize);

    return compressedSize;
  }

  private int compressZSTDArrayToDirect(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    byte[] srcArr = src.array();
    int srcOffset = src.arrayOffset() + srcPos;
    int srcLen = src.remaining();
    int dstLen = dst.remaining();

    ByteBuffer tempSrc = getStagingBuffer(srcLen);
    tempSrc.put(srcArr, srcOffset, srcLen);
    tempSrc.flip();

    int compressedSize =
        zstdCompressCtx.compressDirectByteBuffer(dst, dstPos, dstLen, tempSrc, 0, srcLen);

    if (compressedSize < 0) {
      throw new RuntimeException("ZSTD compression failed with error code: " + compressedSize);
    }

    src.position(src.position() + srcLen);
    dst.position(dst.position() + compressedSize);

    return compressedSize;
  }

  private int compressZSTDDirectToArray(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    int srcLen = src.remaining();
    int dstLen = dst.remaining();

    ByteBuffer tempDst = getStagingBuffer(dstLen);

    int compressedSize =
        zstdCompressCtx.compressDirectByteBuffer(tempDst, 0, dstLen, src, srcPos, srcLen);

    if (compressedSize < 0) {
      throw new RuntimeException("ZSTD compression failed with error code: " + compressedSize);
    }

    src.position(src.position() + srcLen);

    byte[] dstArr = dst.array();
    int dstOffset = dst.arrayOffset() + dstPos;
    tempDst.flip();
    tempDst.get(dstArr, dstOffset, compressedSize);
    dst.position(dstPos + compressedSize);

    return compressedSize;
  }

  private int compressZSTDMixedBuffers(
      ByteBuffer src, ByteBuffer dst, int initialSrcPos, int initialDstPos) {
    final int srcLen = src.remaining();
    final int dstLen = dst.remaining();

    byte[] srcArr = new byte[srcLen];
    byte[] dstArr = new byte[dstLen];

    src.get(srcArr);
    src.position(initialSrcPos);

    int compressedSize = compressZSTDByteArray(srcArr, 0, srcLen, dstArr, 0, dstLen);
    src.position(initialSrcPos + bytesRead);
    dst.put(dstArr, 0, compressedSize);

    return compressedSize;
  }

  /**
   * Decompresses the entire source array. Convenience method equivalent to decompress(src, 0,
   * src.length, dst, 0, dst.length).
   *
   * @param src the source array holding compressed data
   * @param dst the destination array for decompressed data
   * @return the number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if arrays are null or empty
   * @throws RuntimeException if decompression fails
   */
  public int decompress(byte[] src, byte[] dst) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateArraysNotNull(src, dst);
    return decompress(src, 0, src.length, dst, 0, dst.length);
  }

  /**
   * Decompresses source array data into destination array.
   *
   * <p>After successful decompression:
   *
   * <ul>
   *   <li>bytesRead will contain the number of compressed bytes processed
   *   <li>bytesWritten will equal returned decompressed size
   * </ul>
   *
   * @param src the source array holding compressed data
   * @param srcOffset the offset in source array where data starts
   * @param srcLen the number of compressed bytes to process
   * @param dst the destination array for decompressed data
   * @param dstOffset the offset in destination array where decompressed data is written
   * @param dstLen the maximum number of bytes that can be written to destination
   * @return the number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if arrays are null or empty
   * @throws ArrayIndexOutOfBoundsException if offsets are invalid
   * @throws RuntimeException if decompression fails
   */
  public int decompress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateByteArrays(src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (algorithm == Algorithm.ZSTD) {
      return decompressZSTDByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    } else {
      return decompressQATByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    }
  }

  /** Internal decompression using QAT for non-ZSTD algorithms. */
  private int decompressQATByteArray(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    long result =
        InternalJNI.decompressBytesBytes(qzKey, src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (result < 0) {
      bytesRead = 0;
      bytesWritten = 0;
      throw new RuntimeException("QAT decompression failed with error code: " + result);
    }

    bytesRead = unpackBytesRead(result);
    bytesWritten = unpackBytesWritten(result);
    return bytesWritten;
  }

  /**
   * Compresses a source array split into fixed-size sub-blocks into a destination array in a single
   * JNI call. The native side pins all arrays once, loops calling qzCompress for each sub-block,
   * stores each block's compressed size in the sizes array, then unpins. This reduces JNI overhead
   * from N transitions (one per sub-block) to exactly 1.
   *
   * <p>Use this method when the source buffer should be compressed as multiple fixed-size
   * sub-blocks (e.g. Lucene sub-blocks) whose compressed data is written contiguously into a single
   * output buffer.
   *
   * <p>Note: For the ZSTD algorithm, {@code blockLength}, {@code sizes}, and {@code startBlock} are
   * ignored; the entire source range is compressed as a single frame.
   *
   * @param src the source array containing uncompressed data
   * @param srcOffset the start offset in the source array
   * @param srcLen the total number of bytes to compress
   * @param blockLength the sub-block size; the last block may be smaller
   * @param dst the destination array for compressed data
   * @param dstOffset the start offset in the destination array
   * @param dstLen the maximum number of bytes that can be written to destination
   * @param sizes an int array with at least startBlock + ceil(srcLen / blockLength) entries; on
   *     return, sizes[startBlock..startBlock+N-1] holds the compressed size of each sub-block
   * @param startBlock the sub-block index to start compressing from (for resumption after grow)
   * @return the total number of bytes written to dst for blocks [startBlock..last], or a negative
   *     value {@code -(blocksCompleted + 1)} if the destination buffer was too small. In the
   *     partial case, sizes[startBlock..startBlock+blocksCompleted-1] are valid.
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if arrays are null or empty
   * @throws ArrayIndexOutOfBoundsException if offsets are invalid
   * @throws RuntimeException if compression fails for a reason other than buffer overflow
   */
  public int compressFull(
      byte[] src,
      int srcOffset,
      int srcLen,
      int blockLength,
      byte[] dst,
      int dstOffset,
      int dstLen,
      int[] sizes,
      int startBlock) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateByteArrays(src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (algorithm == Algorithm.ZSTD) {
      // Compresses the whole range as a single frame; sets bytesRead/bytesWritten itself.
      return compressZSTDByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    }

    if (blockLength <= 0) {
      throw new IllegalArgumentException("Block length must be positive, got: " + blockLength);
    }
    if (sizes == null) {
      throw new IllegalArgumentException("Sizes array cannot be null");
    }
    int numBlocks = (int) (((long) srcLen + blockLength - 1) / blockLength);
    if (startBlock < 0 || (long) startBlock + numBlocks > sizes.length) {
      throw new ArrayIndexOutOfBoundsException(
          String.format(
              "sizes bounds exceeded: startBlock=%d, blocks=%d, sizes.length=%d",
              startBlock, numBlocks, sizes.length));
    }

    long result =
        InternalJNI.compressFullBytesBytes(
            qzKey, src, srcOffset, srcLen, blockLength, dst, dstOffset, dstLen, sizes, startBlock);

    if (result >= 0) {
      // All remaining blocks compressed successfully
      bytesRead = unpackBytesRead(result);
      bytesWritten = unpackBytesWritten(result);
      return bytesWritten;
    }

    // Negative: either partial progress or real error.
    // Partial progress is encoded as -(blocksCompleted + 1) where blocksCompleted >= 0.
    // Real JNI errors (session null, etc.) are -1 with an exception already thrown.
    // qzCompress errors throw IllegalStateException from native.
    // So if we reach here without an exception, it's partial progress.
    // Return the negative value directly — caller interprets it.
    return (int) result;
  }

  /**
   * Decompresses a source array containing multiple concatenated compressed frames into a
   * destination array in a single JNI call. The native side pins both arrays once, loops calling
   * qzDecompress until all input is consumed, then unpins. This is the Inflater-style "native loop"
   * approach — it reduces JNI overhead from N transitions (one per frame) to exactly 1.
   *
   * <p>Use this method when the source buffer contains back-to-back compressed frames (e.g. Lucene
   * sub-blocks) that should all be decompressed into a contiguous output buffer.
   *
   * @param src the source array containing concatenated compressed frames
   * @param srcOffset the start offset in the source array
   * @param srcLen the total number of compressed bytes to process
   * @param dst the destination array for decompressed data
   * @param dstOffset the start offset in the destination array
   * @param dstLen the maximum number of bytes that can be written to destination
   * @return the total number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if arrays are null or empty
   * @throws ArrayIndexOutOfBoundsException if offsets are invalid
   * @throws RuntimeException if decompression fails
   */
  public int decompressFull(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateByteArrays(src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (algorithm == Algorithm.ZSTD) {
      // Decompresses the whole range; sets bytesRead/bytesWritten itself.
      return decompressZSTDByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    }

    long result =
        InternalJNI.decompressFullBytesBytes(qzKey, src, srcOffset, srcLen, dst, dstOffset, dstLen);

    if (result < 0) {
      bytesRead = 0;
      bytesWritten = 0;
      throw new RuntimeException("QAT decompression failed with error code: " + result);
    }

    bytesRead = unpackBytesRead(result);
    bytesWritten = unpackBytesWritten(result);
    return bytesWritten;
  }

  /** Internal decompression using ZSTD for byte arrays. */
  private int decompressZSTDByteArray(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    try {
      bytesRead = bytesWritten = 0;
      int decompressedSize =
          zstdDecompressCtx.decompressByteArray(dst, dstOffset, dstLen, src, srcOffset, srcLen);

      if (decompressedSize < 0) {
        throw new RuntimeException(
            "ZSTD decompression failed with error code: " + decompressedSize);
      }

      bytesRead = srcLen;
      bytesWritten = decompressedSize;
      return decompressedSize;
    } catch (Exception e) {
      bytesRead = 0;
      bytesWritten = 0;
      throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
    }
  }

  /**
   * Decompresses source buffer to destination buffer. Advances both buffer positions.
   *
   * <p>Buffer positions semantics:
   *
   * <ul>
   *   <li>On input: position marks start of data, limit marks end
   *   <li>On success: source position is advanced by bytesRead, destination position is advanced by
   *       bytesWritten
   *   <li>On error: positions are restored to their initial values and bytesRead/bytesWritten are
   *       reset to 0
   * </ul>
   *
   * @param src the source buffer holding compressed data (position to limit)
   * @param dst the destination buffer for decompressed data (position to limit)
   * @return the number of bytes written to dst
   * @throws IllegalStateException if this QatZipper has been closed
   * @throws IllegalArgumentException if buffers are invalid
   * @throws ReadOnlyBufferException if destination buffer is read-only
   * @throws RuntimeException if decompression fails
   */
  public int decompress(ByteBuffer src, ByteBuffer dst) {
    bytesRead = bytesWritten = 0;
    validateSessionOpen();
    validateByteBuffers(src, dst);

    if (dst.isReadOnly()) {
      throw new ReadOnlyBufferException();
    }

    if (algorithm == Algorithm.ZSTD) {
      return decompressZSTDByteBuffer(src, dst);
    } else {
      return decompressQATByteBuffer(src, dst);
    }
  }

  /**
   * Internal decompression using QAT for ByteBuffers. Symmetric to {@link #compressQATByteBuffer}
   * -- see that method for the design rationale.
   */
  private int decompressQATByteBuffer(ByteBuffer src, ByteBuffer dst) {
    final int initialSrcPos = src.position();
    final int initialDstPos = dst.position();
    final int srcRem = src.remaining();
    final int dstRem = dst.remaining();

    try {
      long result;
      if (src.isDirect()) {
        if (dst.isDirect()) {
          result = decompressBufferBuffer(src, initialSrcPos, srcRem, dst, initialDstPos, dstRem);
        } else if (dst.hasArray()) {
          result =
              decompressBufferBytes(
                  src,
                  initialSrcPos,
                  srcRem,
                  dst.array(),
                  dst.arrayOffset() + initialDstPos,
                  dstRem);
        } else {
          return decompressViaMixedDst(src, initialSrcPos, dst, initialDstPos);
        }
      } else if (src.hasArray()) {
        if (dst.isDirect()) {
          result =
              decompressBytesBuffer(
                  src.array(),
                  src.arrayOffset() + initialSrcPos,
                  srcRem,
                  dst,
                  initialDstPos,
                  dstRem);
        } else if (dst.hasArray()) {
          result =
              InternalJNI.decompressBytesBytes(
                  qzKey,
                  src.array(),
                  src.arrayOffset() + initialSrcPos,
                  srcRem,
                  dst.array(),
                  dst.arrayOffset() + initialDstPos,
                  dstRem);
        } else {
          return decompressViaMixedDst(src, initialSrcPos, dst, initialDstPos);
        }
      } else {
        return decompressViaMixedSrc(src, initialSrcPos, dst, initialDstPos);
      }

      if (result < 0) {
        bytesRead = 0;
        bytesWritten = 0;
        throw new RuntimeException("QAT decompression failed with error code: " + result);
      }
      int br = unpackBytesRead(result);
      int bw = unpackBytesWritten(result);
      bytesRead = br;
      bytesWritten = bw;
      src.position(initialSrcPos + br);
      dst.position(initialDstPos + bw);
      return bw;
    } catch (RuntimeException e) {
      src.position(initialSrcPos);
      dst.position(initialDstPos);
      bytesRead = 0;
      bytesWritten = 0;
      throw e;
    }
  }

  private long decompressBytesBuffer(
      byte[] srcArr, int srcOff, int srcLen, ByteBuffer dst, int dstPos, int dstLen) {
    try {
      return InternalJNI.decompressBytesBuffer(qzKey, srcArr, srcOff, srcLen, dst, dstPos, dstLen);
    } finally {
      Reference.reachabilityFence(dst);
    }
  }

  private long decompressBufferBytes(
      ByteBuffer src, int srcPos, int srcLen, byte[] dstArr, int dstOff, int dstLen) {
    try {
      return InternalJNI.decompressBufferBytes(qzKey, src, srcPos, srcLen, dstArr, dstOff, dstLen);
    } finally {
      Reference.reachabilityFence(src);
    }
  }

  private long decompressBufferBuffer(
      ByteBuffer src, int srcPos, int srcLen, ByteBuffer dst, int dstPos, int dstLen) {
    try {
      return InternalJNI.decompressBufferBuffer(qzKey, src, srcPos, srcLen, dst, dstPos, dstLen);
    } finally {
      Reference.reachabilityFence(src);
      Reference.reachabilityFence(dst);
    }
  }

  private int decompressViaMixedDst(
      ByteBuffer src, int initialSrcPos, ByteBuffer dst, int initialDstPos) {
    final int dstLen = dst.remaining();
    ByteBuffer dstStaging = getStagingBuffer(dstLen);
    dstStaging.limit(dstLen);
    int decompressed;
    if (src.isDirect()) {
      long result =
          decompressBufferBuffer(src, initialSrcPos, src.remaining(), dstStaging, 0, dstLen);
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT decompression failed with error code: " + result);
      }
      decompressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
    } else {
      long result =
          decompressBytesBuffer(
              src.array(),
              src.arrayOffset() + initialSrcPos,
              src.remaining(),
              dstStaging,
              0,
              dstLen);
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT decompression failed with error code: " + result);
      }
      decompressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
    }
    dstStaging.position(0);
    dstStaging.limit(decompressed);
    dst.put(dstStaging);
    bytesWritten = decompressed;
    src.position(initialSrcPos + bytesRead);
    return decompressed;
  }

  private int decompressViaMixedSrc(
      ByteBuffer src, int initialSrcPos, ByteBuffer dst, int initialDstPos) {
    final int srcLen = src.remaining();
    ByteBuffer srcStaging = getStagingBuffer(srcLen);
    srcStaging.put(src);
    srcStaging.flip();
    src.position(initialSrcPos);

    int decompressed;
    if (dst.isDirect()) {
      long result =
          decompressBufferBuffer(srcStaging, 0, srcLen, dst, initialDstPos, dst.remaining());
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT decompression failed with error code: " + result);
      }
      decompressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
      dst.position(initialDstPos + decompressed);
    } else if (dst.hasArray()) {
      long result =
          decompressBufferBytes(
              srcStaging,
              0,
              srcLen,
              dst.array(),
              dst.arrayOffset() + initialDstPos,
              dst.remaining());
      if (result < 0) {
        bytesRead = bytesWritten = 0;
        throw new RuntimeException("QAT decompression failed with error code: " + result);
      }
      decompressed = unpackBytesWritten(result);
      bytesRead = unpackBytesRead(result);
      dst.position(initialDstPos + decompressed);
    } else {
      return decompressViaBothMixed(srcStaging, initialSrcPos, dst, initialDstPos);
    }
    bytesWritten = decompressed;
    src.position(initialSrcPos + bytesRead);
    return decompressed;
  }

  private int decompressViaBothMixed(
      ByteBuffer srcStaging, int initialSrcPos, ByteBuffer dst, int initialDstPos) {
    final int dstLen = dst.remaining();
    ByteBuffer dstStaging = ByteBuffer.allocateDirect(dstLen);
    long result =
        decompressBufferBuffer(srcStaging, 0, srcStaging.remaining(), dstStaging, 0, dstLen);
    if (result < 0) {
      bytesRead = bytesWritten = 0;
      throw new RuntimeException("QAT decompression failed with error code: " + result);
    }
    int decompressed = unpackBytesWritten(result);
    bytesRead = unpackBytesRead(result);
    dstStaging.position(0);
    dstStaging.limit(decompressed);
    dst.put(dstStaging);
    bytesWritten = decompressed;
    return decompressed;
  }

  /**
   * Internal decompression using ZSTD for ByteBuffers. Handles all combinations of buffer types.
   */
  private int decompressZSTDByteBuffer(ByteBuffer src, ByteBuffer dst) {
    final int initialSrcPosition = src.position();
    final int initialDstPosition = dst.position();
    bytesRead = bytesWritten = 0;
    int decompressedSize;

    try {
      if (src.hasArray() && dst.hasArray()) {
        decompressedSize =
            decompressZSTDArrayBuffers(src, dst, initialSrcPosition, initialDstPosition);
      } else if (src.isDirect() && dst.isDirect()) {
        decompressedSize =
            decompressZSTDDirectBuffers(src, dst, initialSrcPosition, initialDstPosition);
      } else if (src.hasArray() && dst.isDirect()) {
        decompressedSize =
            decompressZSTDArrayToDirect(src, dst, initialSrcPosition, initialDstPosition);
      } else if (src.isDirect() && dst.hasArray()) {
        decompressedSize =
            decompressZSTDDirectToArray(src, dst, initialSrcPosition, initialDstPosition);
      } else {
        decompressedSize =
            decompressZSTDMixedBuffers(src, dst, initialSrcPosition, initialDstPosition);
      }
    } catch (RuntimeException e) {
      src.position(initialSrcPosition);
      dst.position(initialDstPosition);
      bytesRead = 0;
      bytesWritten = 0;
      throw e;
    }

    bytesRead = src.position() - initialSrcPosition;
    bytesWritten = dst.position() - initialDstPosition;
    return decompressedSize;
  }

  private int decompressZSTDArrayBuffers(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    byte[] srcArr = src.array();
    byte[] dstArr = dst.array();
    int srcOffset = src.arrayOffset() + srcPos;
    int dstOffset = dst.arrayOffset() + dstPos;
    int srcLen = src.remaining();
    int dstLen = dst.remaining();

    int decompressedSize =
        zstdDecompressCtx.decompressByteArray(dstArr, dstOffset, dstLen, srcArr, srcOffset, srcLen);

    if (decompressedSize < 0) {
      throw new RuntimeException("ZSTD decompression failed with error code: " + decompressedSize);
    }

    // On success the entire source range was consumed.
    src.position(srcPos + srcLen);
    dst.position(dstPos + decompressedSize);
    return decompressedSize;
  }

  private int decompressZSTDDirectBuffers(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    int srcLen = src.remaining();
    int decompressedSize = zstdDecompressCtx.decompress(dst, src);

    if (decompressedSize < 0) {
      throw new RuntimeException("ZSTD decompression failed with error code: " + decompressedSize);
    }

    // zstd-jni advances both positions itself; set them explicitly so this method's
    // contract does not depend on that behavior.
    src.position(srcPos + srcLen);
    dst.position(dstPos + decompressedSize);

    return decompressedSize;
  }

  private int decompressZSTDArrayToDirect(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    byte[] srcArr = src.array();
    int srcOffset = src.arrayOffset() + srcPos;
    int srcLen = src.remaining();

    ByteBuffer tempSrc = getStagingBuffer(srcLen);
    tempSrc.put(srcArr, srcOffset, srcLen);
    tempSrc.flip();

    int decompressedSize = zstdDecompressCtx.decompress(dst, tempSrc);

    if (decompressedSize < 0) {
      throw new RuntimeException("ZSTD decompression failed with error code: " + decompressedSize);
    }

    // On success the entire source range was consumed.
    src.position(srcPos + srcLen);
    dst.position(dstPos + decompressedSize);

    return decompressedSize;
  }

  private int decompressZSTDDirectToArray(ByteBuffer src, ByteBuffer dst, int srcPos, int dstPos) {
    int srcLen = src.remaining();
    int dstLen = dst.remaining();

    ByteBuffer tempDst = getStagingBuffer(dstLen);

    int decompressedSize = zstdDecompressCtx.decompress(tempDst, src);

    if (decompressedSize < 0) {
      throw new RuntimeException("ZSTD decompression failed with error code: " + decompressedSize);
    }

    // On success the entire source range was consumed (zstd-jni advances src itself;
    // set it explicitly so this method's contract does not depend on that behavior).
    src.position(srcPos + srcLen);

    byte[] dstArr = dst.array();
    int dstOffset = dst.arrayOffset() + dstPos;
    tempDst.flip();
    tempDst.get(dstArr, dstOffset, decompressedSize);
    dst.position(dstPos + decompressedSize);

    return decompressedSize;
  }

  private int decompressZSTDMixedBuffers(
      ByteBuffer src, ByteBuffer dst, int initialSrcPos, int initialDstPos) {
    final int srcLen = src.remaining();
    final int dstLen = dst.remaining();

    byte[] srcArr = new byte[srcLen];
    byte[] dstArr = new byte[dstLen];

    src.get(srcArr);
    src.position(initialSrcPos);

    int decompressedSize = decompressZSTDByteArray(srcArr, 0, srcLen, dstArr, 0, dstLen);
    src.position(initialSrcPos + bytesRead);
    dst.put(dstArr, 0, decompressedSize);

    return decompressedSize;
  }

  /**
   * Gets the maximum compressed size for data of given length. Useful for pre-allocating output
   * buffers.
   *
   * @param sourceLength the length of uncompressed data
   * @return the maximum possible compressed size
   */
  public int maxCompressedLength(long sourceLength) {
    validateSessionOpen();

    if (sourceLength < 0) {
      throw new IllegalArgumentException("sourceLength cannot be negative");
    }
    if (algorithm == Algorithm.ZSTD) {
      return (int) Zstd.compressBound(sourceLength);
    }
    // For QAT algorithms, delegate to JNI
    return InternalJNI.maxCompressedLength(qzKey, sourceLength);
  }

  /**
   * Returns the number of bytes read from the source by the most recent compress/decompress
   * operation.
   *
   * <p>Note: If the most recent operation failed, this returns 0.
   *
   * @return number of bytes read from source in the most recent operation
   */
  public int getBytesRead() {
    return bytesRead;
  }

  /**
   * Returns the number of bytes written to the destination by the most recent compress/decompress
   * operation.
   *
   * <p>Note: If the most recent operation failed, this returns 0.
   *
   * @return number of bytes written to destination in the most recent operation
   */
  public int getBytesWritten() {
    return bytesWritten;
  }

  /**
   * Sets whether to include checksum in compressed output. Only supported for ZSTD algorithm.
   *
   * @param checksumFlag whether to enable checksum
   * @throws UnsupportedOperationException if algorithm is not ZSTD
   */
  public void setChecksumFlag(boolean checksumFlag) {
    if (algorithm != Algorithm.ZSTD) {
      throw new UnsupportedOperationException("Checksum flag is only supported for ZSTD algorithm");
    }
    zstdCompressCtx.setChecksum(checksumFlag);
    this.checksumFlag = checksumFlag;
  }

  /**
   * Gets the checksum flag status for ZSTD algorithm.
   *
   * @return whether checksum is enabled
   * @throws UnsupportedOperationException if algorithm is not ZSTD
   */
  public boolean getChecksumFlag() {
    if (algorithm != Algorithm.ZSTD) {
      throw new UnsupportedOperationException("Checksum flag is only supported for ZSTD algorithm");
    }
    return checksumFlag;
  }

  /**
   * Validates that this QatZipper session is still open.
   *
   * @throws IllegalStateException if session has been closed
   */
  private void validateSessionOpen() {
    if (!isValid) {
      throw new IllegalStateException("QatZipper session has been closed");
    }
  }

  /**
   * Validates byte arrays for compression/decompression operations.
   *
   * @throws IllegalArgumentException if arrays are invalid
   * @throws ArrayIndexOutOfBoundsException if offsets/lengths are out of bounds
   */
  private void validateByteArrays(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    validateArraysNotNull(src, dst);
    if (srcLen == 0 || dstLen == 0) {
      throw new IllegalArgumentException("Source and destination length cannot be zero");
    }
    validateArrayBounds(src, srcOffset, srcLen, "source");
    validateArrayBounds(dst, dstOffset, dstLen, "destination");
  }

  /**
   * Validates that the source and destination arrays are non-null.
   *
   * @throws IllegalArgumentException if either array is null
   */
  private static void validateArraysNotNull(byte[] src, byte[] dst) {
    if (src == null || dst == null) {
      throw new IllegalArgumentException("Source and destination arrays cannot be null");
    }
  }

  /**
   * Validates array bounds for a single array.
   *
   * @throws ArrayIndexOutOfBoundsException if bounds are invalid
   */
  private void validateArrayBounds(byte[] array, int offset, int length, String arrayName) {
    if (offset < 0) {
      throw new ArrayIndexOutOfBoundsException(
          String.format("%s offset cannot be negative: %d", arrayName, offset));
    }
    if (length < 0) {
      throw new ArrayIndexOutOfBoundsException(
          String.format("%s length cannot be negative: %d", arrayName, length));
    }
    if (offset + length > array.length) {
      throw new ArrayIndexOutOfBoundsException(
          String.format(
              "%s bounds exceeded: offset=%d, length=%d, array.length=%d",
              arrayName, offset, length, array.length));
    }
  }

  /**
   * Validates ByteBuffers for compression/decompression operations.
   *
   * @throws IllegalArgumentException if buffers are invalid
   */
  private void validateByteBuffers(ByteBuffer src, ByteBuffer dst) {
    if (src == null || dst == null) {
      throw new IllegalArgumentException("Source and destination buffers cannot be null");
    }
    if (src.position() >= src.limit()) {
      throw new IllegalArgumentException("Source buffer position >= limit; no data to process");
    }
    if (dst.position() >= dst.limit()) {
      throw new IllegalArgumentException(
          "Destination buffer position >= limit; no space for output");
    }
  }

  /**
   * Closes this QatZipper and releases associated resources. Can be called multiple times safely.
   *
   * @throws RuntimeException if QAT session cannot be gracefully closed
   */
  public void end() throws RuntimeException {
    if (isValid) {
      try {
        InternalJNI.teardown(qzKey);
        isValid = false;
      } catch (Exception e) {
        throw new RuntimeException("Failed to close QAT session", e);
      } finally {
        // Close ZSTD contexts if initialized
        if (zstdCompressCtx != null) {
          try {
            zstdCompressCtx.close();
          } catch (Exception e) {
            // Ignore exceptions during cleanup
          }
        }
        if (zstdDecompressCtx != null) {
          try {
            zstdDecompressCtx.close();
          } catch (Exception e) {
            // Ignore exceptions during cleanup
          }
        }
      }
    }
  }

  /** Extract bytes_read from a packed JNI result. Caller must check {@code r >= 0} first. */
  private static int unpackBytesRead(long r) {
    return (int) (r & 0x7FFFFFFFL);
  }

  /** Extract bytes_written from a packed JNI result. Caller must check {@code r >= 0} first. */
  private static int unpackBytesWritten(long r) {
    return (int) ((r >>> 31) & 0x7FFFFFFFL);
  }
}
