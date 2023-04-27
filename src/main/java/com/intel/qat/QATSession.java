/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * Defines APIs for creation of setting up hardware based QAT session(with or without software backup,
 * compression and decompression APIs
 */
public class QATSession {

  private final int QZ_OK = 0;

  private final int MAX_RETRY_COUNT = 10;

  private final int DEFAULT_DEFLATE_COMP_LEVEL = 6;
  private int qzStatus = Integer.MIN_VALUE;
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;

  private int retryCount;

  ByteBuffer unCompressedBuffer;
  ByteBuffer compressedBuffer;

  long qzSession;

  private QATUtils.ExecutionPaths executionPath;

  private QATUtils.CompressionAlgo compressionAlgo;

  private int compressionLevel;

  /**
   * default constructor to assign default code path as AUTO(with software fallback option), no retries, ZLIB as default compression algo
   * and compression level 6 which is ZLIB default compression level
   */
  public QATSession(){
    this(QATUtils.ExecutionPaths.AUTO, 0,QATUtils.CompressionAlgo.DEFLATE,6);
  }

  /**
   *
   * @param executionPath HARDWARE with no fallback option
   * @param retryCount how many times a Hardware based compress/decompress call should be tried before failing compression/decompression
   * @param compressionAlgo compression algorithm like LZ4, ZLIB,etc which are supported
   * @param compressionLevel compression level as per compression algorithm chosen
   */
  public QATSession(QATUtils.ExecutionPaths executionPath, int retryCount, QATUtils.CompressionAlgo compressionAlgo, int compressionLevel){
    validateandResetParams(retryCount, compressionLevel);

    this.executionPath = executionPath;
    this.retryCount = retryCount;
    this.compressionAlgo = compressionAlgo;
    this.compressionLevel = compressionLevel;

    setup();
  }

  /**
   * setup API creates and stores QAT hardware session with or without software fallback and
   * assigns natively allocate PINNED memory of predefined sized length
   */

  private void setup() {
    try {
      if (executionPath.getExecutionPathCode() == QATUtils.ExecutionPaths.AUTO.getExecutionPathCode()) {
        setupAUTO();
      } else {
        setupHardware();
      }
    }
    catch (QATException qe){
      throw new QATException(qe.getMessage());
    }
  }

  /**
   * Setup QAT software session with Java allocated byte buffers
   */
  private void setupAUTO(){
    try {
      InternalJNI.setup(this,QATUtils.ExecutionPaths.AUTO.getExecutionPathCode(),DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, this.compressionAlgo.getCompressionAlgorithm(), this.compressionLevel);
    }
    catch (Exception e){
      throw new QATException(e.getMessage());
    }

    if(partialPinnedMem()){
      try {
        teardown();
      }
      catch (QATException qe){
        throw new QATException("teardown failed");
      }
    }
  }

  /**
   * setup QAT hardware session with PINNED memory & assign this nativbe memory to Java bytebuffers
   */

  private void setupHardware(){
    try{
      InternalJNI.setup(this, QATUtils.ExecutionPaths.HARDWARE.getExecutionPathCode(), DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, this.compressionAlgo.getCompressionAlgorithm(), this.compressionLevel);
    }
    catch (Exception e){
      throw new QATException(e.getMessage());
    }

    if(partialPinnedMem()){
      try {
        teardown();
      }
      catch (QATException e) {
        throw new QATException("PINNED memory not available");
      }
    }
  }
  /**
   * teardown API destroys the QAT hardware session and free up resources and PINNED memory allocated with setup API call
   */
  public void teardown() {
    int r = InternalJNI.teardown(qzSession, unCompressedBuffer, compressedBuffer);
    if (r != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(r));
    }
  }

  /**
   * Provides maximum compression length (probable, not exact as this is decided after successful compression) for a given
   * source length
   * @param srcLen source length
   * @return maximum compressed length
   */
  public int maxCompressedLength(long srcLen) {
    return InternalJNI.maxCompressedSize(qzSession, srcLen);
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer from a given destination offset
   * @param src source bytebuffer. This should be set in READ mode
   * @param dest destination bytebuffer. This should be set in WRITE mode
   * @return success or exception thrown
   */

  public int compressByteBuff(ByteBuffer src, ByteBuffer dest) {

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;
    try {

      if (ifPinnedMem()) {
        compressByteBufferInLoop(src, dest);
      }
      else if (src.isReadOnly()) {
        ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());
        compressedSize = InternalJNI.compressByteBuff(qzSession, srcBuffer, 0, srcBuffer.limit(), dest, dest.position(), retryCount);
      } else {
        compressedSize = InternalJNI.compressByteBuff(qzSession, src, src.position(), src.remaining(), dest, dest.position(), retryCount);
      }
    }
    catch (QATException qe){
      throw new QATException(qe.getMessage());
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

  public int compressByteArray ( byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset)
  {

    if(srcLen == 0 || dest.length == 0)
      throw new QATException("empty buffer");

    int compressedSize = 0;

    try {
      if (ifPinnedMem()) {
        compressByteArrayInLoop(src, srcOffset, srcLen, dest, destOffset);
      } else {
        compressedSize = InternalJNI.compressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
      }
    }catch (QATException qe){
      throw new QATException(qe.getMessage());
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

  public int decompressByteBuff (ByteBuffer src, ByteBuffer dest){

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    try {
      if (ifPinnedMem()) {
        decompressByteBufferInLoop(src, dest);
      }
      else if (src.isReadOnly()) {
        ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());

        decompressedSize = InternalJNI.decompressByteBuff(qzSession, srcBuffer, 0, srcBuffer.limit(), dest, 0, retryCount);
      } else {
        decompressedSize = InternalJNI.compressByteBuff(qzSession, src, src.position(), src.remaining(), dest, dest.position(), retryCount);
      }
    }
    catch (QATException qe){
      throw new QATException(qe.getMessage());
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

  public int decompressByteArray(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    if(srcLen == 0 || dest.length == 0)
      throw new QATException("empty buffer");

    int decompressedSize = 0;

    try {
      if (ifPinnedMem()) {
        decompressByteArrayInLoop(src,srcOffset,srcLen,dest,destOffset);
      } else {
        decompressedSize = InternalJNI.compressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
      }
    }catch (QATException qe){
      throw new QATException(qe.getMessage());
    }

    if (decompressedSize < 0) {
      throw new QATException("QAT: Compression failed");
    }

    return decompressedSize;
  }

  private void validateandResetParams (int retryCount, int compressionLevel){
    if(retryCount > MAX_RETRY_COUNT)
      retryCount = MAX_RETRY_COUNT;

    if(compressionLevel > 9 && compressionAlgo.getCompressionAlgorithm() == 0) { // DEFLATE
      compressionLevel = DEFAULT_DEFLATE_COMP_LEVEL;
    }
  }

  private boolean ifPinnedMem() {
    return (unCompressedBuffer != null && compressedBuffer != null);
  }

  private boolean partialPinnedMem() {
    return (unCompressedBuffer == null || compressedBuffer == null);
  }


  private void copyByteBuffToDestination (ByteBuffer compressedBuffer, ByteBuffer dest,int compressedSize){
    if (compressedSize < 0)
      throw new QATException("compressed size is negative");

    try {
      dest.put(compressedBuffer.array(), compressedBuffer.position(), compressedSize);
    } catch (Exception e) {
      throw new QATException(e.getMessage());
    }
  }

  private void compressByteBufferInLoop(ByteBuffer srcBuff, ByteBuffer destBuff){
    int remaining = srcBuff.remaining();
    int sourceOffsetInLoop = srcBuff.position();
    int destOffsetInLoop = destBuff.position();
    int compressedSize = 0;
    int unCompressedBufferLimit = unCompressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();

      unCompressedBuffer.put(srcBuff.array(), sourceOffsetInLoop, unCompressedBufferLimit);
      unCompressedBuffer.flip();

      try {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, unCompressedBufferLimit, compressedBuffer, 0, retryCount);
        compressedBuffer.flip();
        destBuff.put(compressedBuffer.array(), destOffsetInLoop, compressedSize);
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += unCompressedBufferLimit;
      destOffsetInLoop += compressedSize;
      remaining -= unCompressedBufferLimit;
    }
  }

  private void compressByteArrayInLoop(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    int remaining = srcLen;
    int sourceOffsetInLoop = srcOffset;
    int destOffsetInLoop = destOffset;
    int compressedSize = 0;
    int unCompressedBufferLimit = unCompressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();

      unCompressedBuffer.put(src, sourceOffsetInLoop, unCompressedBufferLimit);
      unCompressedBuffer.flip();

      try {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, unCompressedBufferLimit, compressedBuffer, 0, retryCount);
        compressedBuffer.flip();
        compressedBuffer.get(dest,destOffsetInLoop,compressedSize);
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += unCompressedBufferLimit;
      destOffsetInLoop += compressedSize;
      remaining -= unCompressedBufferLimit;
    }
  }

  private void decompressByteBufferInLoop(ByteBuffer srcBuff, ByteBuffer destBuff){
    int remaining = srcBuff.remaining();
    int sourceOffsetInLoop = srcBuff.position();
    int destOffsetInLoop = destBuff.position();
    int decompressedSize = 0;
    int compressedBufferLimit = compressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();

      compressedBuffer.put(srcBuff.array(), sourceOffsetInLoop, compressedBufferLimit);
      compressedBuffer.flip();

      try {
        decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, compressedBufferLimit, unCompressedBuffer, 0, retryCount);
        unCompressedBuffer.flip();
        destBuff.put(unCompressedBuffer.array(), destOffsetInLoop, decompressedSize);
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += compressedBufferLimit;
      destOffsetInLoop += decompressedSize;
      remaining -= compressedBufferLimit;
    }
  }
  private void decompressByteArrayInLoop(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset){
    int remaining = srcLen;
    int sourceOffsetInLoop = srcOffset;
    int destOffsetInLoop = destOffset;
    int compressedSize = 0;
    int compressedBufferLimit = compressedBuffer.limit();

    while (remaining > 0) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();

      compressedBuffer.put(src, sourceOffsetInLoop, compressedBufferLimit);
      compressedBuffer.flip();

      try {
        compressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, compressedBufferLimit, unCompressedBuffer, 0, retryCount);
        unCompressedBuffer.flip();
        unCompressedBuffer.get(dest,destOffsetInLoop,compressedSize);
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += compressedBufferLimit;
      destOffsetInLoop += compressedSize;
      remaining -= compressedBufferLimit;
    }
  }
}
