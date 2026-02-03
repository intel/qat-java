/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatCompressorOutputStream;
import com.intel.qat.QatDecompressorInputStream;
import com.intel.qat.QatZipper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class FuzzerTest {
  private static final Random RANDOM = new Random();
  private static final int MIN_STREAM_SIZE = 32;
  private static final int MAX_BUFFER_SIZE = 1024 * 1024;
  private static final int STREAM_READ_BUFFER_SIZE = 1024;

  public static void fuzzerTestOneInput(FuzzedDataProvider data) throws IOException {
    if (data.remainingBytes() == 0) return;

    byte[] sourceData = data.consumeRemainingAsBytes();

    // Test all compression scenarios
    testAllCompressionScenarios(sourceData);
  }

  private static void testAllCompressionScenarios(byte[] sourceData) throws IOException {
    QatZipper.Algorithm[] algorithms = {
      QatZipper.Algorithm.DEFLATE, QatZipper.Algorithm.LZ4, QatZipper.Algorithm.ZSTD
    };

    for (QatZipper.Algorithm algorithm : algorithms) {
      // Basic compression tests
      testByteArrayCompression(sourceData, algorithm);

      // Compression with parameters
      testByteArrayWithOffsets(sourceData, algorithm);

      // ByteBuffer tests (ZSTD requires direct source buffers)
      if (algorithm == QatZipper.Algorithm.ZSTD) {
        // ZSTD requires direct source byte buffers
        testByteBufferCompression(sourceData, algorithm, true);
        testZstdSpecificTests(sourceData);
      } else {
        testByteBufferCompression(sourceData, algorithm, false);
        testByteBufferCompression(sourceData, algorithm, true);

        // Mixed buffer type tests (skip for ZSTD due to direct buffer requirement)
        testMixedBufferTypes(sourceData, algorithm);
      }

      // Advanced configuration tests
      testWithAdvancedConfiguration(sourceData, algorithm);

      // Stream-based tests
      testStreamCompression(sourceData, algorithm, false);
      testStreamCompression(sourceData, algorithm, true);
    }
  }

  private static void testByteArrayCompression(byte[] sourceData, QatZipper.Algorithm algorithm) {
    QatZipper qzip = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      byte[] compressed = new byte[qzip.maxCompressedLength(sourceData.length)];
      byte[] decompressed = new byte[sourceData.length];

      int compressedSize =
          qzip.compress(sourceData, 0, sourceData.length, compressed, 0, compressed.length);
      qzip.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assert Arrays.equals(sourceData, decompressed)
          : String.format("Array compression failed for %s", algorithm);
    } finally {
      qzip.end();
    }
  }

  private static void testByteArrayWithOffsets(byte[] sourceData, QatZipper.Algorithm algorithm) {
    if (sourceData.length == 0) return;

    QatZipper qzip = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      int offset = RANDOM.nextInt(sourceData.length);
      int length = sourceData.length - offset;

      byte[] compressed = new byte[qzip.maxCompressedLength(length)];
      byte[] decompressed = new byte[length];

      int compressedSize =
          qzip.compress(sourceData, offset, length, compressed, 0, compressed.length);
      int decompressedSize =
          qzip.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      byte[] expectedData = Arrays.copyOfRange(sourceData, offset, sourceData.length);
      byte[] actualData = Arrays.copyOfRange(decompressed, 0, decompressedSize);

      assert Arrays.equals(expectedData, actualData)
          : String.format("Array compression with offsets failed for %s", algorithm);
    } finally {
      qzip.end();
    }
  }

  private static void testByteBufferCompression(
      byte[] sourceData, QatZipper.Algorithm algorithm, boolean direct) {
    QatZipper qzip = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      // ZSTD requires direct source buffers, so force direct for source when using ZSTD
      boolean useDirectSource = direct || (algorithm == QatZipper.Algorithm.ZSTD);

      ByteBuffer sourceBuf = createBuffer(sourceData.length, useDirectSource);
      sourceBuf.put(sourceData).flip();

      int compressedSize = qzip.maxCompressedLength(sourceData.length);
      assert compressedSize > 0 : "Compressed size should be positive";

      ByteBuffer compressedBuf = createBuffer(compressedSize, direct);
      qzip.compress(sourceBuf, compressedBuf);
      sourceBuf.flip();
      compressedBuf.flip();

      ByteBuffer decompressedBuf = createBuffer(sourceData.length, direct);
      qzip.decompress(compressedBuf, decompressedBuf);
      decompressedBuf.flip();

      assert sourceBuf.compareTo(decompressedBuf) == 0
          : String.format("ByteBuffer compression failed for %s (direct: %s)", algorithm, direct);
    } finally {
      qzip.end();
    }
  }

  private static void testMixedBufferTypes(byte[] sourceData, QatZipper.Algorithm algorithm) {
    // Skip mixed buffer type tests entirely for ZSTD due to direct buffer requirements
    if (algorithm == QatZipper.Algorithm.ZSTD) {
      return;
    }

    QatZipper qzip = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      // Test various buffer type combinations
      testBufferCombination(qzip, sourceData, false, true, false); // heap->direct->heap
      testBufferCombination(qzip, sourceData, true, false, true); // direct->heap->direct
      testBufferCombination(qzip, sourceData, false, false, true); // heap->heap->direct
      testBufferCombination(qzip, sourceData, true, true, false); // direct->direct->heap

      // Test with read-only buffers (only for DEFLATE and LZ4)
      testReadOnlyBufferCompression(qzip, sourceData);
    } finally {
      qzip.end();
    }
  }

  private static void testBufferCombination(
      QatZipper qzip, byte[] sourceData, boolean srcDirect, boolean compDirect, boolean decDirect) {
    // For ZSTD, always use direct source buffers
    boolean useDirectSource = srcDirect;
    if (qzip.toString().contains("ZSTD")) {
      useDirectSource = true;
    }

    ByteBuffer sourceBuf = createBuffer(sourceData.length, useDirectSource);
    sourceBuf.put(sourceData).flip();

    int compressedSize = qzip.maxCompressedLength(sourceData.length);
    ByteBuffer compressedBuf = createBuffer(compressedSize, compDirect);
    qzip.compress(sourceBuf, compressedBuf);
    sourceBuf.flip();
    compressedBuf.flip();

    ByteBuffer decompressedBuf = createBuffer(sourceData.length, decDirect);
    qzip.decompress(compressedBuf, decompressedBuf);
    decompressedBuf.flip();

    assert sourceBuf.compareTo(decompressedBuf) == 0
        : String.format(
            "Mixed buffer type test failed (src:%s, comp:%s, dec:%s)",
            useDirectSource, compDirect, decDirect);
  }

  private static void testReadOnlyBufferCompression(QatZipper qzip, byte[] sourceData) {
    // This method is only called for DEFLATE and LZ4 algorithms
    ByteBuffer sourceBuf = ByteBuffer.allocate(sourceData.length);
    sourceBuf.put(sourceData).flip();

    int compressedSize = qzip.maxCompressedLength(sourceData.length);
    ByteBuffer readOnlySource = sourceBuf.asReadOnlyBuffer();

    ByteBuffer compressedBuf = ByteBuffer.allocateDirect(compressedSize);
    qzip.compress(readOnlySource, compressedBuf);
    compressedBuf.flip();

    ByteBuffer decompressedBuf = ByteBuffer.allocate(sourceData.length);
    ByteBuffer readOnlyCompressed = compressedBuf.asReadOnlyBuffer();
    qzip.decompress(readOnlyCompressed, decompressedBuf);
    decompressedBuf.flip();
    /*
    System.out.printf(
        "%d, %d, %d, %d\n",
        sourceBuf.position(),
        sourceBuf.limit(),
        decompressedBuf.position(),
        decompressedBuf.limit());
    */
    assert sourceBuf.compareTo(decompressedBuf) == 0
        : "Read-only buffer compression failed for DEFLATE/LZ4";
  }

  private static void testZstdSpecificTests(byte[] sourceData) {
    // Test ZSTD-specific features like checksum functionality
    QatZipper qzip = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      // Test with checksum disabled (this should work)
      qzip.setChecksumFlag(false);

      ByteBuffer sourceBuf = ByteBuffer.allocateDirect(sourceData.length);
      sourceBuf.put(sourceData).flip();

      int compressedSize = qzip.maxCompressedLength(sourceData.length);
      ByteBuffer compressedBuf = ByteBuffer.allocateDirect(compressedSize);
      qzip.compress(sourceBuf, compressedBuf);
      sourceBuf.flip();
      compressedBuf.flip();

      ByteBuffer decompressedBuf = ByteBuffer.allocateDirect(sourceData.length);
      qzip.decompress(compressedBuf, decompressedBuf);
      decompressedBuf.flip();

      assert sourceBuf.compareTo(decompressedBuf) == 0 : "ZSTD checksum test failed";
    } finally {
      qzip.end();
    }
  }

  private static void testWithAdvancedConfiguration(
      byte[] sourceData, QatZipper.Algorithm algorithm) {
    int compressionLevel = RANDOM.nextInt(9) + 1;
    int retryCount = RANDOM.nextInt(20);

    QatZipper qzip =
        new QatZipper.Builder()
            .algorithm(algorithm)
            .level(compressionLevel)
            .retryCount(retryCount)
            .build();
    try {
      byte[] compressed = new byte[qzip.maxCompressedLength(sourceData.length)];
      byte[] decompressed = new byte[sourceData.length];

      int compressedSize =
          qzip.compress(sourceData, 0, sourceData.length, compressed, 0, compressed.length);
      qzip.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assert Arrays.equals(sourceData, decompressed)
          : String.format(
              "Advanced configuration test failed for %s (level: %d, retry: %d)",
              algorithm, compressionLevel, retryCount);
    } finally {
      qzip.end();
    }
  }

  private static void testStreamCompression(
      byte[] sourceData, QatZipper.Algorithm algorithm, boolean singleByteRead) throws IOException {
    if (sourceData.length < MIN_STREAM_SIZE) return;

    int compressBufferSize = 1 + Math.abs(RANDOM.nextInt(MAX_BUFFER_SIZE));
    int decompressBufferSize = 1 + Math.abs(RANDOM.nextInt(MAX_BUFFER_SIZE));

    // Compress using stream
    ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressor =
        new QatCompressorOutputStream(compressedStream, compressBufferSize, algorithm)) {
      compressor.write(sourceData);
    }

    // Decompress using stream
    byte[] compressedData = compressedStream.toByteArray();
    ByteArrayOutputStream decompressedStream = new ByteArrayOutputStream();

    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
        QatDecompressorInputStream decompressor =
            new QatDecompressorInputStream(inputStream, decompressBufferSize, algorithm)) {

      if (singleByteRead) {
        decompressSingleByte(decompressor, decompressedStream);
      } else {
        decompressBuffered(decompressor, decompressedStream);
      }

      assert decompressor.available() == 0 : "Stream should be fully consumed";
    }

    byte[] result = decompressedStream.toByteArray();
    assert Arrays.equals(sourceData, result)
        : String.format(
            "Stream compression failed for %s (compress buffer: %d, decompress buffer: %d, single byte: %s, source length: %d)",
            algorithm, compressBufferSize, decompressBufferSize, singleByteRead, sourceData.length);
  }

  private static void decompressBuffered(
      QatDecompressorInputStream decompressor, ByteArrayOutputStream output) throws IOException {
    byte[] buffer = new byte[STREAM_READ_BUFFER_SIZE];
    int bytesRead;
    while ((bytesRead = decompressor.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
    }
  }

  private static void decompressSingleByte(
      QatDecompressorInputStream decompressor, ByteArrayOutputStream output) throws IOException {
    int byteValue;
    while ((byteValue = decompressor.read()) != -1) {
      output.write(byteValue);
    }
  }

  private static ByteBuffer createBuffer(int size, boolean direct) {
    return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
  }
}
