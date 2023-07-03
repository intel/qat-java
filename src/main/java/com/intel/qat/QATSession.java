/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;

// TODO: Exception messages with constants
/**
 * Defines APIs for creation of setting up hardware based QAT session(with or without software backup,
 * compression and decompression APIs
 */
public class QATSession {

  /**
   * Default compression is set to 6, this is to align with default compression mode chosen as ZLIB
   */
  public static final  int DEFAULT_DEFLATE_COMP_LEVEL = 6;
  /**
   * Default PINNED memory allocated is set as 480 KB. This accelerates HW based compression/decompression
   */
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;
  /**
   * If retryCount is set as 0, it means no retries in compress/decompress, non-zero value means there will be retries
   */
  public final static int DEFAULT_RETRY_COUNT = 0;

  private boolean isValid;
  private int retryCount;
  ByteBuffer unCompressedBuffer;
  ByteBuffer compressedBuffer;
  long qzSession;
  private Mode mode;
  private CompressionAlgorithm compressionAlgorithm;
  private int compressionLevel;
  private boolean isPinnedMemAvailable;

  /**
   * code paths for library. Currently, HARDWARE and AUTO(HARDWARE with SOFTWARE fallback) is supported
   */
  public static enum Mode{
    /**
     * Hardware ONLY Path, QAT session fails if this is chosen and HW is not available
     */
    HARDWARE,

    /**
     * Hardware based QAT session optionally switches to software in case of HW failure
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
   * default constructor to assign default code path as AUTO(with software fallback option), no retries, ZLIB as default compression algo
   * and compression level 6 which is ZLIB default compression level
   */
  public QATSession(){
    this(CompressionAlgorithm.DEFLATE,DEFAULT_DEFLATE_COMP_LEVEL, Mode.AUTO, DEFAULT_RETRY_COUNT);
  }

  /**
   * Sets defined compression algroithm, others are set to their respective default value
   * @param compressionAlgorithm CompressionAlgorithm enum value
   */
  public QATSession(CompressionAlgorithm compressionAlgorithm){
    this(compressionAlgorithm,DEFAULT_DEFLATE_COMP_LEVEL,Mode.AUTO,DEFAULT_RETRY_COUNT);
  }

  /**
   * Sets defined Compression algorithm along with compression level, others are set to their default value
   * @param compressionAlgorithm CompressionAlgorithm enum value
   * @param compressionLevel Compression Level
   */
  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel){
    this(compressionAlgorithm,compressionLevel,Mode.AUTO,DEFAULT_RETRY_COUNT);
  }

  /**
   * Sets defined Compression algorithm along with compression level and chosen code path mode from Mode enum, others are set to their default value
   * @param compressionAlgorithm CompressionAlgorithm enum value
   * @param compressionLevel Compression Level
   * @param mode Mode enum value
   */
  public QATSession( CompressionAlgorithm compressionAlgorithm,  int compressionLevel, Mode mode){
    this(compressionAlgorithm,compressionLevel,mode,DEFAULT_RETRY_COUNT);
  }
  /**
   * sets the parameter supplied by user or through other constructor with varying params
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
    if(unCompressedBuffer == null || compressedBuffer == null)
      System.out.println("------------ DEBUG: PINNED MEMORY WAS NOT ALLOCATED -----------");
    isValid = true;
  }

  /**
   * teardown API destroys the QAT hardware session and free up resources and PINNED memory allocated with setup API call
   */
  void teardown() throws QATException{
    if(!isValid)
      throw new IllegalStateException();
    InternalJNI.teardown(qzSession, unCompressedBuffer, compressedBuffer);
    isValid = false;
  }

  /**
   * Provides maximum compression length (probable, not exact as this is decided after successful compression) for a given
   * source length
   * @param srcLen source length
   * @return maximum compressed length
   */
  public int maxCompressedLength(long srcLen){
    if(!isValid)
      throw new IllegalStateException();

    return InternalJNI.maxCompressedSize(qzSession, srcLen);
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer from a given destination offset
   * @param src source bytebuffer. This should be set in READ mode
   * @param dest destination bytebuffer. This should be set in WRITE mode
   * @return non-zero compressed size or throw QATException
   */

  public int compress(ByteBuffer src, ByteBuffer dest){
    if(!isValid)
      throw new IllegalStateException();

    if ((src == null || dest == null) || (src.position() == src.limit() || dest.position() == dest.limit()))
      throw new IllegalArgumentException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;

    if (isPinnedMemory()) {
      compressedSize = compressByteBufferInLoop(src, dest);
    }
    else if(src.isDirect() && dest.isDirect()){
      compressedSize = InternalJNI.compressByteBuff(qzSession, src, src.position(), src.remaining(), dest, retryCount,1);
      dest.position(compressedSize);
    } else if (src.hasArray() && dest.hasArray()) {

      byte[] destArray = new byte[dest.remaining()];
      compressedSize = InternalJNI.compressByteArray(qzSession, src.array(), src.position(), src.remaining(),destArray,0,retryCount,1);
      dest.put(destArray, 0, compressedSize);
    }
    else{
      byte[] srcArray = new byte[src.remaining()];
      byte[] destArray = new byte[dest.remaining()];
      src.get(srcArray, src.position(), src.remaining());
      compressedSize = InternalJNI.compressByteArray(qzSession, srcArray, 0, srcArray.length, destArray, 0, retryCount,1);
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
   * @param destOffset destination offset
   * @return success or throws exception
   */

  public int compress( byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    if(!isValid)
      throw new IllegalStateException();

    if(src == null || dest == null || srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    if(srcOffset < 0 || (srcOffset + srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Invalid byte array index");

    int compressedSize = 0;

    if (isPinnedMemory()) {
      compressedSize = compressByteArrayInLoop(src, srcOffset, srcLen, dest, destOffset);
    } else {
      compressedSize = InternalJNI.compressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount,1);
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

  public int decompress (ByteBuffer src, ByteBuffer dest){
    if(!isValid)
      throw new IllegalStateException();

    if ((src == null || dest == null) || (src.position() == src.limit() || dest.position() == dest.limit()))
      throw new IllegalArgumentException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    int[] result = new int[2];

    if (isPinnedMemory()) {
      if(src.isDirect() && dest.isDirect()) {
        result = InternalJNI.decompressByteBuffInLoop(qzSession, src, src.position(), src.remaining(), compressedBuffer, compressedBuffer.limit(), unCompressedBuffer, unCompressedBuffer.limit(), dest, dest.position(), dest.remaining(), retryCount);
        System.out.println(" source modified : " + result[0] + " destination modified at "+result[1]);
        dest.position(dest.position() + result[1]);
        src.position(src.position() + result[0]);
        return result[1];
      }
      else if (src.hasArray() && dest.hasArray()) {
        byte[] destArray = dest.array();
        result = InternalJNI.decompressByteArrayInLoop(qzSession,src.array(),src.position(),src.remaining(),compressedBuffer, compressedBuffer.limit(), unCompressedBuffer, unCompressedBuffer.limit(),destArray,dest.position(), dest.remaining(),retryCount);
        src.position(src.position() + result[0]);
        dest.put(destArray,0, result[1]);
        //dest.position(result[1]);
        return result[1];
      }
      else{
          byte[] srcArray = new byte[src.remaining()];
          src.get(srcArray, src.position(), src.remaining());
          result = InternalJNI.decompressByteArrayInLoop(qzSession, srcArray, 0, srcArray.length, compressedBuffer, compressedBuffer.limit(), unCompressedBuffer, unCompressedBuffer.limit(), dest.array(), dest.position(), dest.remaining(),retryCount);
          src.position(src.position() + result[0]);
          dest.position(dest.position() + result[1]);
          return result[1];
      }
    }
    else if(src.isDirect() && dest.isDirect()){
      decompressedSize = InternalJNI.decompressByteBuff(qzSession, src, src.position(), src.remaining(), dest, retryCount);
      dest.position(decompressedSize);
    } else if (src.hasArray() && dest.hasArray()) {
      byte[] destArray = new byte[dest.remaining()];
      decompressedSize = InternalJNI.decompressByteArray(qzSession, src.array(), src.position(), src.remaining(),destArray,0,retryCount);
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
   * @param destOffset destination offset
   * @return success or throws exception
   */

  public int decompress(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    if(!isValid)
      throw new IllegalStateException();

    if(src == null || dest == null || srcLen == 0 || dest.length == 0)
      throw new IllegalArgumentException("empty buffer");

    if(srcOffset < 0 || (srcOffset + srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Invalid byte array index");

    int decompressedSize = 0;
    int[] result = new int[2];

    if (isPinnedMemory()) {
      result = InternalJNI.decompressByteArrayInLoop(qzSession,src,srcOffset,srcLen,compressedBuffer, compressedBuffer.limit(), unCompressedBuffer, unCompressedBuffer.limit(), dest,destOffset,dest.length,retryCount);
      return result[1];
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

  void setIsPinnedMemAvailable(){
    this.isPinnedMemAvailable = false;
  }
  private int compressByteBufferInLoop(ByteBuffer srcBuff, ByteBuffer destBuff){
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

      if(remaining <= sourceLimit) {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, retryCount, 1);
      }
      else {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, retryCount, 0);
      }
      compressedBuffer.limit(compressedSize);
      destBuff.put(compressedBuffer);
      totalCompressedSize += compressedSize;

      sourceOffsetInLoop += sourceLimit;
      srcBuff.position(sourceOffsetInLoop);
      destOffsetInLoop += compressedSize;
      remaining -= sourceLimit;
    }
    unCompressedBuffer.clear();
    compressedBuffer.clear();
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

      if(remaining <= sourceLimit) {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, retryCount, 1);
      }
      else{
          compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, retryCount, 0);
      }

      if(compressedSize < 0)
        throw new QATException("Compression Byte buffer fails");

      compressedBuffer.get(dest,destOffsetInLoop,compressedSize);
      totalCompressedSize += compressedSize;
      sourceOffsetInLoop += sourceLimit;
      destOffsetInLoop += compressedSize;
      remaining -= sourceLimit;
      compressedBuffer.position(destOffsetInLoop);
    }

    unCompressedBuffer.clear();
    compressedBuffer.clear();
    return totalCompressedSize;
  }

  static void cleanUp(long qzSessionReference, ByteBuffer unCompressedBufferReference, ByteBuffer compressedBufferReference){
    InternalJNI.teardown(qzSessionReference,unCompressedBufferReference,compressedBufferReference);
  }

  /**
   * This method is called by GC when doing cleaning action
   * @return Runnable to be used in cleaner register
   */
  public Runnable cleanningAction(){
    return new QATSessionCleaner(qzSession,unCompressedBuffer,compressedBuffer);
  }

  static class QATSessionCleaner implements Runnable{
    private long qzSession;
    private ByteBuffer unCompressedBuffer;
    private ByteBuffer compressedBuffer;

    public QATSessionCleaner(long qzSession, ByteBuffer unCompressedBuffer, ByteBuffer compressedBuffer){
      this.qzSession = qzSession;
      this.unCompressedBuffer = unCompressedBuffer;
      this.compressedBuffer = compressedBuffer;
    }
    @Override
    public void run(){
        if(qzSession != 0){
          cleanUp(qzSession,unCompressedBuffer,compressedBuffer);
          qzSession = 0;
          unCompressedBuffer = null;
          compressedBuffer = null;
        }
        else{
          System.out.println("DEBUGGING : Cleaner called more than once");
        }
    }
  }
}
