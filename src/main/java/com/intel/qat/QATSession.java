/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;

// TODO: check if qzSession is bogus, do not allow it rather than throwing exception
// TODO: Exception messages with constants
/**
 * Defines APIs for creation of setting up hardware based QAT session(with or without software backup,
 * compression and decompression APIs
 */
public class QATSession {

  public static final  int DEFAULT_DEFLATE_COMP_LEVEL = 6;
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;
  public final static int DEFAULT_RETRY_COUNT = 0;

  private boolean isValid;
  private int retryCount;
  ByteBuffer unCompressedBuffer;
  ByteBuffer compressedBuffer;
  long qzSession;
  private Mode mode;
  private CompressionAlgorithm compressionAlgorithm;
  private int compressionLevel;
  // visible for testing only annotation
  boolean isPinnedMemAvailable;

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
   */

  // one more constructor with ONLY compression algorithm
  public QATSession(CompressionAlgorithm compressionAlgorithm){
    this(compressionAlgorithm,DEFAULT_DEFLATE_COMP_LEVEL,Mode.AUTO,DEFAULT_RETRY_COUNT);
  }
  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel){// use this
    this(compressionAlgorithm,compressionLevel,Mode.AUTO,DEFAULT_RETRY_COUNT);
  }

  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel, Mode mode){
    this(compressionAlgorithm,compressionLevel,mode,DEFAULT_RETRY_COUNT);
  }
  /**
   *
   * @param mode HARDWARE and auto
   * @param retryCount how many times a Hardware based compress/decompress call should be tried before failing compression/decompression
   * @param compressionAlgorithm compression algorithm like LZ4, ZLIB,etc which are supported
   * @param compressionLevel compression level as per compression algorithm chosen
   */
  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel, Mode mode, int retryCount){
    if(!validateParams(compressionAlgorithm, compressionLevel, retryCount))
      throw new IllegalArgumentException("Invalid parameters");

    this.mode = mode;
    this.retryCount = retryCount;
    this.compressionAlgorithm = compressionAlgorithm;
    this.compressionLevel = compressionLevel;
    this.isPinnedMemAvailable = true;
    setup();
  }

  /**
   * setup API creates and stores QAT hardware session with or without software fallback and
   * assigns natively allocate PINNED memory of predefined sized length
   */

  private void setup() throws QATException{
    InternalJNI.setup(this,mode.ordinal(), DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, compressionAlgorithm.ordinal(), this.compressionLevel);
    isValid = true;
  }

  /**
   * teardown API destroys the QAT hardware session and free up resources and PINNED memory allocated with setup API call
   */
  public void teardown() throws QATException{ // change name to cleanUp
    if(!isValid)
      throw new IllegalStateException();

    InternalJNI.teardown(qzSession, unCompressedBuffer, compressedBuffer);
    isValid = false;
  }

  //static teardown with three long params - qzsession and other 2 pinned buffers addresses

  /**
   * Provides maximum compression length (probable, not exact as this is decided after successful compression) for a given
   * source length
   * @param srcLen source length
   * @return maximum compressed length
   */
  public int maxCompressedLength(long srcLen){
    if(!isValid) // do we need it here ? it increases path length
      throw new IllegalStateException();

    return InternalJNI.maxCompressedSize(qzSession, srcLen);
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer from a given destination offset
   * @param src source bytebuffer. This should be set in READ mode
   * @param dest destination bytebuffer. This should be set in WRITE mode
   * @return non-zero compressed size or throw QATException
   */

  public int compress(ByteBuffer src, ByteBuffer dest){ //unCompressedBuffer.put(src) -> compressedBuffer -> copies to dest
    if(!isValid)
      throw new IllegalStateException();

    if ((src == null || dest == null) || (src.position() == src.limit() || dest.position() == dest.limit()))
      throw new IllegalArgumentException(); // check if this exception is correct if empty buffers are provided

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;

    if (isPinnedMemory()) {
      compressedSize = compressByteBufferInLoop(src, dest);
    }
    else if(src.isDirect() && dest.isDirect()){
      compressedSize = InternalJNI.compressByteBuff(qzSession, src, src.position(), src.remaining(), dest, retryCount);
    } else if (src.hasArray() && dest.hasArray()) {
      byte[] destArray = new byte[dest.remaining()];
      compressedSize = InternalJNI.compressByteArray(qzSession, src.array(), src.position(), src.remaining(),dest.array(),0,retryCount);
      dest.put(destArray, 0, compressedSize);
    }
    else{
      byte[] srcArray = new byte[src.remaining()];
      byte[] destArray = new byte[dest.remaining()];
      src.get(srcArray, src.position(), src.remaining());
      compressedSize = InternalJNI.compressByteArray(qzSession, srcArray, 0, srcArray.length, destArray, 0, retryCount);
      dest.put(destArray,dest.position(),compressedSize);
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
    if(!isValid)
      throw new IllegalStateException();

    if(src == null || dest == null || srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    // add logic to validate srcOffset + srcLen > src.length, ArrayIndexOutOfBoundException
    // srcOffset < 0 ArrayIndexOutOfBoundException
    // srcOffset >= src.length ArrayIndexOutOfBoundException

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
    if(!isValid)
      throw new IllegalStateException();

    if ((src == null || dest == null) || (src.position() == src.limit() || dest.position() == dest.limit()))
      throw new IllegalArgumentException(); // check if this exception is correct if empty buffers are provided

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;

    if (isPinnedMemory() && src.isDirect() && dest.isDirect()) {
      decompressedSize = decompressByteBufferInLoop(src, dest);
    }
    else if(src.isDirect() && dest.isDirect()){
      decompressedSize = InternalJNI.decompressByteBuff(qzSession, src, src.position(), src.remaining(), dest, retryCount);
    } else if (src.hasArray() && dest.hasArray()) {
      byte[] destArray = new byte[dest.remaining()];
      decompressedSize = InternalJNI.decompressByteArray(qzSession, src.array(), 0, src.remaining(),destArray,0,retryCount);
      dest.put(destArray, 0, decompressedSize);
    }
    else {
      byte[] srcArray = new byte[src.remaining()];
      byte[] destArray = new byte[dest.remaining()];
      src.get(srcArray, src.position(), src.remaining());
      decompressedSize = InternalJNI.decompressByteArray(qzSession, srcArray, 0, srcArray.length, destArray, 0, retryCount);
      dest.put(destArray,dest.position(),decompressedSize);
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
    if(!isValid)
      throw new IllegalStateException();

    if(src == null || dest == null || srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    // add logic to validate srcOffset + srcLen > src.length, ArrayIndexOutOfBoundException
    // srcOffset < 0 ArrayIndexOutOfBoundException
    // srcOffset >= src.length ArrayIndexOutOfBoundException

    int decompressedSize = 0;

    if (isPinnedMemory()) {
      decompressedSize = decompressByteArrayInLoop(src,srcOffset,srcLen,dest,destOffset);
    } else {
      decompressedSize = InternalJNI.decompressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
    }

    if (decompressedSize < 0) {
      throw new QATException("QAT: decompression failed");
    }

    return decompressedSize;
  }

  private boolean validateParams(CompressionAlgorithm compressionAlgorithm,int compressionLevel, int retryCount){
    return !(retryCount < 0 || compressionLevel < 0 || (compressionAlgorithm.ordinal() == 0 && compressionLevel > 9));
  }

  private boolean isPinnedMemory(){
    return (isPinnedMemAvailable && unCompressedBuffer != null && compressedBuffer != null);
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

      compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, retryCount);
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

      compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, retryCount);

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

      decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, compressedBufferLimit, unCompressedBuffer, retryCount);

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

      decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, sourceLimit, unCompressedBuffer, retryCount);

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
