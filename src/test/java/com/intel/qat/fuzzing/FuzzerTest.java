/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;
import com.intel.qat.QatZipper;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class FuzzerTest {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      // Save remaining bytes as a source data
      byte[] src = data.consumeRemainingAsBytes();

      testByteArray(src);
      testByteArrayWithParams(src);
      testByteBuffer(src);
      testDirectByteBuffer(src);
      testMixedTypesOne(src);
      testMixedTypesTwo(src);
      testMixedTypesThree(src);
      testWithCompressionLengthAndRetry(src);
      testByteArrayLZ4(src);
      testByteArrayWithParamsLZ4(src);
      testByteBufferLZ4(src);
      testDirectByteBufferLZ4(src);
      testMixedTypesOneLZ4(src);
      testMixedTypesTwoLZ4(src);
      testMixedTypesThreeLZ4(src);
      testWithCompressionLengthAndRetryLZ4(src);
    } catch (QatException e) {
      throw e;
    }
  }

  static void testByteArray(byte[] src) {
    QatZipper zipper = new QatZipper();
    byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
    byte[] dec = new byte[src.length];

    int compressedSize =
        zipper.compress(src, 0, src.length, dst, 0, dst.length);
    zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);
    zipper.end();

    assert Arrays.equals(src, dec)
        : "The source and decompressed arrays do not match.";
  }

  static void testByteArrayLZ4(byte[] src) {
    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);
    byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
    byte[] dec = new byte[src.length];

    int compressedSize =
        zipper.compress(src, 0, src.length, dst, 0, dst.length);
    zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);
    zipper.end();

    assert Arrays.equals(src, dec)
        : "The source and decompressed arrays do not match.";
  }

  static void testByteArrayWithParams(byte[] src) {
    QatZipper zipper = new QatZipper();

    byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
    byte[] dec = new byte[src.length];

    int srcOffset = new Random().nextInt(src.length);
    int compressedSize = zipper.compress(
        src, srcOffset, src.length - srcOffset, dst, 0, dst.length);
    int decompressedSize =
        zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);

    zipper.end();

    assert Arrays.equals(Arrays.copyOfRange(src, srcOffset, src.length),
        Arrays.copyOfRange(dec, 0, decompressedSize))
        : "The source and decompressed arrays do not match.";
  }

  static void testByteArrayWithParamsLZ4(byte[] src) {
    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);

    byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
    byte[] dec = new byte[src.length];

    int srcOffset = new Random().nextInt(src.length);
    int compressedSize = zipper.compress(
        src, srcOffset, src.length - srcOffset, dst, 0, dst.length);
    int decompressedSize =
        zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);

    zipper.end();

    assert Arrays.equals(Arrays.copyOfRange(src, srcOffset, src.length),
        Arrays.copyOfRange(dec, 0, decompressedSize))
        : "The source and decompressed arrays do not match.";
  }

  static void testByteBuffer(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper();
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocate(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testByteBufferLZ4(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocate(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testDirectByteBuffer(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper();
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocateDirect(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testDirectByteBufferLZ4(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocateDirect(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testMixedTypesOne(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper();
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocateDirect(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testMixedTypesOneLZ4(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocateDirect(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testMixedTypesTwo(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper();
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocate(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testMixedTypesTwoLZ4(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;

    ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
    zipper.compress(srcBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocate(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testMixedTypesThree(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper();
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;
    ByteBuffer readonlyBuf = srcBuf.asReadOnlyBuffer();
    srcBuf.flip();

    ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
    zipper.compress(readonlyBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocate(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testMixedTypesThreeLZ4(byte[] src) {
    ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
    srcBuf.put(src, 0, src.length);
    srcBuf.flip();

    QatZipper zipper = new QatZipper(QatZipper.Algorithm.LZ4);
    int compressedSize = zipper.maxCompressedLength(src.length);

    assert compressedSize > 0;
    ByteBuffer readonlyBuf = srcBuf.asReadOnlyBuffer();
    srcBuf.flip();

    ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
    zipper.compress(readonlyBuf, comBuf);
    comBuf.flip();

    ByteBuffer decBuf = ByteBuffer.allocate(src.length);
    zipper.decompress(comBuf, decBuf);

    zipper.end();

    assert srcBuf.compareTo(decBuf)
        == 0 : "The source and decompressed buffers do not match.";
  }

  static void testWithCompressionLengthAndRetry(byte[] src) {
    int comLevel = new Random().nextInt(9) + 1;
    int retryCount = new Random().nextInt(20);

    QatZipper zipper = new QatZipper(
        QatZipper.Algorithm.DEFLATE, comLevel, QatZipper.Mode.AUTO, retryCount);

    byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
    byte[] dec = new byte[src.length];

    int compressedSize =
        zipper.compress(src, 0, src.length, dst, 0, dst.length);
    zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);

    zipper.end();

    assert Arrays.equals(src, dec)
        : "The source and decompressed arrays do not match.";
  }

  static void testWithCompressionLengthAndRetryLZ4(byte[] src) {
    int comLevel = new Random().nextInt(9) + 1;
    int retryCount = new Random().nextInt(20);

    QatZipper zipper = new QatZipper(
        QatZipper.Algorithm.LZ4, comLevel, QatZipper.Mode.AUTO, retryCount);

    byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
    byte[] dec = new byte[src.length];

    int compressedSize =
        zipper.compress(src, 0, src.length, dst, 0, dst.length);
    zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);

    zipper.end();

    assert Arrays.equals(src, dec)
        : "The source and decompressed arrays do not match.";
  }
}
