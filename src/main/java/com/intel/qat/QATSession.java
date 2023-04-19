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
  private final static long DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES = 491520L;
  private long internalBufferSizeInBytes;

  private int retryCount = 0;
  private ByteBuffer unCompressedBuffer;
  private ByteBuffer compressedBuffer;

  private QATUtils.ExecutionPaths executionPath;

  private String compressionAlgo; // public ENUM instead of String to restrict the choice and validation wont be needed

  private int compressionLevel;

  //TODO: Have two boolean flags HARDWARE_ONLY(performance mode - if pinned memory not available , fail it (no cmalloc and no bytebuffer)
  // , AUTO -> hardware first else fallback to software
  // ,RETRY logic ->
  // check what happens ,

  public QATSession(){
    // call parameterized constructor
    this(DEFAULT_INTERNAL_BUFFER_SIZE_IN_BYTES, QATUtils.ExecutionPaths.AUTO, 0,QATUtils.CompressionAlgo.DEFLATE,0);
  }
  public QATSession(long bufferSize, QATUtils.ExecutionPaths executionPath, int retryCount, QATUtils.CompressionAlgo compressionAlgo, int compressionLevel){
    if(!validateParams(retryCount, compressionLevel)){
      throw new IllegalArgumentException("Invalid QATSession object parameters");
    }

    this.internalBufferSizeInBytes = bufferSize;
    this.executionPath = executionPath;
    this.retryCount = retryCount;
    this.compressionAlgo = compressionAlgo.getCompressionAlgorithm();
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
    System.out.println("execution path value = "+ executionPath.getExecutionPathCode());
    ByteBuffer[] tmpBuffer = null;
    try {
      if (executionPath == QATUtils.ExecutionPaths.AUTO) {
        setupAUTO();
      } else {
        setupHardware();
      }
    }
    catch (QATException qe){
      throw new QATException(qe.getMessage());
    }
    qzStatus = QZ_OK;
  }

  /**
   * Setup QAT software session with Java allocated byte buffers
   */
  private void setupAUTO(){
    ByteBuffer[] tmpBuffer = null;

    tmpBuffer = InternalJNI.setupAUTO(internalBufferSizeInBytes, compressionAlgo, compressionLevel);

    if(tmpBuffer[0] == null || tmpBuffer[1] == null){
      teardown();
      unCompressedBuffer = null;
      compressedBuffer = null;
      //throw new QATException("PINNED memory not available");
    }
    else {
      unCompressedBuffer = tmpBuffer[0];
      compressedBuffer = tmpBuffer[1];
    }
  }

  /**
   * setup QAT hardware session with PINNED memory & assign this nativbe memory to Java bytebuffers
   */

  private void setupHardware(){
    ByteBuffer[] tmpBuffer = null;

    tmpBuffer = InternalJNI.setupHardware(internalBufferSizeInBytes, compressionAlgo, compressionLevel);

    if(null == tmpBuffer)
      throw new QATException("QAT session failed");

    if(tmpBuffer[0] == null || tmpBuffer[1] == null){
      teardown();
      throw new QATException("PINNED memory not available");
    }
    unCompressedBuffer = tmpBuffer[0];
    compressedBuffer = tmpBuffer[1];
  }
  /**
   * teardown API destroys the QAT hardware session and free up resources allocated with setup API call
   */
  public void teardown() {
    if (qzStatus != QZ_OK) {
      throw new QATException("Setup was not successful.");
    }
    if(executionPath.getExecutionPathCode() == QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY.getExecutionPathCode()) {
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
  public int maxCompressedLength(long srcLen) {
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
  /*ByteBuffer[] nativeSrcDestByteBuff(int srcSize){ // wrap inside Compressor Decompressor
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
  }*/

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
   * @param dest destination bytebuffer
   * @return success or fail status
   */

  /*
  public int compressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){ // we dont need offset and length parameters
    if((srcLen - srcOffset + 1) < unCompressedBuffer.limit()){
      throw new QATException("buffer size is larger than initial mentioned srcSize, recreate the object again");
    }
    if(!src.isDirect() || !dest.isDirect()){
      throw new QATException("Provided buffer is not direct bytebuffer");
    } // why it should be direct byte buffer??
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    return InternalJNI.compressByteBuff(src,srcOffset, srcLen, dest, retryCount);
  }
  */
  public int compressByteBuff(ByteBuffer src, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }
    int compressedSize = 0;
    if(unCompressedBuffer != null && compressedBuffer != null){
      src.rewind();
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      unCompressedBuffer.put(src);
      unCompressedBuffer.flip();

      compressedSize = InternalJNI.compressByteBuff(unCompressedBuffer,0, unCompressedBuffer.limit(), compressedBuffer, retryCount);
    }
    else{
      compressedSize = InternalJNI.compressByteBuff(src,0, src.limit(),dest, retryCount);
    }

    if(compressedSize < 0){
      throw new QATException("QAT: Compression failed");
    }

    if(unCompressedBuffer != null && compressedBuffer != null)
      dest.put(compressedBuffer);

    dest.flip();

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

  public int compressByteArray(byte[] src, int srcOffset, int srcLen, byte[] dest){
    int len = srcLen - srcOffset + 1;
    if(len < compressedBuffer.limit()){
      throw new QATException("buffer size is larger than initial mentioned destSize, recreate the object again");
    }
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }
    int compressedSize = 0;
    ByteBuffer srcBuffer = null, destBuffer = null;

    if(unCompressedBuffer != null && compressedBuffer != null) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      unCompressedBuffer.put(src, srcOffset, srcLen);
      unCompressedBuffer.flip();
      compressedSize = InternalJNI.compressByteBuff(unCompressedBuffer,0, unCompressedBuffer.limit(), compressedBuffer, retryCount);
    }
    else{
      srcBuffer = ByteBuffer.allocateDirect(len);
      destBuffer = ByteBuffer.allocateDirect(maxCompressedLength(len));
      // create byte array JNI API not bytebuffer based
      srcBuffer.put(src);
      srcBuffer.flip();
      compressedSize = InternalJNI.compressByteBuff(srcBuffer,0, srcBuffer.limit(), destBuffer, retryCount);
    }


    if(compressedSize < 0){
      throw new QATException("QAT: Compression failed");
    }
    if(unCompressedBuffer != null && compressedBuffer != null) {
      compressedBuffer.flip();
      compressedBuffer.get(dest, 0, compressedSize);
    }
    else{
      destBuffer.flip();
      destBuffer.get(dest,0,compressedSize);
    }

    return compressedSize;
  }
  /**
   * decompresses source bytebuffer into destination bytebuffer
   * @param src source bytebuffer
   * @param dest destination bytebuffer
   * @return success or throws exception
   */

  public int decompressByteBuff(ByteBuffer src, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    int decompressedSize = 0;
    if(unCompressedBuffer != null && compressedBuffer != null) {
      src.rewind();
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      unCompressedBuffer.put(src);
      unCompressedBuffer.flip();

      decompressedSize = InternalJNI.decompressByteBuff(compressedBuffer, 0, compressedBuffer.limit(), unCompressedBuffer, retryCount);
    }
    else{
      decompressedSize = InternalJNI.decompressByteBuff(src, 0, src.limit(), dest, retryCount);
    }
    if(decompressedSize < 0){
      throw new QATException("QAT: Compression failed");
    }

    if(unCompressedBuffer != null && compressedBuffer != null)
      dest.put(unCompressedBuffer);

    dest.flip();

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

  public int decompressByteArray(byte[] src,int srcOffset, int srcLen, byte[] dest){
    int len = srcLen - srcOffset + 1;
    if(len < compressedBuffer.limit()){
      throw new QATException("buffer size is larger than initial mentioned destSize, recreate the object again");
    }
    if (qzStatus != QZ_OK) {
      throw new QATException(QATUtils.getErrorMessage(qzStatus));
    }

    int decompressedSize = 0;
    ByteBuffer srcBuffer = null, destBuffer = null;

    if(unCompressedBuffer != null && compressedBuffer != null) {
      unCompressedBuffer.clear();
      compressedBuffer.clear();
      unCompressedBuffer.put(src, srcOffset, srcLen);
      unCompressedBuffer.flip();
      decompressedSize = InternalJNI.decompressByteBuff(compressedBuffer,0, compressedBuffer.limit(), unCompressedBuffer, retryCount);
    }
    else{
      srcBuffer = ByteBuffer.allocateDirect(len);
      destBuffer = ByteBuffer.allocateDirect(maxCompressedLength(len));

      srcBuffer.put(src);
      srcBuffer.flip();
      decompressedSize = InternalJNI.decompressByteBuff(srcBuffer,0, srcBuffer.limit(), destBuffer, retryCount);
    }

    if(decompressedSize < 0){
      throw new QATException("QAT: Compression failed");
    }

    if(unCompressedBuffer != null && compressedBuffer != null) {
      unCompressedBuffer.flip();
      unCompressedBuffer.get(dest, 0, decompressedSize);
    }
    else{
      destBuffer.flip();
      destBuffer.get(dest,0,decompressedSize);
    }

    return decompressedSize;
  }
  private boolean validateParams(int retryCount, int compressionLevel){
    System.out.println("validating..");
    return (retryCount <= 10 && compressionLevel <= 9 && compressionLevel > 0);
  }
}