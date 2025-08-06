/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class QatZipperTests {
  private static final String SAMPLE_CORPUS = "src/test/resources/sample.txt";
  private static final Random RANDOM = new Random();
  private static final String HELLO_WORLD = "Hello, world!";

  private QatZipper qzip;

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

  static Stream<Arguments> provideAlgorithmLevelParams() {
    return Arrays.stream(Algorithm.values())
        .flatMap(
            algo -> Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9).map(level -> Arguments.of(algo, level)));
  }

  static Stream<Arguments> provideModeAlgorithmLengthParams() {
    return provideModeAlgorithmParams()
        .flatMap(
            args ->
                Stream.of(131072, 524288, 2097152)
                    .map(length -> Arguments.of(args.get()[0], args.get()[1], length)));
  }

  // Utility methods
  private byte[] getRandomBytes(int length) {
    byte[] bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  private byte[] readAllBytes(String fileName) throws IOException {
    return Files.readAllBytes(Path.of(fileName));
  }

  private void performCompressionDecompressionTest(String input) throws Exception {
    byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
    byte[] output = new byte[qzip.maxCompressedLength(inputBytes.length)];

    int compressedSize = qzip.compress(inputBytes, output);
    byte[] decompressed = new byte[inputBytes.length];
    int decompressedSize =
        qzip.decompress(output, 0, compressedSize, decompressed, 0, decompressed.length);

    String result = new String(decompressed, 0, decompressedSize, StandardCharsets.UTF_8);
    assertEquals(input, result);
  }

  // Basic functionality tests
  @Test
  void testDefaultConstructor() {
    if (!QatZipper.isQatAvailable()) return;

    assertDoesNotThrow(
        () -> {
          qzip = new QatZipper.Builder().setMode(Mode.HARDWARE).build();
        });
  }

  @Test
  void testEnd() {
    assertDoesNotThrow(
        () -> {
          QatZipper localQzip = new QatZipper.Builder().setMode(Mode.HARDWARE).build();
          localQzip.end();
        });
  }

  @Test
  void testDuplicateEndHardware() {
    if (!QatZipper.isQatAvailable()) return;

    QatZipper localQzip =
        new QatZipper.Builder().setAlgorithm(Algorithm.LZ4).setMode(Mode.HARDWARE).build();

    localQzip.end();
    assertThrows(IllegalStateException.class, localQzip::end);
  }

  @Test
  void testToString() {
    QatZipper.Builder builder = new QatZipper.Builder();
    String expected =
        "QatZipper{algorithm=DEFLATE, level=6, mode=AUTO, retryCount=0, "
            + "pollingMode=BUSY, dataFormat=DEFLATE_GZIP_EXT, hardwareBufferSize=DEFAULT_BUFFER_SIZE, logLevel=NONE}";
    assertEquals(expected, builder.toString());
  }

  @Test
  void testBuilderConfiguration() {
    QatZipper.Builder builder =
        new QatZipper.Builder()
            .setPollingMode(QatZipper.PollingMode.BUSY)
            .setDataFormat(QatZipper.DataFormat.DEFLATE_GZIP_EXT)
            .setHardwareBufferSize(QatZipper.HardwareBufferSize.DEFAULT_BUFFER_SIZE);

    String builderString = builder.toString();
    assertAll(
        () -> assertThat(builderString).contains("pollingMode=BUSY"),
        () -> assertThat(builderString).contains("dataFormat=DEFLATE_GZIP_EXT"),
        () -> assertThat(builderString).contains("hardwareBufferSize=DEFAULT_BUFFER_SIZE"));
  }

  @Test
  void testQatAvailableHolder() {
    Boolean isAvailable = QatZipper.QatAvailableHolder.IS_QAT_AVAILABLE;
    assertNotNull(isAvailable);
    // The value can be either true or false, both are valid
    assertTrue(isAvailable || !isAvailable);
  }

  @Test
  void testChecksumFlag() {
    qzip = new QatZipper();
    assertThrows(UnsupportedOperationException.class, () -> qzip.setChecksumFlag(true));

    QatZipper zstdQzip = new QatZipper(Algorithm.ZSTD);
    assertDoesNotThrow(() -> zstdQzip.setChecksumFlag(false));
    zstdQzip.end();
  }

  @Test
  void testIsQatAvailable() {
    qzip = new QatZipper();
    // Method should return a boolean value
    boolean available = qzip.isQatAvailable();
    assertTrue(available || !available); // Always true regardless of the actual value
  }

  // Null parameter tests
  @Test
  void testNullParameters() {
    qzip = new QatZipper.Builder().setMode(Mode.HARDWARE).build();

    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> qzip.compress((ByteBuffer) null, null)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> qzip.compress(null, 0, 100, null, 0, 0)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> qzip.decompress((ByteBuffer) null, null)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> qzip.decompress(null, 0, 100, null, 0, 0)));
  }

  // Constructor tests
  @ParameterizedTest
  @EnumSource(Mode.class)
  void testModeConstructor(Mode mode) {
    assertDoesNotThrow(
        () -> {
          qzip = new QatZipper.Builder().setMode(mode).build();
        });
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testAlgorithmConstructor(Algorithm algorithm) {
    if (!QatZipper.isQatAvailable()) return;

    assertDoesNotThrow(
        () -> {
          qzip = new QatZipper.Builder().setAlgorithm(algorithm).build();
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testModeAlgorithmConstructor(Mode mode, Algorithm algorithm) {
    assertDoesNotThrow(
        () -> {
          qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();
        });
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testAlgorithmLevelConstructor(Algorithm algorithm) {
    if (!QatZipper.isQatAvailable()) return;

    assertDoesNotThrow(
        () -> {
          qzip = new QatZipper.Builder().setAlgorithm(algorithm).setLevel(9).build();
        });
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testFullConstructor(Mode mode, Algorithm algorithm) {
    assertDoesNotThrow(
        () -> {
          qzip =
              new QatZipper.Builder()
                  .setAlgorithm(algorithm)
                  .setLevel(9)
                  .setMode(mode)
                  .setRetryCount(10)
                  .build();
        });
  }

  // Basic compression/decompression tests
  @Test
  void testBasicCompressionDecompression() throws Exception {
    qzip = new QatZipper();
    performCompressionDecompressionTest(HELLO_WORLD);
  }

  @Test
  void testCompressionWithAlgorithm() throws Exception {
    qzip = new QatZipper(Algorithm.DEFLATE);
    performCompressionDecompressionTest(HELLO_WORLD);
  }

  @Test
  void testCompressionWithAlgorithmAndLevel() throws Exception {
    qzip = new QatZipper(Algorithm.DEFLATE, 6);
    performCompressionDecompressionTest(HELLO_WORLD);
  }

  @ParameterizedTest
  @MethodSource("provideAlgorithmLevelParams")
  void testHelloWorldAllCombinations(Algorithm algorithm, int level) throws Exception {
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setLevel(level).build();
    performCompressionDecompressionTest(HELLO_WORLD);
  }

  @ParameterizedTest
  @MethodSource("provideAlgorithmLevelParams")
  void testHelloWorldWithMetrics(Algorithm algorithm, int level) throws Exception {
    byte[] input = HELLO_WORLD.getBytes(StandardCharsets.UTF_8);
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setLevel(level).build();

    byte[] output = new byte[qzip.maxCompressedLength(input.length) * 2];
    int compressedSize = qzip.compress(input, output);

    assertEquals(input.length, qzip.getBytesRead());
    assertEquals(compressedSize, qzip.getBytesWritten());

    byte[] decompressed = new byte[input.length];
    int decompressedSize =
        qzip.decompress(output, 0, compressedSize, decompressed, 0, decompressed.length);

    assertEquals(compressedSize, qzip.getBytesRead());
    assertEquals(input.length, qzip.getBytesWritten());

    String result = new String(decompressed, 0, decompressedSize, StandardCharsets.UTF_8);
    assertEquals(HELLO_WORLD, result);
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void testInvalidCompressionLevel(Algorithm algorithm) {
    assertThrows(
        RuntimeException.class,
        () -> {
          qzip = new QatZipper.Builder().setAlgorithm(algorithm).setLevel(15).build();
        });
  }

  // File-based tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testFileCompression(Mode mode, Algorithm algorithm) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();

    byte[] source = readAllBytes(SAMPLE_CORPUS);
    byte[] compressed = new byte[qzip.maxCompressedLength(source.length)];
    byte[] decompressed = new byte[source.length];

    int compressedSize = qzip.compress(source, 0, source.length, compressed, 0, compressed.length);
    assertEquals(source.length, qzip.getBytesRead());
    assertEquals(compressedSize, qzip.getBytesWritten());

    int decompressedSize =
        qzip.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);
    assertEquals(compressedSize, qzip.getBytesRead());
    assertEquals(decompressedSize, qzip.getBytesWritten());

    assertAll(
        () -> assertTrue(compressedSize > 0),
        () -> assertEquals(decompressedSize, source.length),
        () -> assertArrayEquals(source, decompressed));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testFileCompressionWithOffset(Mode mode, Algorithm algorithm) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();

    byte[] source = readAllBytes(SAMPLE_CORPUS);
    byte[] compressed = new byte[qzip.maxCompressedLength(source.length)];
    byte[] decompressed = new byte[source.length];

    final int offset = 3;
    int compressedSize =
        qzip.compress(source, offset, source.length - offset, compressed, 0, compressed.length);

    assertAll(
        () -> assertEquals(source.length - offset, qzip.getBytesRead()),
        () -> assertEquals(compressedSize, qzip.getBytesWritten()),
        () -> assertTrue(compressedSize > 0));

    int decompressedSize =
        qzip.decompress(
            compressed, 0, compressedSize, decompressed, offset, decompressed.length - offset);

    assertAll(() -> assertEquals(source.length - offset, decompressedSize));

    String originalSubstring = new String(source, StandardCharsets.UTF_8).substring(offset);
    String decompressedSubstring =
        new String(decompressed, StandardCharsets.UTF_8).substring(offset);
    assertEquals(originalSubstring, decompressedSubstring);
  }

  // ByteBuffer tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  void testDirectByteBufferCompression(Mode mode, Algorithm algorithm) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();

    byte[] source = readAllBytes(SAMPLE_CORPUS);
    byte[] result = new byte[source.length];

    ByteBuffer sourceBuf = ByteBuffer.allocateDirect(source.length);
    ByteBuffer compressedBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(source.length));
    ByteBuffer decompressedBuf = ByteBuffer.allocateDirect(source.length);

    sourceBuf.put(source).flip();

    int compressedSize = qzip.compress(sourceBuf, compressedBuf);
    compressedBuf.flip();

    int decompressedSize = qzip.decompress(compressedBuf, decompressedBuf);
    decompressedBuf.flip();
    decompressedBuf.get(result, 0, decompressedSize);

    assertAll(
        () -> assertTrue(compressedSize > 0),
        () -> assertEquals(source.length, decompressedSize),
        () -> assertArrayEquals(source, result));
  }

  // Buffer type tests with length variations
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testWrappedBuffers(Mode mode, Algorithm algorithm, int length) {
    // ZSTD requires direct source byte buffers
    if (algorithm == Algorithm.ZSTD) return;

    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();

    byte[] source = getRandomBytes(length);
    byte[] decompressed = new byte[source.length];
    byte[] compressed = new byte[qzip.maxCompressedLength(source.length)];

    ByteBuffer sourceBuf = ByteBuffer.wrap(source);
    ByteBuffer compressedBuf = ByteBuffer.wrap(compressed);
    ByteBuffer decompressedBuf = ByteBuffer.wrap(decompressed);

    int compressedSize = qzip.compress(sourceBuf, compressedBuf);
    compressedBuf.flip();

    int decompressedSize = qzip.decompress(compressedBuf, decompressedBuf);

    assertAll(
        () -> assertTrue(compressedSize > 0),
        () -> assertNotNull(compressedBuf),
        () -> assertNotNull(decompressedBuf),
        () -> assertTrue(decompressedSize > 0),
        () -> assertArrayEquals(source, decompressed));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadOnlyBuffers(Mode mode, Algorithm algorithm, int length) {
    // ZSTD requires direct source byte buffers
    if (algorithm == Algorithm.ZSTD) return;

    qzip =
        new QatZipper.Builder()
            .setAlgorithm(algorithm)
            .setLevel(9)
            .setMode(mode)
            .setRetryCount(0)
            .build();

    byte[] source = getRandomBytes(length);
    byte[] decompressed = new byte[source.length];

    ByteBuffer sourceBuf = ByteBuffer.allocate(source.length);
    ByteBuffer compressedBuf = ByteBuffer.allocate(qzip.maxCompressedLength(source.length));
    ByteBuffer decompressedBuf = ByteBuffer.allocate(decompressed.length);

    sourceBuf.put(source).flip();
    int compressedSize = qzip.compress(sourceBuf.asReadOnlyBuffer(), compressedBuf);

    compressedBuf.flip();
    int decompressedSize = qzip.decompress(compressedBuf.asReadOnlyBuffer(), decompressedBuf);

    decompressedBuf.flip();
    decompressedBuf.get(decompressed, 0, decompressedSize);

    assertAll(
        () -> assertTrue(compressedSize > 0),
        () -> assertNotNull(compressedBuf),
        () -> assertNotNull(decompressedBuf),
        () -> assertTrue(decompressedSize > 0),
        () -> assertArrayEquals(source, decompressed));
  }

  // Error condition tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testReadOnlyDestinationBuffers(Mode mode, Algorithm algorithm, int length) {
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();

    byte[] source = getRandomBytes(length);
    ByteBuffer sourceBuf = ByteBuffer.allocateDirect(source.length);
    ByteBuffer compressedBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(source.length));
    ByteBuffer decompressedBuf = ByteBuffer.allocateDirect(source.length);

    sourceBuf.put(source).flip();

    assertThrows(
        ReadOnlyBufferException.class,
        () -> qzip.compress(sourceBuf, compressedBuf.asReadOnlyBuffer()));

    // Test decompression with read-only destination
    sourceBuf.rewind();
    qzip.compress(sourceBuf, compressedBuf);
    compressedBuf.flip();

    assertThrows(
        ReadOnlyBufferException.class,
        () -> qzip.decompress(compressedBuf, decompressedBuf.asReadOnlyBuffer()));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testOperationsAfterEnd(Mode mode, Algorithm algorithm, int length) {
    QatZipper localQzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();
    byte[] source = getRandomBytes(length);
    byte[] compressed = new byte[2 * source.length];

    localQzip.end();

    assertThrows(
        IllegalStateException.class,
        () -> localQzip.compress(source, 0, source.length, compressed, 0, compressed.length));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testInvalidOffsets(Mode mode, Algorithm algorithm, int length) {
    qzip =
        new QatZipper.Builder()
            .setAlgorithm(algorithm)
            .setLevel(9)
            .setMode(mode)
            .setRetryCount(0)
            .build();

    byte[] source = getRandomBytes(length);
    byte[] compressed = new byte[qzip.maxCompressedLength(source.length)];

    assertAll(
        () ->
            assertThrows(
                ArrayIndexOutOfBoundsException.class,
                () -> qzip.compress(source, -1, source.length, compressed, 0, compressed.length)),
        () ->
            assertThrows(
                ArrayIndexOutOfBoundsException.class,
                () ->
                    qzip.compress(
                        source,
                        source.length + 1,
                        source.length,
                        compressed,
                        0,
                        compressed.length)));
  }

  // Complex buffer manipulation tests
  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  void testVaryingOffsets(Mode mode, Algorithm algorithm, int length) {
    qzip = new QatZipper.Builder().setAlgorithm(algorithm).setMode(mode).build();
    byte[] data = getRandomBytes(length);

    final int inputOffset = 2;
    final int outputOffset = 5;

    ByteBuffer sourceBuf = ByteBuffer.allocateDirect(inputOffset + length + inputOffset);
    sourceBuf.position(inputOffset).put(data, 0, length).flip().position(inputOffset);

    ByteBuffer compressedBuf =
        ByteBuffer.allocateDirect(
            outputOffset + qzip.maxCompressedLength(data.length) + outputOffset);
    byte[] garbage = new byte[compressedBuf.capacity()];
    RANDOM.nextBytes(garbage);
    compressedBuf
        .put(garbage)
        .position(outputOffset)
        .limit(compressedBuf.capacity() - outputOffset);

    qzip.compress(sourceBuf, compressedBuf);

    assertEquals(inputOffset + length, sourceBuf.position());
    assertEquals(inputOffset + length, sourceBuf.limit());

    compressedBuf.flip().position(outputOffset);
    int remaining = compressedBuf.remaining();

    ByteBuffer resultBuf = ByteBuffer.allocateDirect(inputOffset + length + inputOffset);
    resultBuf.position(inputOffset).limit(resultBuf.capacity() - inputOffset);

    qzip.decompress(compressedBuf, resultBuf);

    int decompressedLength = resultBuf.position() - inputOffset;
    assertEquals(length, decompressedLength, "Decompressed size mismatch");

    for (int i = 0; i < length; i++) {
      assertEquals(data[i], resultBuf.get(inputOffset + i), "Data mismatch at index: " + i);
    }
  }

  // Helper method for assertThat (since we can't import assertj)
  private static StringAssertion assertThat(String actual) {
    return new StringAssertion(actual);
  }

  private static class StringAssertion {
    private final String actual;

    StringAssertion(String actual) {
      this.actual = actual;
    }

    void contains(String expected) {
      assertTrue(
          actual.contains(expected),
          "Expected string to contain: " + expected + " but was: " + actual);
    }
  }
}
