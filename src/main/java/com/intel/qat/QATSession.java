/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

// TODO: check if qzSession is bogus, do not allow it rather than throwing exception
// TODO: Exception messages with constants
/**
 * Defines APIs for creation of setting up hardware based QAT session(with or without software backup,
 * compression and decompression APIs
 */
public class QATSession {

  private static final int QZ_INIT = 0;
  private static final int QZ_CLOSE = 1;
  public static final  int DEFAULT_DEFLATE_COMP_LEVEL = 6;
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;
  public final static int DEFAULT_RETRY_COUNT = 0;

  private int qzStatus = QZ_CLOSE;
  private int retryCount;
  ByteBuffer unCompressedBuffer;
  ByteBuffer compressedBuffer;
  long qzSession;
  private Mode mode;
  private CompressionAlgorithm compressionAlgorithm;
  private int compressionLevel;

  /**
   * code paths for library. Currently, HARDWARE and AUTO(HARDWARE with SOFTWARE fallback) is supported
   */
  public static enum Mode{
    HARDWARE,
    AUTO;
  }

  /**
   * Compression Algorithm for library.
   */
  public static enum CompressionAlgorithm{
    DEFLATE,
    LZ4,
    ZSTD // TODO: if it is under release
  }

  /**
   * default constructor to assign default code path as AUTO(with software fallback option), no retries, ZLIB as default compression algo
   * and compression level 6 which is ZLIB default compression level
   */
  public QATSession(){
    this(CompressionAlgorithm.DEFLATE,DEFAULT_DEFLATE_COMP_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   *
   * @param compressionAlgorithm compression algorithm like LZ4, ZLIB,etc which are supported
   * @param compressionLevel compression level as per compression algorithm chosen
   */
  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel){
    this.mode = Mode.AUTO;
    this.retryCount = DEFAULT_RETRY_COUNT;
    this.compressionAlgorithm = compressionAlgorithm;
    this.compressionLevel = compressionLevel;

    if(!validateandResetParams())
      throw new IllegalArgumentException("Invalid parameters");

    setup();
  }

  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel, Mode mode){
    this.mode = mode;
    this.retryCount = DEFAULT_RETRY_COUNT;
    this.compressionAlgorithm = compressionAlgorithm;
    this.compressionLevel = compressionLevel;

    if(!validateandResetParams())
      throw new IllegalArgumentException("Invalid parameters");

    setup();
  }
  /**
   *
   * @param mode HARDWARE and auto
   * @param retryCount how many times a Hardware based compress/decompress call should be tried before failing compression/decompression
   * @param compressionAlgorithm compression algorithm like LZ4, ZLIB,etc which are supported
   * @param compressionLevel compression level as per compression algorithm chosen
   */
  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel, Mode mode, int retryCount){
    this.mode = mode;
    this.retryCount = retryCount;
    this.compressionAlgorithm = compressionAlgorithm;
    this.compressionLevel = compressionLevel;

    if(!validateandResetParams())
      throw new IllegalArgumentException("Invalid parameters");

    setup();
  }

  /**
   * setup API creates and stores QAT hardware session with or without software fallback and
   * assigns natively allocate PINNED memory of predefined sized length
   */

  private void setup() throws QATException{
    InternalJNI.setup(this,mode.ordinal(), DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, compressionAlgorithm.ordinal(), this.compressionLevel);
    qzStatus = QZ_INIT;
  }

  /**
   * teardown API destroys the QAT hardware session and free up resources and PINNED memory allocated with setup API call
   */
  public void teardown() throws QATException{
    if(qzStatus == QZ_CLOSE)
      throw new IllegalStateException();

    InternalJNI.teardown(qzSession, unCompressedBuffer, compressedBuffer);
    qzStatus = QZ_CLOSE;
  }

  /**
   * Provides maximum compression length (probable, not exact as this is decided after successful compression) for a given
   * source length
   * @param srcLen source length
   * @return maximum compressed length
   */
  public int maxCompressedLength(long srcLen){
    if(qzStatus == QZ_CLOSE) // do we need it here ? it increases path length
      throw new IllegalStateException();

    return InternalJNI.maxCompressedSize(qzSession, srcLen);
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer from a given destination offset
   * @param src source bytebuffer. This should be set in READ mode
   * @param dest destination bytebuffer. This should be set in WRITE mode
   * @return non-zero compressed size or throw QATException
   */

  public int compress(ByteBuffer src, ByteBuffer dest){ // keep the name compress and overload it for ByteBuffer and Bytearray
    if(qzStatus == QZ_CLOSE)
      throw new IllegalStateException(); // or UnsupportedOperationException ?

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;

    if (isPinnedMemory()) {
      compressedSize = compressByteBufferInLoop(src, dest);
    }
    else if (src.isReadOnly()) {
      ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());
      compressedSize = InternalJNI.compressByteBuff(qzSession, srcBuffer, 0, srcBuffer.limit(), dest, dest.position(), retryCount);
    } else {
      System.out.println("compressedByteBuff from "+ src.position() + " dest position "+ dest.position());
      compressedSize = InternalJNI.compressByteBuff(qzSession, src, src.position(), src.remaining(), dest, dest.position(), retryCount);
    }

    if (compressedSize < 0) {
      throw new QATException("QAT: Compression failed");
    }
    return compressedSize;
  }
  /**
   * compresses source bytearray from a given source offset till source length into destination bytearray
   * @param src source bytearray
   * @param srcOffset source offset
   * @param srcLen source length
   * @param dest destination bytearray
   * @return success or throws exception
   */

  public int compress( byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    if(qzStatus == QZ_CLOSE)
      throw new IllegalStateException();

    if(srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    int compressedSize = 0;

    if (isPinnedMemory()) {
      compressedSize = compressByteArrayInLoop(src, srcOffset, srcLen, dest, destOffset);
    } else {
      compressedSize = InternalJNI.compressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
    }

    if (compressedSize < 0) {
      throw new QATException("QAT: Compression failed");
    }

    return compressedSize;
  }
  /**
   * decompresses source bytebuffer into destination bytebuffer
   * @param src source bytebuffer. This should be in READ mode
   * @param dest destination bytebuffer. This should be in WRITE mode
   * @return success or throws exception
   */

  public int decompress (ByteBuffer src, ByteBuffer dest){ // keep the name decompress and overload it for ByteBuffer and Bytearray
    if(qzStatus == QZ_CLOSE)
      throw new IllegalStateException();

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();


    int decompressedSize = 0;

    if (isPinnedMemory()) {
      decompressedSize = decompressByteBufferInLoop(src, dest);
    }
    else if (src.isReadOnly()) {
      ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());

      decompressedSize = InternalJNI.decompressByteBuff(qzSession, srcBuffer, 0, srcBuffer.limit(), dest, 0, retryCount);
    } else {
      decompressedSize = InternalJNI.compressByteBuff(qzSession, src, src.position(), src.remaining(), dest, dest.position(), retryCount);
    }


    if (decompressedSize < 0) {
      throw new QATException("QAT: Compression failed");
    }
    return decompressedSize;
  }

  /**
   * decompresses source bytearray from a given source offset till source length into destination bytearray
   * @param src source bytearray
   * @param srcOffset source offset
   * @param srcLen source length
   * @param dest destination bytearray
   * @return success or throws exception
   */

  public int decompress(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    if(qzStatus == QZ_CLOSE)
      throw new IllegalStateException();

    if(srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    int decompressedSize = 0;

    if (isPinnedMemory()) {
      decompressedSize = decompressByteArrayInLoop(src,srcOffset,srcLen,dest,destOffset);
    } else {
      decompressedSize = InternalJNI.compressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
    }

    if (decompressedSize < 0) {
      throw new QATException("QAT: decompression failed");
    }

    return decompressedSize;
  }

  private boolean validateandResetParams(){
    if(retryCount < 0 || compressionLevel < 0)
      return false;

    if((compressionAlgorithm.ordinal() == 0 && compressionLevel > 9))
      return false;

    return true;
  }

  private boolean isPinnedMemory(){
    return (unCompressedBuffer != null && compressedBuffer != null);
  }

  private int compressByteBufferInLoop(ByteBuffer srcBuff, ByteBuffer destBuff){ // looping should be done in the C side
    int remaining = srcBuff.remaining();
    int sourceOffsetInLoop = srcBuff.position();
    int destOffsetInLoop = destBuff.position();
    int compressedSize = 0;
    int totalCompressedSize = 0;
    int unCompressedBufferLimit = unCompressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      int sourceLimit = Math.min(unCompressedBufferLimit,remaining);
      unCompressedBuffer.put(srcBuff.slice().limit(sourceLimit));
      unCompressedBuffer.flip();

      compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, 0, retryCount);
      compressedBuffer.limit(compressedSize);
      destBuff.put(compressedBuffer);
      totalCompressedSize += compressedSize;

      sourceOffsetInLoop += sourceLimit;
      srcBuff.position(sourceOffsetInLoop);
      destOffsetInLoop += compressedSize;
      remaining -= sourceLimit;
    }
    return totalCompressedSize;
  }

  private int compressByteArrayInLoop(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    int remaining = srcLen;
    int sourceOffsetInLoop = srcOffset;
    int destOffsetInLoop = destOffset;
    int compressedSize = 0;
    int totalCompressedSize = 0;
    int unCompressedBufferLimit = unCompressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      int sourceLimit = Math.min(unCompressedBufferLimit,remaining);
      unCompressedBuffer.put(src, sourceOffsetInLoop, sourceLimit);
      unCompressedBuffer.flip();

      compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, 0, retryCount);

      if(compressedSize < 0)
        throw new QATException("Compression Byte buffer fails");

      compressedBuffer.get(dest,destOffsetInLoop,compressedSize);

      totalCompressedSize += compressedSize;
      sourceOffsetInLoop += sourceLimit;
      destOffsetInLoop += compressedSize;
      remaining -= sourceLimit;
    }
    return totalCompressedSize;
  }

  private int decompressByteBufferInLoop(ByteBuffer srcBuff, ByteBuffer destBuff){
    int remaining = srcBuff.remaining();
    int sourceOffsetInLoop = srcBuff.position();
    int destOffsetInLoop = destBuff.position();
    int decompressedSize = 0;
    int totalDecompressedSize = 0;
    int compressedBufferLimit = compressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      int sourceLimit = Math.min(compressedBufferLimit,remaining);
      compressedBuffer.put(srcBuff.slice().limit(sourceLimit));
      compressedBuffer.flip();

      decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, compressedBufferLimit, unCompressedBuffer, 0, retryCount);

      if(decompressedSize < 0)
        throw new QATException("Decompression fails");

      unCompressedBuffer.limit(decompressedSize);
      destBuff.put(unCompressedBuffer);

      totalDecompressedSize += decompressedSize;
      sourceOffsetInLoop += sourceLimit;
      srcBuff.position(sourceOffsetInLoop);
      destOffsetInLoop += decompressedSize;
      remaining -= sourceLimit;
    }
    return totalDecompressedSize;
  }
  private int decompressByteArrayInLoop(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    int remaining = srcLen;
    int sourceOffsetInLoop = srcOffset;
    int destOffsetInLoop = destOffset;
    int decompressedSize = 0;
    int totalDecompressedSize = 0;
    int compressedBufferLimit = compressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      int sourceLimit = Math.min(compressedBufferLimit, remaining);
      compressedBuffer.put(src, sourceOffsetInLoop, sourceLimit);
      compressedBuffer.flip();

      decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, sourceLimit, unCompressedBuffer, 0, retryCount);

      if(decompressedSize < 0)
        throw new QATException("Decompression fails");

      unCompressedBuffer.get(dest,destOffsetInLoop,decompressedSize);

      totalDecompressedSize += decompressedSize;
      sourceOffsetInLoop += sourceLimit;
      destOffsetInLoop += decompressedSize;
      remaining -= sourceLimit;
    }
    return totalDecompressedSize;
  }
}
