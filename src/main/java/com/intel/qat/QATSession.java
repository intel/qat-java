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
 * Defines APIs for creation of hardware session with QAT device, exposed compression and decompression APIs and hardware session cleanup
 */
public class QATSession {

  private final int QZ_OK = 0;
  private int qzStatus = Integer.MIN_VALUE;
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;

  private int retryCount;
  //private ByteBuffer unCompressedBuffer;
  //private ByteBuffer compressedBuffer;
  Buffers buffers;

  private QATUtils.ExecutionPaths executionPath;

  private String compressionAlgo; // public ENUM instead of String to restrict the choice and validation wont be needed

  private int compressionLevel;

  //TODO: Have two boolean flags HARDWARE_ONLY(performance mode - if pinned memory not available , fail it (no cmalloc and no bytebuffer)
  // , AUTO -> hardware first else fallback to software
  // ,RETRY logic ->
  // check what happens ,

  public QATSession(){
    // call parameterized constructor
    this(QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, 0,QATUtils.CompressionAlgo.DEFLATE,6);
  }
  public QATSession(QATUtils.ExecutionPaths executionPath, int retryCount, QATUtils.CompressionAlgo compressionAlgo, int compressionLevel){
    if(!validateParams(retryCount, compressionLevel)){
      throw new IllegalArgumentException("Invalid QATSession object parameters");
    }

    this.executionPath = executionPath;
    this.retryCount = retryCount;
    this.compressionAlgo = compressionAlgo.getCompressionAlgorithm();
    this.compressionLevel = compressionLevel;
    this.buffers = new Buffers();

    setup();
  }

  /**
   * setup API creates and stores QAT hardware session at C layer in JNI application, all other API call can use the stored session created
   * using this API
   */

  // code review comments: merge set up and allocation of buffer in 1 JNI function call
  protected void setup() { // refactor this and there should be ONLY 1 JNI call per execution path
    // setup call specific to a path
    System.out.println("execution path value = "+ executionPath.getExecutionPathCode());
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
    qzStatus = QZ_OK; // this is not needed in this design
  }

  /**
   * Setup QAT software session with Java allocated byte buffers
   */
  private void setupAUTO(){
    try {
      InternalJNI.setup(this.buffers,QATUtils.ExecutionPaths.AUTO.getExecutionPathCode(),DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, this.compressionAlgo, this.compressionLevel);
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
      InternalJNI.setup(this.buffers, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY.getExecutionPathCode(), DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, this.compressionAlgo, this.compressionLevel);
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
   * teardown API destroys the QAT hardware session and free up resources allocated with setup API call
   */
  public void teardown() {
    if(ifPinnedMem()) {
      try {
        freeNativesrcDestByteBuff(buffers.unCompressedBuffer, buffers.compressedBuffer);
      } catch (Exception e) {
        throw new QATException(QATUtils.getErrorMessage(Integer.parseInt(e.getMessage())));
      }
    }

    int r = InternalJNI.teardown();
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
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.maxCompressedSize(buffers.qzSession, srcLen);
  }

  /**
   * destroys special native allocated memory to be used by QAT hardware for compression/decompression
   * @param srcbuff source buffer
   * @param destbuff destination buffer
   * @return success or fail status
   */
  void freeNativesrcDestByteBuff(ByteBuffer srcbuff, ByteBuffer destbuff){
    if(srcbuff == null || destbuff == null)
      throw new QATException("empty buffers cannot be freed");

    InternalJNI.freeNativesrcDestByteBuff(buffers.qzSession, srcbuff, destbuff);
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer from a given destination offset
   * @param src source bytebuffer
   * @param dest destination bytebuffer
   * @return success or exception thrown
   */

  public int compressByteBuff(ByteBuffer src, ByteBuffer dest) { // document the state of buffers

    //validation check for empty buffers, also if dest is readOnly,also for source buffer position == limit -> throw exceptions
    // https://docs.oracle.com/javase/7/docs/api/java/nio/ByteBuffer.html#put(byte[]) -> throw exception as in javadoc
    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int compressedSize = 0;
    try {
      // boolean function rather than explicit check for null here
      if (ifPinnedMem()) {
        // loop it with a size of pinned memory size
        compressByteBufferInLoop(src, dest);
      }
      // check if source bytebuffer is readonly and then create direct bytebuffer and copy this data
      else if (src.isReadOnly()) {
        ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());
        compressedSize = InternalJNI.compressByteBuff(buffers.qzSession, srcBuffer, 0, srcBuffer.limit(), dest, dest.position(), retryCount);
      } else {
        compressedSize = InternalJNI.compressByteBuff(buffers.qzSession, src, src.position(), src.remaining(), dest, dest.position(), retryCount);
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
  { //take destOffset as parameter

    if(srcLen == 0 || dest.length == 0)
      throw new QATException("empty buffer");

    int compressedSize = 0;

    try {
      if (ifPinnedMem()) { // PINNED MEMORY is available

        // refactor this to have this included in loop function itself
        // loop it with a size of pinned memory size
        ByteBuffer srcBuffer = ByteBuffer.allocateDirect(srcLen);
        ByteBuffer destBuffer = ByteBuffer.allocateDirect(dest.length);

        srcBuffer.put(src, srcOffset, srcLen);
        srcBuffer.flip();

        compressByteBufferInLoop(srcBuffer,destBuffer);
        destBuffer.get(dest, destOffset, destBuffer.limit());

      } else {
        compressedSize = InternalJNI.compressByteArray(buffers.qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
      }
      // if destination is smaller than compressedSize we throw an exception by checking if updared src_len != sourceBuffersize value after qzDecompress
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
   * @param src source bytebuffer
   * @param dest destination bytebuffer
   * @return success or throws exception
   */

  public int decompressByteBuff (ByteBuffer src, ByteBuffer dest){
    //validation check for empty buffers, also if dest is readOnly,also for source buffer position == limit -> throw exceptions
    // https://docs.oracle.com/javase/7/docs/api/java/nio/ByteBuffer.html#put(byte[]) -> throw exception as in javadoc
    if (src.position() == src.limit() || dest.position() == dest.limit())
      throw new BufferOverflowException();

    if (dest.isReadOnly())
      throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    try {
      // boolean function rather than explicit check for null here
      if (ifPinnedMem()) {
        // loop it with a size of pinned memory size
        decompressByteBufferInLoop(src, dest);
      }
      // check if source bytebuffer is readonly and then create direct bytebuffer and copy this data
      else if (src.isReadOnly()) {
        ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.remaining());

        decompressedSize = InternalJNI.decompressByteBuff(buffers.qzSession, srcBuffer, 0, srcBuffer.limit(), dest, 0, retryCount);
      } else {
        decompressedSize = InternalJNI.compressByteBuff(buffers.qzSession, src, src.position(), src.remaining(), dest, dest.position(), retryCount);
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
      if (ifPinnedMem()) { // PINNED MEMORY is available

        // refactor this to have this included in loop function itself
        // loop it with a size of pinned memory size
        ByteBuffer srcBuffer = ByteBuffer.allocateDirect(srcLen);
        ByteBuffer destBuffer = ByteBuffer.allocateDirect(dest.length);

        srcBuffer.put(src, srcOffset, srcLen);
        srcBuffer.flip();

        decompressByteBufferInLoop(srcBuffer,destBuffer);
        destBuffer.get(dest,destOffset, destBuffer.limit());

      } else {
        decompressedSize = InternalJNI.compressByteArray(buffers.qzSession, src, srcOffset, srcLen, dest, destOffset, retryCount);
      }
      // if destination is smaller than compressedSize we throw an exception by checking if updared src_len != sourceBuffersize value after qzDecompress
    }catch (QATException qe){
      throw new QATException(qe.getMessage());
    }

    if (decompressedSize < 0) {
      throw new QATException("QAT: Compression failed");
    }

    return decompressedSize;
  }

  private boolean validateParams ( int retryCount, int compressionLevel){
    System.out.println("validating..");
    return (retryCount <= 10 && compressionLevel <= 9 && compressionLevel > 0);
  }

  private boolean ifPinnedMem() { // think of a better name that describe the logic
    return (buffers.unCompressedBuffer != null && buffers.compressedBuffer != null);
  }

  private boolean partialPinnedMem() { // think of a better name that describe the logic
    return (buffers.unCompressedBuffer == null || buffers.compressedBuffer == null);
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
    int unCompressedBufferLimit = buffers.unCompressedBuffer.limit();

    while (remaining > 0) {
      buffers.unCompressedBuffer.clear();
      buffers.compressedBuffer.clear();

      buffers.unCompressedBuffer.put(srcBuff.array(), sourceOffsetInLoop, unCompressedBufferLimit);
      buffers.unCompressedBuffer.flip();

      try {
        compressedSize = InternalJNI.compressByteBuff(buffers.qzSession, buffers.unCompressedBuffer, 0, unCompressedBufferLimit, buffers.compressedBuffer, 0, retryCount);
        buffers.compressedBuffer.flip();
        destBuff.put(buffers.compressedBuffer.array(), destOffsetInLoop, compressedSize);
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
    int compressedBufferLimit = buffers.compressedBuffer.limit();

    while (remaining > 0) {
      buffers.unCompressedBuffer.clear();
      buffers.compressedBuffer.clear();

      buffers.compressedBuffer.put(srcBuff.array(), sourceOffsetInLoop, compressedBufferLimit);
      buffers.compressedBuffer.flip();

      try {
        decompressedSize = InternalJNI.decompressByteBuff(buffers.qzSession, buffers.compressedBuffer, 0, compressedBufferLimit, buffers.unCompressedBuffer, 0, retryCount);
        buffers.unCompressedBuffer.flip();
        destBuff.put(buffers.unCompressedBuffer.array(), destOffsetInLoop, decompressedSize);
      } catch (Exception e) {
        throw new QATException("not compressed successfully");
      }

      sourceOffsetInLoop += compressedBufferLimit;
      destOffsetInLoop += decompressedSize;
      remaining -= compressedBufferLimit;
    }
  }
}
