/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class QatCompressorOutputStreamTests {
  private static final String SAMPLE_CORPUS = "src/test/resources/sample.txt";
  private static final int DEFAULT_BUFFER_SIZE = 16 * 1024;
  private static final int MAX_CHUNK_SIZE = 20 * 1024;

  private static byte[] sourceData;
  private QatZipper qzip;
  private final Random random = new Random();

  @BeforeAll
  static void setup() throws IOException {
    sourceData = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
  }

  @AfterEach
  void cleanupSession() {
    if (qzip != null) {
      qzip.end();
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
        .flatMap(
            args ->
                Stream.of(16384, 65536, 524288, 1048576)
                    .map(length -> Arguments.of(args.get()[0], args.get()[1], length)));
  }

  static Stream<Arguments> provideAlgorithmLevelParams() {
    return Arrays.stream(Algorithm.values())
        .flatMap(
            algorithm ->
                Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9).map(level -> Arguments.of(algorithm, level)));
  }

  // Utility methods
  private QatZipper.Builder createQatBuilder(Algorithm algorithm) {
    return new QatZipper.Builder().algorithm(algorithm);
  }

  private QatZipper.Builder createQatBuilder(Algorithm algorithm, Mode mode) {
    return new QatZipper.Builder().algorithm(algorithm).mode(mode);
  }

  private void performCompressionDecompressionTest(
      Mode mode, Algorithm algorithm, int bufferSize, WriteStrategy strategy) throws IOException {
    qzip = createQatBuilder(algorithm, mode).build();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, bufferSize, createQatBuilder(algorithm, mode))) {

      strategy.write(compressedStream, sourceData);
    }

    byte[] compressedData = outputStream.toByteArray();
    byte[] decompressed = new byte[sourceData.length];

    qzip.decompress(compressedData, 0, compressedData.length, decompressed, 0, decompressed.length);
    assertArrayEquals(sourceData, decompressed);
  }

  @FunctionalInterface
  private interface WriteStrategy {
    void write(QatCompressorOutputStream stream, byte[] data) throws IOException;
  }

  // Constructor tests
  @Test
  void testNullOutputStream() {
    assertThrows(
        NullPointerException.class,
        () -> {
          try (QatCompressorOutputStream stream =
              new QatCompressorOutputStream(null, Algorithm.DEFLATE)) {
            // Should throw before reaching this point
          }
        });
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testSimpleConstructor(Algorithm algorithm) throws IOException {
    if (!QatZipper.isQatAvailable()) return;

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        QatCompressorOutputStream compressedStream =
            new QatCompressorOutputStream(outputStream, algorithm)) {
      // Constructor should work without exceptions
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testConstructorWithBufferSize(Algorithm algorithm) throws IOException {
    if (!QatZipper.isQatAvailable()) return;

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        QatCompressorOutputStream compressedStream =
            new QatCompressorOutputStream(outputStream, DEFAULT_BUFFER_SIZE, algorithm)) {
      // Constructor should work without exceptions
    }
  }

  @ParameterizedTest
  @MethodSource("provideAlgorithmLevelParams")
  void testConstructorWithBuilder(Algorithm algorithm, int level) throws IOException {
    if (!QatZipper.isQatAvailable()) return;

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        QatCompressorOutputStream compressedStream =
            new QatCompressorOutputStream(
                outputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm).level(level))) {
      // Constructor should work without exceptions
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testConstructorWithModeAndAlgorithm(Mode mode, Algorithm algorithm) throws IOException {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        QatCompressorOutputStream compressedStream =
            new QatCompressorOutputStream(
                outputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {
      // Constructor should work without exceptions
    }
  }

  @Test
  void testInvalidBufferSize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
              QatCompressorOutputStream compressedStream =
                  new QatCompressorOutputStream(outputStream, 0, Algorithm.LZ4)) {
            // Should throw before reaching this point
          }
        });
  }

  // Write operation tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testWriteAllAtOnce(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    performCompressionDecompressionTest(
        mode, algorithm, bufferSize, (stream, data) -> stream.write(data));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testWriteInChunks(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    performCompressionDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, data) -> {
          int totalWritten = 0;
          while (totalWritten < data.length) {
            int chunkSize = Math.min(random.nextInt(MAX_CHUNK_SIZE), data.length - totalWritten);
            stream.write(data, totalWritten, chunkSize);
            totalWritten += chunkSize;
          }
          assertEquals(data.length, totalWritten);
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testWriteMixedByteAndChunks(Mode mode, Algorithm algorithm, int bufferSize)
      throws IOException {
    performCompressionDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, data) -> {
          int totalWritten = 0;
          while (totalWritten < data.length) {
            if (totalWritten % 10 == 0) {
              // Write single byte
              stream.write(data[totalWritten]);
              totalWritten += 1;
            } else {
              // Write chunk
              int chunkSize = Math.min(random.nextInt(MAX_CHUNK_SIZE), data.length - totalWritten);
              stream.write(data, totalWritten, chunkSize);
              totalWritten += chunkSize;
            }
          }
          assertEquals(data.length, totalWritten);
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testWriteWithFlush(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    performCompressionDecompressionTest(
        mode,
        algorithm,
        bufferSize,
        (stream, data) -> {
          int totalWritten = 0;
          while (totalWritten < data.length) {
            if (totalWritten > 0 && totalWritten % 10 == 0) {
              stream.flush();
            }
            int chunkSize = Math.min(random.nextInt(MAX_CHUNK_SIZE), data.length - totalWritten);
            stream.write(data, totalWritten, chunkSize);
            totalWritten += chunkSize;
          }
          assertEquals(data.length, totalWritten);
        });
  }

  // Close and error condition tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testWriteAfterClose(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(outputStream, bufferSize, createQatBuilder(algorithm, mode));

    compressedStream.close();

    assertAll(
        () -> assertThrows(IOException.class, () -> compressedStream.write(sourceData)),
        () -> assertThrows(IOException.class, () -> compressedStream.write(sourceData[0])),
        () -> assertThrows(IOException.class, () -> compressedStream.flush()));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testDoubleClose(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(outputStream, bufferSize, createQatBuilder(algorithm, mode));

    assertDoesNotThrow(
        () -> {
          compressedStream.close();
          compressedStream.close(); // Should not throw
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testFlushOnClose(Mode mode, Algorithm algorithm, int bufferSize) throws IOException {
    QatZipper localQzip = createQatBuilder(algorithm, mode).build();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    byte[] preCloseData;
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, bufferSize, createQatBuilder(algorithm, mode))) {
      compressedStream.write(sourceData);
      preCloseData = outputStream.toByteArray();
      // Stream will be closed automatically, triggering flush
    }

    byte[] postCloseData = outputStream.toByteArray();
    assertFalse(Arrays.equals(postCloseData, preCloseData), "Data should change after close/flush");

    // Verify decompression works
    byte[] decompressed = new byte[sourceData.length];
    localQzip.decompress(
        postCloseData, 0, postCloseData.length, decompressed, 0, decompressed.length);
    assertArrayEquals(sourceData, decompressed);

    localQzip.end();
  }

  // Parameter validation tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testInvalidWriteParameters(Mode mode, Algorithm algorithm) throws IOException {
    qzip = createQatBuilder(algorithm, mode).build();

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        QatCompressorOutputStream compressedStream =
            new QatCompressorOutputStream(
                outputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      assertAll(
          "Invalid write parameters should throw exceptions",
          () ->
              assertThrows(NullPointerException.class, () -> compressedStream.write(null, 33, 100)),
          () ->
              assertThrows(
                  IndexOutOfBoundsException.class,
                  () -> compressedStream.write(sourceData, -33, 100)),
          () ->
              assertThrows(
                  IndexOutOfBoundsException.class,
                  () -> compressedStream.write(sourceData, sourceData.length - 1, -100)),
          () ->
              assertThrows(
                  IndexOutOfBoundsException.class,
                  () -> compressedStream.write(sourceData, sourceData.length - 1, 100)));
    }
  }

  // Integration test combining multiple features
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testComplexWritePattern(Mode mode, Algorithm algorithm) throws IOException {
    qzip = createQatBuilder(algorithm, mode).build();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, DEFAULT_BUFFER_SIZE, createQatBuilder(algorithm, mode))) {

      // Write in various patterns to test robustness
      int written = 0;
      int iteration = 0;

      while (written < sourceData.length) {
        int remaining = sourceData.length - written;

        switch (iteration % 4) {
          case 0: // Single byte
            if (remaining > 0) {
              compressedStream.write(sourceData[written]);
              written += 1;
            }
            break;
          case 1: // Small chunk
            int smallChunk = Math.min(100, remaining);
            compressedStream.write(sourceData, written, smallChunk);
            written += smallChunk;
            break;
          case 2: // Large chunk with flush
            int largeChunk = Math.min(5000, remaining);
            compressedStream.write(sourceData, written, largeChunk);
            written += largeChunk;
            compressedStream.flush();
            break;
          case 3: // Random chunk
            int randomChunk = Math.min(random.nextInt(1000) + 1, remaining);
            compressedStream.write(sourceData, written, randomChunk);
            written += randomChunk;
            break;
        }
        iteration++;
      }
    }

    // Verify compression worked correctly
    byte[] compressedData = outputStream.toByteArray();
    byte[] decompressed = new byte[sourceData.length];

    qzip.decompress(compressedData, 0, compressedData.length, decompressed, 0, decompressed.length);
    assertArrayEquals(sourceData, decompressed);
  }
}
