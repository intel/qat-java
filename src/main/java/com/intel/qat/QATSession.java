/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;
import java.nio.ByteBuffer;

/**
 * Defines APIs for creation of hardware session with QAT device, exposed compression and decompression APIs and hardware session cleanup
 */
public class QATSession {

  private final int QZ_OK = 0;
  private int qzStatus = Integer.MIN_VALUE;
  private final static int DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 480 * 1024;
  private int internalBufferSizeInBytes;

  private int retryCount = 0;
  public ByteBuffer unCompressedBuffer;
  public ByteBuffer compressedBuffer;

  private QATUtils.ExecutionPaths executionPath;

  private String compressionAlgo; // public ENUM instead of String to restrict the choice and validation wont be needed

  private int compressionLevel;

  //TODO: Have two boolean flags HARDWARE_ONLY(performance mode - if pinned memory not available , fail it (no cmalloc and no bytebuffer)
  // , AUTO -> hardware first else fallback to software
  // ,RETRY logic ->
  // check what happens ,

  public QATSession(){
    // call parameterized constructor
    this(DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, QATUtils.ExecutionPaths.AUTO, 0,"deflate",0);
    setup();
  }
  public QATSession(int bufferSize, QATUtils.ExecutionPaths executionPath, int retryCount, String compressionAlgo, int compressionLevel){
    if(!validateParams(retryCount, compressionAlgo, compressionLevel))
      throw new IllegalArgumentException("Invalid QATSession object parameters"); // Illegal argument exception - fail it right away

    this.internalBufferSizeInBytes = bufferSize;
    this.executionPath = executionPath;
    this.retryCount = retryCount;
    this.compressionAlgo = compressionAlgo;
    this.compressionLevel = compressionLevel;
    setup();
  }

  /**
   * setup API creates and stores QAT hardware session at C layer in JNI application, all other API call can use the stored session created
   * using this API
   */

  // code review comments: merge set up and allocation of buffer in 1 JNI function call
  protected void setup() { // refactor this and there should be ONLY 1 JNI call per execution path
    // setup call specific to a path
    System.out.println("execution path value = "+ this.executionPath.getExecutionPathCode());
    int r = InternalJNI.setup(this.executionPath.getExecutionPathCode(), this.compressionAlgo, this.compressionLevel);
    if (r != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(r));
    }

    if(this.executionPath.getExecutionPathCode() == QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY.getExecutionPathCode()) {
      ByteBuffer[] tmpBuffer = null;

      try {
        tmpBuffer = nativeSrcDestByteBuff(internalBufferSizeInBytes); // combine this with setup native method
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
      // make sure this came with allocate with allocateDirect API call
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      unCompressedBuffer = null;
      compressedBuffer = null; // better solution to mark this for garbage collection instead of assignment to null
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

  public int compressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){ // we dont need offset and length parameters
    if((srcLen - srcOffset + 1) < this.unCompressedBuffer.limit()){
      throw new QATException("buffer size is larger than initial mentioned srcSize, recreate the object again");
    }
    if(!src.isDirect() || !dest.isDirect()){
      throw new QATException("Provided buffer is not direct bytebuffer");
    } // why it should be direct byte buffer??
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.compressByteBuff(src,srcOffset, srcLen, dest, this.retryCount);
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
    if((srcLen - srcOffset + 1) < this.compressedBuffer.limit()){
      throw new QATException("buffer size is larger than initial mentioned destSize, recreate the object again");
    }
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.decompressByteBuff(src,srcOffset, srcLen, dest, this.retryCount);
  }

  private Boolean validateParams(int retryCount, String compressionAlgo, int compressionLevel){
    System.out.println("validating..");
    try{
      QATUtils.CompressionAlgo.valueOf(compressionAlgo);
      System.out.println("algo present..");
    }
    catch (IllegalArgumentException ie){
      return false;
    }
    return (retryCount <= 10 && compressionLevel <= 9 && compressionLevel > 0);
  }
}
