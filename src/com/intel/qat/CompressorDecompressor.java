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
  public ByteBuffer[] nativeSrcDestByteBuff(int srcSize, int destSize){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to get native Byte Buffer.");
    }

    return InternalJNI.nativeSrcDestByteBuff(srcSize, destSize);
  }

  public int freeNativesrcDestByteBuff(ByteBuffer srcbuff, ByteBuffer destbuff){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to free native Byte Buffer.");
    }

    return InternalJNI.freeNativesrcDestByteBuff(srcbuff, destbuff);
  }

  public int compressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to compress Byte Buffer.");
    }

    return InternalJNI.compressByteBuff(src,srcOffset, srcLen, dest);
  }

  public int decompressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest){
    if (qzStatus != QZ_OK) {
      throw new RuntimeException("Error " + qzStatus +
                                 ": failed to decompress Byte Buffer.");
    }

    return InternalJNI.decompressByteBuff(src,srcOffset, srcLen, dest);
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
