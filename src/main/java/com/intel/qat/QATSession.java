/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;
import java.nio.ByteBuffer;
import java.lang.ref.Cleaner;
// need javadocs for all public methods
//clang format for the source code - default
//create QAT specific exception ( override Runtime Exception)
// Do not put error code directly in the log , create mapping with integer with string error code (only error code)
// change to QATSession name

/**
 * Defines APIs for creation of hardware session with QAT device, exposed compression and decompression APIs and hardware session cleanup
 */
public class QATSession {

  private final int QZ_OK = 0;
  private int qzStatus = Integer.MIN_VALUE;
  private final int DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 480 * 1024;
  private int internalBufferSizeInBytes;

  private int retryCount = 0;

  private Boolean retry = false;
  public ByteBuffer unCompressedBuffer;
  public ByteBuffer compressedBuffer;

  private QATUtils.ExecutionPaths executionPath;

  //TODO: Have two boolean flags HARDWARE_ONLY(performance mode - if pinned memory not available , fail it (no cmalloc and no bytebuffer)
  // , AUTO -> hardware first else fallback to software
 // ,RETRY logic ->
  // check what happens ,

  public QATSession(){
    internalBufferSizeInBytes = DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES;
    this.executionPath = QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY;
  }
  public QATSession(int bufferSize, QATUtils.ExecutionPaths executionPath, boolean retry, int retryCount){
    this.internalBufferSizeInBytes = bufferSize;
    this.executionPath = executionPath;
    this.retry = retry;
    this.retryCount = retryCount;
  }

  /**
   * setup API creates and stores QAT hardware session at C layer in JNI application, all other API call can use the stored session created
   * using this API
   */

  public void setup() {
	System.out.println("execution path value = "+ this.executionPath.getExecutionPathCode());
    int r = InternalJNI.setup(this.executionPath.getExecutionPathCode());
    if (r != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(r));
    }

    if(this.executionPath.getExecutionPathCode() == QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY.getExecutionPathCode()) {
      ByteBuffer[] tmpBuffer = null;

      try {
        tmpBuffer = nativeSrcDestByteBuff(internalBufferSizeInBytes);
      } catch (QATException e) {
        teardown();
        throw new QATException(QATUtils.getErrorMessage(Integer.parseInt(e.getMessage())));
        // TODO: free up all the memory allocated
      }
      unCompressedBuffer = tmpBuffer[0];
      compressedBuffer = tmpBuffer[1];

      if(unCompressedBuffer == null || compressedBuffer == null){
        teardown();
        throw new QATException("PINNED memory not available");
      }
    }
    else{
      unCompressedBuffer = ByteBuffer.allocateDirect(internalBufferSizeInBytes);
      compressedBuffer = ByteBuffer.allocateDirect(maxCompressedLength(internalBufferSizeInBytes));
    }

    qzStatus = QZ_OK;
  }

  /**
   * teardown API destroys the QAT hardware session and free up resources allocated with setup API call
   */
  public void teardown() {
    if (qzStatus != QZ_OK) {
      throw new QATException("Setup was not successful.");
    }
    if(this.executionPath.getExecutionPathCode() == QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY.getExecutionPathCode()) {
      try {
        freeNativesrcDestByteBuff(unCompressedBuffer, compressedBuffer);
      } catch (Exception e) {
        throw new QATException(QATUtils.getErrorMessage(Integer.parseInt(e.getMessage())));
      }
    }
    else{
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      unCompressedBuffer = null;
      compressedBuffer = null;
    }
    int r = InternalJNI.teardown();
    if (r != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(r));
    }


    qzStatus = r;
  }

  /**
   * Provides maximum compression length (probable, not exact as this is decided after successful compression) for a given
   * source length
   * @param srcLen source length
   * @return maximum compressed length
   */
   public int maxCompressedLength(int srcLen) {
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.maxCompressedSize(srcLen);
  }

  /**
   * creates special native allocated memory to be used by QAT hardware for compression/decompression
   * @param srcSize source size
   * @return ByteBuffer array of source and destination buffer
   */
  ByteBuffer[] nativeSrcDestByteBuff(int srcSize){ // wrap inside Compressor Decompressor
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }
    int maxCompressedLen = internalBufferSizeInBytes; // put this logic inside nativeSrcDestByteBuff
    try {
      maxCompressedLen = maxCompressedLength(internalBufferSizeInBytes);
    } catch (QATException e) {

      throw new QATException(QATUtils.getErrorMessage(Integer.parseInt(e.getMessage())));
    }

    return InternalJNI.nativeSrcDestByteBuff(srcSize, maxCompressedLen);
  }

  /**
   * destroys special native allocated memory to be used by QAT hardware for compression/decompression
   * @param srcbuff source buffer
   * @param destbuff destination buffer
   * @return success or fail status
   */
   int freeNativesrcDestByteBuff(ByteBuffer srcbuff, ByteBuffer destbuff){
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.freeNativesrcDestByteBuff(srcbuff, destbuff);
  }

  /**
   * compresses source bytebuffer from a given source offset till source length into destination bytebuffer
   * @param src source bytebuffer
   * @param srcOffset source offset
   * @param srcLen source length
   * @param dest destination bytebuffer
   * @return success or fail status
   */

  public int compressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.compressByteBuff(src,srcOffset, srcLen, dest, this.retry, this.retryCount);
  }

  /**
   * decompresses source bytebuffer from a given source offset till source length into destination bytebuffer
   * @param src source bytebuffer
   * @param srcOffset source offset
   * @param srcLen source length
   * @param dest destination bytebuffer
   * @return success or fail status
   */

  public int decompressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.decompressByteBuff(src,srcOffset, srcLen, dest, this.retry, this.retryCount);
  }
}
