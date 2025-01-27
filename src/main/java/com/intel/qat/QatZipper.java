/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
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
  /** The default compression algorithm. */
  public static final Algorithm DEFAULT_ALGORITHM = Algorithm.DEFLATE;

  /** The default compression level is 6. */
  public static final int DEFAULT_COMPRESS_LEVEL = 6;

  /** The default execution mode. */
  public static final Mode DEFAULT_MODE = Mode.AUTO;

  /**
   * The default number of times QatZipper attempts to acquire hardware resources is <code>0</code>.
   */
  public static final int DEFAULT_RETRY_COUNT = 0;

  /** The default polling mode. */
  public static final PollingMode DEFAULT_POLLING_MODE = PollingMode.BUSY;

  /** The default data format. */
  public static final DataFormat DEFAULT_DATA_FORMAT = DataFormat.DEFLATE_GZIP_EXT;

  /** The default hardware buffer size. */
  public static final HardwareBufferSize DEFAULT_HW_BUFFER_SIZE =
      HardwareBufferSize.DEFAULT_BUFFER_SIZE;

  /** The current compression algorithm. */
  private Algorithm algorithm = DEFAULT_ALGORITHM;

  /** The compression level. */
  private int level = DEFAULT_COMPRESS_LEVEL;

  /** The mode of execution. */
  private Mode mode = DEFAULT_MODE;

  /**
   * The number of retry counts for session creation before Qat-Java gives up and throws an error.
   */
  private int retryCount = DEFAULT_RETRY_COUNT;

  /** The polling mode. */
  private PollingMode pollingMode = DEFAULT_POLLING_MODE;

  /** The data format. */
  private DataFormat dataFormat = DEFAULT_DATA_FORMAT;

  /** The buffer size for QAT device. */
  private HardwareBufferSize hwBufferSize = DEFAULT_HW_BUFFER_SIZE;

  /** Indicates if a QAT session is valid or not. */
  private boolean isValid;

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

  /** Zstd compress context. */
  private ZstdCompressCtx zstdCompressCtx;

  /** Zstd decompress context. */
  private ZstdDecompressCtx zstdDecompressCtx;

  /** Checksum flag, currently valid for only ZSTD. */
  private boolean checksumFlag;

  static {
    InternalJNI.initFieldIDs();

    // Needed for applications where a Java security manager is in place -- e.g. OpenSearch.
    SecurityManager sm = System.getSecurityManager();
    if (sm == null) {
      cleaner = Cleaner.create();
    } else {
      cleaner =
          java.security.AccessController.doPrivileged(
              (java.security.PrivilegedAction<Cleaner>) Cleaner::create);
    }
  }

  /** The compression algorithm to use. DEFLATE and LZ4 are supported. */
  public static enum Algorithm {
    /** The deflate compression algorithm. */
    DEFLATE,

    /** The LZ4 compression algorithm. */
    LZ4,

    /** The Zstandard compression algorithm. */
    ZSTD
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

  /**
   * Polling mode dictates how QAT processes compression/decompression requests and waits for a
   * response, directly affecting the performance of these operations. Two polling modes are
   * supported: BUSY and PERIODICAL. BUSY polling is the default polling mode.<br>
   * <br>
   * Use BUSY polling mode when:
   *
   * <ul>
   *   <li>Your CPUs are not fully saturated and have cycles to spare.
   *   <li>Your workload is latency-sensitive.
   * </ul>
   *
   * <br>
   * Use PERIODICAL polling mode when:
   *
   * <ul>
   *   <li>Your workload has very high CPU utilization.
   *   <li>Your workload is throughput-sensitive.
   * </ul>
   */
  public static enum PollingMode {
    /** Use this mode unless your workload is CPU-bound. */
    BUSY,

    /** Use this mode when your workload is CPU-bound. */
    PERIODICAL
  }

  /**
   * The data format to use. Qat-Java supports the following: <br>
   *
   * <ul>
   *   <li>DEFLATE_4B -- raw DEFLATE format with 4 byte header.
   *   <li>DEFLATE_GZIP -- DEFLATE wrapped by GZip header and footer.
   *   <li>DEFLATE_GZIP_EXT -- DEFLATE wrapped by GZip extended header and footer.
   *   <li>DEFLATE_RAW -- raw DEFLATE format.
   * </ul>
   */
  public static enum DataFormat {
    /** Raw DEFLATE format with 4 byte header. */
    DEFLATE_4B,

    /** DEFLATE wrapped by GZip header and footer. */
    DEFLATE_GZIP,

    /** DEFLATE wrapped by GZip extended header and footer. */
    DEFLATE_GZIP_EXT,

    /** Raw DEFLATE format. */
    DEFLATE_RAW
  }

  /**
   * Hardware buffer size the QAT uses internally. <br>
   *
   * <ul>
   *   <li>DEFAULT_BUFFER_SIZE -- 64KB
   *   <li>MAX_BUFFER_SIZE -- 512KB
   * </ul>
   */
  public static enum HardwareBufferSize {
    /** The default buffer size for the QAT device (64KB). */
    DEFAULT_BUFFER_SIZE(64 * 1024),

    /** The maximum buffer size allowed for the QAT device (512KB). */
    MAX_BUFFER_SIZE(512 * 1024);

    private final int value;

    private HardwareBufferSize(int hwBufferSize) {
      value = hwBufferSize;
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

  /**
   * Builder is used to build instances of {@link QatZipper} from values configured by the setters.
   */
  public static class Builder {
    private Algorithm algorithm = DEFAULT_ALGORITHM;
    private int level = DEFAULT_COMPRESS_LEVEL;
    private Mode mode = DEFAULT_MODE;
    private int retryCount = DEFAULT_RETRY_COUNT;
    private PollingMode pollingMode = DEFAULT_POLLING_MODE;
    private DataFormat dataFormat = DEFAULT_DATA_FORMAT;
    private HardwareBufferSize hwBufferSize = DEFAULT_HW_BUFFER_SIZE;

    /**
     * Constructs a builder that has default values for QatZipper -- {@link DEFAULT_ALGORITHM},
     * {@link DEFAULT_COMPRESS_LEVEL}, {@link DEFAULT_MODE}, {@link DEFAULT_RETRY_COUNT}, {@link
     * DEFAULT_POLLING_MODE}, {@link DEFAULT_DATA_FORMAT}, and {@link DEFAULT_HW_BUFFER_SIZE}.
     */
    public Builder() {}

    /**
     * Sets the compression {@link Algorithm}.
     *
     * @param algorithm the {@link Algorithm}.
     * @return This Builder.
     */
    public Builder setAlgorithm(Algorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    /**
     * Gets the compression {@link Algorithm}.
     *
     * @return The Algorithm.
     */
    Algorithm getAlgorithm() {
      return this.algorithm;
    }

    /**
     * Sets the compression level.
     *
     * @param level the compression level.
     * @return This Builder.
     */
    public Builder setLevel(int level) {
      this.level = level;
      return this;
    }

    /**
     * Sets the mode of execution.
     *
     * @param mode the {@link Mode}.
     * @return This Builder.
     */
    public Builder setMode(Mode mode) {
      this.mode = mode;
      return this;
    }

    /**
     * Sets the number of tries to acquire hardware resouces before giving up.
     *
     * @param retryCount the {@link PollingMode}.
     * @return This Builder.
     */
    public Builder setRetryCount(int retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    /**
     * Sets the {@link PollingMode}.
     *
     * @param pollingMode the {@link PollingMode}.
     * @return This Builder.
     */
    public Builder setPollingMode(PollingMode pollingMode) {
      this.pollingMode = pollingMode;
      return this;
    }

    /**
     * Sets the {@link DataFormat}.
     *
     * @param dataFormat the {@link DataFormat}.
     * @return This Builder.
     */
    public Builder setDataFormat(DataFormat dataFormat) {
      this.dataFormat = dataFormat;
      return this;
    }

    /**
     * Sets the {@link HardwareBufferSize} QAT uses.
     *
     * @param hwBufferSize the {@link HardwareBufferSize} for QAT.
     * @return This Builder.
     */
    public Builder setHardwareBufferSize(HardwareBufferSize hwBufferSize) {
      this.hwBufferSize = hwBufferSize;
      return this;
    }

    /**
     * Returns an instance of {@link QatZipper} created from the fields set on this builder.
     *
     * @return A QatZipper.
     */
    public QatZipper build() throws QatException {
      return new QatZipper(this);
    }

    @Override
    public String toString() {
      return "QatZipper{algorithm='"
          + algorithm
          + "', level="
          + level
          + ", mode="
          + mode
          + ", retryCount="
          + retryCount
          + ", pollingMode="
          + pollingMode
          + ", dataFormat='"
          + dataFormat
          + "', hardwareBufferSize="
          + hwBufferSize
          + "}";
    }

  private QatZipper(Builder builder) throws QatException {
    algorithm = builder.algorithm;
    level = builder.level;
    mode = builder.mode;
    retryCount = builder.retryCount;
    pollingMode = builder.pollingMode;
    dataFormat = builder.dataFormat;
    hwBufferSize = builder.hwBufferSize;

    if (retryCount < 0) throw new IllegalArgumentException("Invalid value for retry count.");

    if (algorithm == Algorithm.ZSTD) {
      zstdCompressCtx = new ZstdCompressCtx();
      zstdDecompressCtx = new ZstdDecompressCtx();
    }

    this.algorithm = algorithm;
    this.retryCount = retryCount;
    int status =
        InternalJNI.setup(
            this,
            algorithm.ordinal(),
            level,
            mode.ordinal(),
            pollingMode.ordinal(),
            dataFormat.ordinal(),
            hwBufferSize.getValue());

    if (algorithm == Algorithm.ZSTD) {
      final int QZ_OK = 0; // indicates that ZSTD can start QAT device
      if (mode == Mode.HARDWARE || (mode == Mode.AUTO && status == QZ_OK)) {
        // Only if mode is HARDWARE or AUTO with QAT device started
        zstdCompressCtx.registerSequenceProducer(new QatZstdSequenceProducer());
      }
      zstdCompressCtx.setLevel(level);
    }

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

    if (algorithm != Algorithm.ZSTD) {
      return InternalJNI.maxCompressedSize(session, len);
    }

    return (int) Zstd.compressBound(len);
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

    if (srcOffset < 0 || srcLen < 0 || srcOffset > src.length - srcLen)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bound.");

    if (dstOffset < 0 || dstLen < 0 || dstOffset > dst.length - dstLen)
      throw new ArrayIndexOutOfBoundsException("Destination offset is out of bound.");

    if (algorithm != Algorithm.ZSTD) {
      return compressByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    } else {
      bytesRead = bytesWritten = 0;
      int compressedSize =
          zstdCompressCtx.compressByteArray(dst, dstOffset, dstLen, src, srcOffset, srcLen);
      bytesWritten = compressedSize;
      bytesRead = srcLen;
      return compressedSize;
    }
  }

  private int compressByteArray(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
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

    if (algorithm != Algorithm.ZSTD) {
      return compressByteBuffer(src, dst);
    } else {
      // ZSTD treats the first parameter as the destination and the second as the source.
      return zstdCompressCtx.compress(dst, src);
    }
  }

  private int compressByteBuffer(ByteBuffer src, ByteBuffer dst) {
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

    if (srcOffset < 0 || srcLen < 0 || srcOffset > src.length - srcLen)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bound.");

    if (dstOffset < 0 || dstLen < 0 || dstOffset > dst.length - dstLen)
      throw new ArrayIndexOutOfBoundsException("Destination offset is out of bound.");

    if (algorithm != Algorithm.ZSTD) {
      return decompressByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
    } else {
      bytesRead = bytesWritten = 0;
      int decompressedSize =
          zstdDecompressCtx.decompressByteArray(dst, dstOffset, dstLen, src, srcOffset, srcLen);
      bytesWritten = decompressedSize;
      bytesRead = srcLen;
      return decompressedSize;
    }
  }

  private int decompressByteArray(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
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

    if (algorithm != Algorithm.ZSTD) {
      return decompressByteBuffer(src, dst);
    } else {
      // ZSTD treats the first parameter as the destination and the second as the source.
      if (!src.isDirect())
        throw new IllegalArgumentException(
            "Zstd-jni requires source buffers to be direct byte buffers.");
      return zstdDecompressCtx.decompress(dst, src);
    }
  }

  private int decompressByteBuffer(ByteBuffer src, ByteBuffer dst) {
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
   * Sets a checksum flag. Currently valid only for ZSTD.
   *
   * @param checksumFlag the checksum flag.
   * @throws UnsupportedOperationException if called for a compressor algorithm other than ZSTD.
   */
  public void setChecksumFlag(boolean checksumFlag) {
    if (algorithm != Algorithm.ZSTD)
      throw new UnsupportedOperationException(
          "Setting a checksum flag is currently valid only for ZSTD compressor.");
    zstdCompressCtx.setChecksum(checksumFlag);
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
