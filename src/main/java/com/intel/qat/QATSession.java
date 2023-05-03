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

/**
 * Defines APIs for creation of setting up hardware based QAT session(with or without software backup,
 * compression and decompression APIs
 */
public class QATSession {

  private final int QZ_OK = 0;

  private final int MAX_RETRY_COUNT = 10;

  private final static int DEFAULT_DEFLATE_COMP_LEVEL = 6;
  private int qzStatus = Integer.MIN_VALUE;
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;

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
    ZSTD
  }

  /**
   * default constructor to assign default code path as AUTO(with software fallback option), no retries, ZLIB as default compression algo
   * and compression level 6 which is ZLIB default compression level
   */
  public QATSession(){
    this(Mode.AUTO, 0,CompressionAlgorithm.DEFLATE,DEFAULT_DEFLATE_COMP_LEVEL);
  }

  /**
   *
   * @param mode HARDWARE and auto
   * @param retryCount how many times a Hardware based compress/decompress call should be tried before failing compression/decompression
   * @param compressionAlgorithm compression algorithm like LZ4, ZLIB,etc which are supported
   * @param compressionLevel compression level as per compression algorithm chosen
   */
  public QATSession(Mode mode, int retryCount, CompressionAlgorithm compressionAlgorithm, int compressionLevel){
    // CompressionAlgo, level, execution path

    this.mode = mode;
    this.retryCount = retryCount;
    this.compressionAlgorithm = compressionAlgorithm;
    this.compressionLevel = compressionLevel;

    validateandResetParams(retryCount, compressionLevel);
    setup();
  }

  /**
   * setup API creates and stores QAT hardware session with or without software fallback and
   * assigns natively allocate PINNED memory of predefined sized length
   */

  private void setup() throws QATException{
    if (mode.ordinal() == Mode.AUTO.ordinal()) {
      InternalJNI.setup(this,Mode.AUTO.ordinal(), DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, compressionAlgorithm.ordinal(), this.compressionLevel);
    } else {
      InternalJNI.setup(this, Mode.HARDWARE.ordinal(), DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, compressionAlgorithm.ordinal(), this.compressionLevel);
    }

  }

  /**
   * teardown API destroys the QAT hardware session and free up resources and PINNED memory allocated with setup API call
   */
  public void teardown() throws QATException{
    System.out.println("Java: teardown");
    InternalJNI.teardown(qzSession, unCompressedBuffer, compressedBuffer);
  }

  /**
   * Provides maximum compression length (probable, not exact as this is decided after successful compression) for a given
   * source length
   * @param srcLen source length
   * @return maximum compressed length
   */
  public int maxCompressedLength(long srcLen) throws QATException{
    return InternalJNI.maxCompressedSize(qzSession, srcLen); // exceptions
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer from a given destination offset
   * @param src source bytebuffer. This should be set in READ mode
   * @param dest destination bytebuffer. This should be set in WRITE mode
   * @return success or exception thrown
   */

  public int compressByteBuff(ByteBuffer src, ByteBuffer dest) throws QATException, BufferOverflowException, ReadOnlyBufferException{

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;

    if (ifPinnedMem()) {
      compressedSize = compressByteBufferInLoop(src, dest);
    }
    else if (src.isReadOnly()) {
      ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());
      compressedSize = InternalJNI.compressByteBuff(qzSession, srcBuffer, 0, srcBuffer.limit(), dest, dest.position(), retryCount);
    } else {
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

  public int compressByteArray ( byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset) throws QATException {

    if(srcLen == 0 || dest.length == 0)
      throw new QATException("empty buffer");

    int compressedSize = 0;

    if (ifPinnedMem()) {
      System.out.println("Pinned memory is available");
      compressedSize = compressByteArrayInLoop(src, srcOffset, srcLen, dest, destOffset);
    } else {
      System.out.println("Pinned memory is NOT available");
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

  public int decompressByteBuff (ByteBuffer src, ByteBuffer dest) throws QATException, BufferOverflowException, ReadOnlyBufferException{

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    try {
      if (ifPinnedMem()) {
        decompressedSize = decompressByteBufferInLoop(src, dest);
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

  public int decompressByteArray(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset) throws QATException{
    if(srcLen == 0 || dest.length == 0)
      throw new QATException("empty buffer");

    int decompressedSize = 0;

    if (ifPinnedMem()) {
      decompressedSize = decompressByteArrayInLoop(src,srcOffset,srcLen,dest,destOffset);
    } else {
      decompressedSize = InternalJNI.compressByteArray(qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
    }

    if (decompressedSize < 0) {
      throw new QATException("QAT: decompression failed");
    }

    return decompressedSize;
  }

  private void validateandResetParams (int retryCount, int compressionLevel){
    if(retryCount > MAX_RETRY_COUNT)
      retryCount = MAX_RETRY_COUNT;

    if(compressionLevel > 9 && compressionAlgorithm.ordinal() == 0) { // DEFLATE
      compressionLevel = DEFAULT_DEFLATE_COMP_LEVEL;
    }

    if(compressionAlgorithm.ordinal() != 0 || compressionAlgorithm.ordinal() != 1){
      compressionAlgorithm = CompressionAlgorithm.DEFLATE;
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
      int sourceLimit = (unCompressedBufferLimit <= remaining)? unCompressedBufferLimit:remaining;
      unCompressedBuffer.put(srcBuff.array(), sourceOffsetInLoop, unCompressedBufferLimit);
      unCompressedBuffer.flip();

      try {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, 0, retryCount);
        compressedBuffer.flip();
        destBuff.put(compressedBuffer.array(), destOffsetInLoop, compressedSize);
        totalCompressedSize += compressedSize;
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += sourceLimit;
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

      try {
        compressedSize = InternalJNI.compressByteBuff(qzSession, unCompressedBuffer, 0, sourceLimit, compressedBuffer, 0, retryCount);
        System.out.println("the position of compressed buffer "+ compressedBuffer.position());
        System.out.println("compressed size is "+ compressedSize);
        if(compressedSize < 0)
          throw new QATException("Compression Byte buffer fails");

        System.out.println("put compressed size as "+compressedSize+" bytes into dest of size "+dest.length +", starting at offset "+destOffsetInLoop);
        compressedBuffer.get(dest,destOffsetInLoop,compressedSize);
        totalCompressedSize += compressedSize;
      }
      catch (BufferUnderflowException be){
        throw new QATException("compressByteArrayInLoop buffer Underflow " + be.getMessage());
      }
      catch (IndexOutOfBoundsException ie){
        throw new QATException("compressByteArrayInLoop index out of bounds "+ ie.getMessage());
      }
      catch (Exception e) {
        throw new QATException("compressByteArrayInLoop: not compressed successfully "+ e.getMessage());
      }

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

      compressedBuffer.put(srcBuff.array(), sourceOffsetInLoop, compressedBufferLimit);
      compressedBuffer.flip();

      try {
        decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, compressedBufferLimit, unCompressedBuffer, 0, retryCount);
        unCompressedBuffer.flip();
        destBuff.put(unCompressedBuffer.array(), destOffsetInLoop, decompressedSize);
        totalDecompressedSize += decompressedSize;
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += compressedBufferLimit;
      destOffsetInLoop += decompressedSize;
      remaining -= compressedBufferLimit;
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

      try {
        decompressedSize = InternalJNI.decompressByteBuff(qzSession, compressedBuffer, 0, sourceLimit, unCompressedBuffer, 0, retryCount);
        //unCompressedBuffer.flip();
        unCompressedBuffer.get(dest,destOffsetInLoop,decompressedSize);
        totalDecompressedSize += decompressedSize;
      } catch (Exception e) {
        throw new QATException("decompressByteArrayInLoop not decompressed successfully"+ e.getMessage());
      }

      sourceOffsetInLoop += sourceLimit;
      destOffsetInLoop += decompressedSize;
      remaining -= sourceLimit;
    }
    return totalDecompressedSize;
  }
}
