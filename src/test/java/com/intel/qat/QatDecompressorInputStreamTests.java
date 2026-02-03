/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class QatDecompressorInputStreamTests {
  private static final String SAMPLE_CORPUS = "src/test/resources/sample.txt";
  private static final int DEFAULT_BUFFER_SIZE = 16 * 1024;
  private static final int MAX_CHUNK_SIZE = 20 * 1024;

  private static byte[] sourceData;
  private static Map<Algorithm, byte[]> compressedDataByAlgorithm;
  private final Random random = new Random();

  @BeforeAll
  static void setup() throws IOException {
    sourceData = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    compressedDataByAlgorithm = new EnumMap<>(Algorithm.class);

    // Pre-compress data for each algorithm
    for (Algorithm algorithm : Algorithm.values()) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(outputStream, DEFAULT_BUFFER_SIZE, algorithm)) {
        compressedStream.write(sourceData);
      }
      compressedDataByAlgorithm.put(algorithm, outputStream.toByteArray());
    }
  }

  // Test data providers
  static Stream<Arguments> provideModeAlgorithmParams() {
    Stream<Arguments> baseParams =
        Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
            Arguments.of(Mode.AUTO, Algorithm.LZ4),
            Arguments.of(Mode.AUTO, Algorithm.ZSTD));

    if (QatZipper.isQatAvailable()) {
      return Stream.concat(
          baseParams,
          Stream.of(
              Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE),
              Arguments.of(Mode.HARDWARE, Algorithm.LZ4),
              Arguments.of(Mode.HARDWARE, Algorithm.ZSTD)));
    }
    return baseParams;
  }

  static Stream<Arguments> provideModeAlgorithmLengthParams() {
    return provideModeAlgorithmParams()
        .filter(args -> !args.get()[1].equals(Algorithm.ZSTD)) // Exclude ZSTD for length tests
        .flatMap(
            args ->
                Stream.of(16384, 65536, 524288, 1048576)
                    .map(length -> Arguments.of(args.get()[0], args.get()[1], length)));
  }

  static Stream<Arguments> provideModeAlgorithmSkipLengthParams() {
    return provideModeAlgorithmParams()
        .filter(args -> !args.get()[1].equals(Algorithm.ZSTD)) // Exclude ZSTD for skip tests
        .flatMap(
            args ->
                Stream.of(0, 1024, 16384)
                    .map(skipLength -> Arguments.of(args.get()[0], args.get()[1], skipLength)));
  }

  // Utility methods
  private byte[] getCompressedData(Algorithm algorithm) {
    return compressedDataByAlgorithm.get(algorithm);
  }

  private QatZipper.Builder createQatBuilder(Algorithm algorithm) {
    return new QatZipper.Builder().algorithm(algorithm);
  }

  private QatZipper.Builder createQatBuilder(Algorithm algorithm, Mode mode) {
    return new QatZipper.Builder().algorithm(algorithm).mode(mode);
  }

  private void performDecompressionTest(
      Mode mode, Algorithm algorithm, int bufferSize, ReadStrategy strategy) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    byte[] result = new byte[sourceData.length];

    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, createQatBuilder(algorithm, mode))) {
      strategy.read(decompressedStream, result);
    }

    assertArrayEquals(sourceData, result);
  }

  @FunctionalInterface
  private interface ReadStrategy {
    void read(QatDecompressorInputStream stream, byte[] buffer) throws IOException;
  }

  // Constructor tests
  @Test
  void testNullInputStream() {
    assertThrows(
        NullPointerException.class,
        () -> {
          try (QatDecompressorInputStream stream =
              new QatDecompressorInputStream(null, Algorithm.ZSTD)) {
            // Should throw before reaching this point
          }
        });
  }

  @Test
  void testBasicConstructors() {
    if (!QatZipper.isQatAvailable()) return;

    byte[] deflateData = getCompressedData(Algorithm.DEFLATE);

    assertAll(
        () ->
            assertDoesNotThrow(
                () -> {
                  try (ByteArrayInputStream inputStream = new ByteArrayInputStream(deflateData);
                      QatDecompressorInputStream stream =
                          new QatDecompressorInputStream(inputStream, Algorithm.DEFLATE)) {
                    // Constructor should work
                  }
                }),
        () ->
            assertDoesNotThrow(
                () -> {
                  try (ByteArrayInputStream inputStream = new ByteArrayInputStream(deflateData);
                      QatDecompressorInputStream stream =
                          new QatDecompressorInputStream(
                              inputStream, DEFAULT_BUFFER_SIZE, Algorithm.LZ4)) {
                    // Constructor should work
                  }
                }));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testConstructorWithAlgorithm(Algorithm algorithm) {
    if (!QatZipper.isQatAvailable()) return;

    byte[] compressedData = getCompressedData(algorithm);
    assertDoesNotThrow(
        () -> {
          try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
              QatDecompressorInputStream stream =
                  new QatDecompressorInputStream(inputStream, DEFAULT_BUFFER_SIZE, algorithm)) {
            // Constructor should work
          }
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testConstructorWithBuilder(Mode mode, Algorithm algorithm) {
    byte[] compressedData = getCompressedData(algorithm);
    assertDoesNotThrow(
        () -> {
          try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
              QatDecompressorInputStream stream =
                  new QatDecompressorInputStream(
                      inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {
            // Constructor should work
          }
        });
  }

  // Stream behavior tests
  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testStreamCapabilities(Algorithm algorithm) throws IOException {
    byte[] compressedData = getCompressedData(algorithm);

    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
        QatDecompressorInputStream stream =
            new QatDecompressorInputStream(
                inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, Mode.AUTO))) {

      assertAll(
          () -> assertFalse(stream.markSupported(), "Mark should not be supported"),
          () -> assertThrows(IOException.class, stream::reset, "Reset should throw IOException"));
    }
  }

  // Read operation tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadAllAtOnce(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    performDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, buffer) -> {
          int bytesRead = stream.read(buffer);
          assertEquals(buffer.length, bytesRead);
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadInChunks(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    performDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, buffer) -> {
          int totalRead = 0;
          while (totalRead < buffer.length) {
            int chunkSize = Math.min(random.nextInt(MAX_CHUNK_SIZE), buffer.length - totalRead);
            int bytesRead = stream.read(buffer, totalRead, chunkSize);
            assertEquals(chunkSize, bytesRead);
            totalRead += bytesRead;
          }
          assertEquals(buffer.length, totalRead);
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadMixedByteAndChunks(Mode mode, Algorithm algorithm, int bufferSize)
      throws IOException {
    performDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, buffer) -> {
          int totalRead = 0;
          while (totalRead < buffer.length) {
            if (totalRead % 10 == 0) {
              // Read single byte
              buffer[totalRead] = (byte) stream.read();
              totalRead += 1;
            } else {
              // Read chunk
              int chunkSize = Math.min(random.nextInt(MAX_CHUNK_SIZE), buffer.length - totalRead);
              int bytesRead = stream.read(buffer, totalRead, chunkSize);
              assertEquals(chunkSize, bytesRead);
              totalRead += bytesRead;
            }
          }
          assertEquals(buffer.length, totalRead);

          // Verify end of stream
          assertEquals(-1, stream.read());
          assertEquals(0, stream.available());
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadWithAvailable(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    performDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, buffer) -> {
          int totalRead = 0;
          while (totalRead < buffer.length) {
            int available = stream.available();
            assertTrue(available > 0, "Available should be positive");
            int bytesRead = stream.read(buffer, totalRead, available);
            assertEquals(available, bytesRead);
            totalRead += bytesRead;
          }
          assertEquals(buffer.length, totalRead);
        });
  }

  // Short data tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadShortData(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    byte[] shortData = Arrays.copyOf(sourceData, bufferSize / 2);

    // Compress the short data
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressor =
        new QatCompressorOutputStream(
            outputStream, bufferSize, createQatBuilder(algorithm, mode))) {
      compressor.write(shortData);
    }

    // Decompress and verify
    byte[] compressedData = outputStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
    byte[] result = new byte[bufferSize];

    int bytesRead;
    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(
            inputStream, bufferSize, createQatBuilder(algorithm, mode))) {
      bytesRead = decompressor.read(result);

      assertEquals(-1, decompressor.read(), "Should reach end of stream");
      assertEquals(0, decompressor.available(), "Available should be 0 at end");
    }

    assertArrayEquals(shortData, Arrays.copyOf(result, bytesRead));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadVeryShortData(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    byte[] veryShortData = Arrays.copyOf(sourceData, 1024);

    // Compress the very short data
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressor =
        new QatCompressorOutputStream(
            outputStream, bufferSize, createQatBuilder(algorithm, mode))) {
      compressor.write(veryShortData);
    }

    // Decompress with small buffer and verify
    byte[] compressedData = outputStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
    byte[] result = new byte[bufferSize];

    int bytesRead;
    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(inputStream, 4, createQatBuilder(algorithm, mode))) {
      bytesRead = decompressor.read(result);

      assertEquals(-1, decompressor.read(), "Should reach end of stream");
      assertEquals(0, decompressor.available(), "Available should be 0 at end");
    }

    assertArrayEquals(veryShortData, Arrays.copyOf(result, bytesRead));
  }

  // Skip functionality tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmSkipLengthParams")
  void testSkipFunctionality(Mode mode, Algorithm algorithm, int bytesToSkip) throws IOException {
    // Create test data: sourceData + padding + sourceData
    ByteArrayOutputStream testDataStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressor =
        new QatCompressorOutputStream(
            testDataStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {
      compressor.write(sourceData);
      compressor.write(new byte[bytesToSkip]); // Padding to skip
      compressor.write(sourceData);
    }

    // Test skipping
    byte[] compressedTestData = testDataStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedTestData);
    byte[] firstRead = new byte[sourceData.length];
    byte[] secondRead = new byte[sourceData.length];

    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(
            inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      // Read first part
      int bytesRead1 = decompressor.read(firstRead);
      assertEquals(sourceData.length, bytesRead1);

      // Skip padding
      long bytesSkipped = decompressor.skip(bytesToSkip);
      assertEquals(bytesToSkip, bytesSkipped);

      // Read second part
      int bytesRead2 = decompressor.read(secondRead);
      assertEquals(sourceData.length, bytesRead2);
    }

    assertAll(
        () -> assertArrayEquals(sourceData, firstRead),
        () -> assertArrayEquals(sourceData, secondRead));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testSkipNegative(Mode mode, Algorithm algorithm) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    byte[] result = new byte[sourceData.length];

    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(
            inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      long skipped = decompressor.skip(-5);
      assertEquals(0, skipped, "Negative skip should return 0");

      int bytesRead = decompressor.read(result);
      assertEquals(result.length, bytesRead);

      assertEquals(-1, decompressor.read());
      assertEquals(0, decompressor.available());
    }

    assertArrayEquals(sourceData, result);
  }

  // Close and error condition tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testOperationsAfterClose(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(inputStream, bufferSize, createQatBuilder(algorithm, mode));

    decompressor.close();

    byte[] buffer = new byte[sourceData.length];
    assertAll(
        () -> assertThrows(IOException.class, () -> decompressor.read(buffer)),
        () -> assertThrows(IOException.class, () -> decompressor.read()),
        () -> assertThrows(IOException.class, () -> decompressor.available()));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testDoubleClose(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(inputStream, bufferSize, createQatBuilder(algorithm, mode));

    assertDoesNotThrow(
        () -> {
          decompressor.close();
          decompressor.close(); // Should not throw
        });
  }

  // Parameter validation tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testInvalidReadParameters(Mode mode, Algorithm algorithm) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    byte[] buffer = new byte[sourceData.length];

    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(
            inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      assertAll(
          "Invalid read parameters should throw exceptions",
          () ->
              assertThrows(
                  IndexOutOfBoundsException.class, () -> decompressor.read(buffer, -33, 100)),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> decompressor.read(null, buffer.length - 1, -100)),
          () ->
              assertThrows(
                  IndexOutOfBoundsException.class,
                  () -> decompressor.read(buffer, buffer.length - 1, -100)),
          () ->
              assertThrows(
                  IndexOutOfBoundsException.class,
                  () -> decompressor.read(buffer, buffer.length - 1, 100)));
    }
  }

  // End-of-stream tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testEndOfStreamBehavior(Mode mode, Algorithm algorithm) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    byte[] firstBuffer = new byte[sourceData.length];
    byte[] secondBuffer = new byte[sourceData.length];

    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(
            inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      // Read all data
      int firstRead = decompressor.read(firstBuffer);
      assertEquals(sourceData.length, firstRead);

      // Attempt to read beyond end of stream
      int secondRead = decompressor.read(secondBuffer);
      assertEquals(-1, secondRead, "Should return -1 at end of stream");
      assertEquals(0, decompressor.available(), "Available should be 0 at end");

      // Test single byte read at end of stream
      int singleByteRead = decompressor.read();
      assertEquals(-1, singleByteRead, "Single byte read should return -1 at end");
    }

    assertArrayEquals(sourceData, firstBuffer);
  }

  // Integration test with complex read patterns
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testComplexReadPattern(Mode mode, Algorithm algorithm) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(getCompressedData(algorithm));
    byte[] result = new byte[sourceData.length];

    try (QatDecompressorInputStream decompressor =
        new QatDecompressorInputStream(
            inputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      int totalRead = 0;
      int iteration = 0;

      while (totalRead < result.length) {
        int remaining = result.length - totalRead;

        switch (iteration % 4) {
          case 0: // Single byte read
            if (remaining > 0) {
              result[totalRead] = (byte) decompressor.read();
              totalRead += 1;
            }
            break;
          case 1: // Small chunk read
            int smallChunk = Math.min(100, remaining);
            int smallRead = decompressor.read(result, totalRead, smallChunk);
            assertEquals(smallChunk, smallRead);
            totalRead += smallRead;
            break;
          case 2: // Read using available()
            int available = Math.min(decompressor.available(), remaining);
            if (available > 0) {
              int availableRead = decompressor.read(result, totalRead, available);
              assertEquals(available, availableRead);
              totalRead += availableRead;
            }
            break;
          case 3: // Random chunk read
            int randomChunk = Math.min(random.nextInt(1000) + 1, remaining);
            int randomRead = decompressor.read(result, totalRead, randomChunk);
            assertEquals(randomChunk, randomRead);
            totalRead += randomRead;
            break;
        }
        iteration++;
      }
    }

    assertArrayEquals(sourceData, result);
  }
}
