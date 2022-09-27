package com.intel.qat;
import java.nio.ByteBuffer;
public class CompressorDecompressor{

  private final int QZ_OK = 0;
  private int qzStatus = -1;


  public void setup() {
    int r = InternalJNI.setup();
    if (r != QZ_OK) {
      qzStatus = r;
      throw new RuntimeException("Error " + r + ": QAT failed to initialize.");
    }
    qzStatus = r;
  }

  public void teardown() {
    int r = InternalJNI.teardown();
    if (r != QZ_OK) {
      qzStatus = r;
      throw new RuntimeException("Error " + r + ": QAT failed to teardown.");
    }
    qzStatus = r;

  }

  public int maxCompressedLength(int srcLen) {
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to get maxCompressed length.");
    }

    return InternalJNI.maxCompressedSize(srcLen);
  }

  public ByteBuffer nativeByteBuff(int size){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to get native Byte Buffer.");
    }

    return InternalJNI.nativeByteBuff(size);
  }

  public ByteBuffer[] nativeSrcDestByteBuff(int srcSize, int destSize){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to get native Byte Buffer.");
    }

    return InternalJNI.nativeSrcDestByteBuff(srcSize, destSize);
  }

  public ByteBuffer nativeByteBuffThreadLocal(int size, boolean ifSource){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to get native Byte Buffer.");
    }

    return InternalJNI.nativeByteBuffThreadLocal(size, ifSource);
  }

  public int freeNativeByteBuff(ByteBuffer buff){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to free native Byte Buffer.");
    }

    return InternalJNI.freeNativeByteBuff(buff);
  }

  public int freeNativesrcDestByteBuff(ByteBuffer srcbuff, ByteBuffer destbuff){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to free native Byte Buffer.");
    }

    return InternalJNI.freeNativesrcDestByteBuff(srcbuff, destbuff);
  }


  public int freeNativeByteBuffThreadLocal(ByteBuffer buff, boolean ifSource){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to free native Byte Buffer.");
    }

    return InternalJNI.freeNativeByteBuffThreadLocal(buff, ifSource);
  }

  public int compressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to compress Byte Buffer.");
    }

    return InternalJNI.compressByteBuff(src,srcOffset, srcLen, dest);
  }

  public int compressByteBuffThreadLocal(int srcOffset, int srcLen, int compressedLength){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to compress Byte Buffer.");
    }

    return InternalJNI.compressByteBuffThreadLocal(srcOffset, srcLen, compressedLength);
  }

  public int decompressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to decompress Byte Buffer.");
    }

    return InternalJNI.decompressByteBuff(src,srcOffset, srcLen, dest);
  }

  public int decompressByteBuffThreadLocal(int srcOffset, int srcLen, int uncompressedLength){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to decompress Byte Buffer.");
    }

    return InternalJNI.decompressByteBuffThreadLocal(srcOffset, srcLen, uncompressedLength);
  }

  public int compressByteArray(byte[] src, int srcOffset, int srcLen, byte[] dest) {
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to compress.");
    }

    return InternalJNI.compressByteArray(src, srcOffset, srcLen, dest);
  }

  public int decompressByteArray(byte[] src, int srcOffset, int srcLen, byte[] dest,
                        int uncompressedLength) {
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to decompress.");
    }

    return InternalJNI.decompressByteArray(src, srcOffset, srcLen, dest, uncompressedLength);
  }
}
