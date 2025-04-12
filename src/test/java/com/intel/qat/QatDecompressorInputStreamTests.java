/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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
  private static byte[] src;
  private static byte[] deflateBytes;
  private static byte[] lz4Bytes;
  private static byte[] zstdBytes;
  private Random RANDOM = new Random();

  @BeforeAll
  public static void setup() throws IOException {
    src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(outputStream, 16 * 1024, Algorithm.LZ4)) {
      compressedStream.write(src);
    }
    lz4Bytes = outputStream.toByteArray();
    ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(outputStream2, 16 * 1024, Algorithm.DEFLATE)) {
      compressedStream.write(src);
    }
    deflateBytes = outputStream2.toByteArray();
    ByteArrayOutputStream outputStream3 = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(outputStream3, 16 * 1024, Algorithm.ZSTD)) {
      compressedStream.write(src);
    }
    zstdBytes = outputStream3.toByteArray();
  }

  public static byte[] selectInputBytes(Algorithm algo) {
    switch (algo) {
      case DEFLATE:
        return deflateBytes;
      case LZ4:
        return lz4Bytes;
      case ZSTD:
        return zstdBytes;
      default:
        throw new RuntimeException();
    }
  }

  public static Stream<Arguments> provideModeAlgorithmParams() {
    if (QatZipper.isQatAvailable()) {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
          Arguments.of(Mode.AUTO, Algorithm.LZ4),
          Arguments.of(Mode.AUTO, Algorithm.ZSTD),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4),
          Arguments.of(Mode.HARDWARE, Algorithm.ZSTD));
    } else {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
          Arguments.of(Mode.AUTO, Algorithm.LZ4),
          Arguments.of(Mode.AUTO, Algorithm.ZSTD));
    }
  }

  public static Stream<Arguments> provideModeAlgorithmLengthParams() {
    if (QatZipper.isQatAvailable()) {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 65536),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 524288),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1048576),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 65536),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 524288),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 1048576),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 16384),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 65536),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 524288),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 1048576),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 16384),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 65536),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 524288),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 1048576));
    } else {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 65536),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 524288),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1048576),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 65536),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 524288),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 1048576));
    }
  }

  public static Stream<Arguments> provideModeAlgorithmSkipLengthParams() {
    if (QatZipper.isQatAvailable()) {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 0),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1024),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 0),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 1024),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 0),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 1024),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 16384),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 0),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 1024),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 16384));
    } else {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 0),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1024),
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 0),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 1024),
          Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384));
    }
  }

  @Test
  public void testNullStream() {
    ByteArrayInputStream inputStream = null;
    try {
      try (QatDecompressorInputStream decompressedStream =
          new QatDecompressorInputStream(inputStream, Algorithm.ZSTD)) {}
      fail("Failed to catch NullPointerException");
    } catch (NullPointerException | IOException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testConstructor() {
    if (!QatZipper.isQatAvailable()) {
      return;
    }
    ByteArrayInputStream inputStream = new ByteArrayInputStream(deflateBytes);
    try {
      try (QatDecompressorInputStream decompressedStream =
          new QatDecompressorInputStream(inputStream, 16 * 1024, Algorithm.LZ4)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testTwoArgConstructor() {
    if (!QatZipper.isQatAvailable()) {
      return;
    }
    ByteArrayInputStream inputStream = new ByteArrayInputStream(deflateBytes);
    try {
      try (QatDecompressorInputStream decompressedStream =
          new QatDecompressorInputStream(inputStream, Algorithm.DEFLATE)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testConstructor1(Algorithm algo) {
    if (!QatZipper.isQatAvailable()) {
      return;
    }
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    try {
      try (QatDecompressorInputStream decompressedStream =
          new QatDecompressorInputStream(inputStream, 16 * 1024, algo)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testConstructor2(Mode mode, Algorithm algo) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    try {
      try (QatDecompressorInputStream decompressedStream =
          new QatDecompressorInputStream(
              inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testReset(Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream,
            16 * 1024,
            new QatZipper.Builder().setAlgorithm(algo).setMode(Mode.AUTO))) {
      try {
        decompressedStream.reset();
        fail("Failed to catch IOException!");
      } catch (IOException ioe) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testMarkSupported(Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream,
            16 * 1024,
            new QatZipper.Builder().setAlgorithm(algo).setMode(Mode.AUTO))) {
      assertFalse(decompressedStream.markSupported());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAll1(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAll3(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int i;
      int len = 0;
      for (i = 0; i < result.length; i += len) {
        len = Math.min(RANDOM.nextInt(20 * 1024), result.length - i);
        int read = decompressedStream.read(result, i, len);
        assertEquals(len, read);
      }
      assertEquals(result.length, i);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadShort(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    byte[] newSrc = Arrays.copyOf(src, bufferSize / 2);
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream outputStream =
        new QatCompressorOutputStream(
            outStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      outputStream.write(newSrc);
    }
    byte[] compressedBytes = outStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBytes);
    byte[] result = new byte[bufferSize];
    int read = 0;
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      read = decompressedStream.read(result);
      assertEquals(-1, decompressedStream.read());
      assertEquals(0, decompressedStream.available());
    }
    assertTrue(Arrays.equals(newSrc, Arrays.copyOf(result, read)));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadShort2(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    byte[] newSrc = Arrays.copyOf(src, 1024);
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream outputStream =
        new QatCompressorOutputStream(
            outStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      outputStream.write(newSrc);
    }
    byte[] compressedBytes = outStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBytes);
    byte[] result = new byte[bufferSize];
    int read = 0;
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 4, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      read = decompressedStream.read(result);
      assertEquals(-1, decompressedStream.read());
      assertEquals(0, decompressedStream.available());
    }
    assertTrue(Arrays.equals(newSrc, Arrays.copyOf(result, read)));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadByte(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int i;
      int len = 0;
      for (i = 0; i < result.length; i += len) {
        if (i % 10 == 0) { // doReadByte
          len = 1;
          result[i] = (byte) decompressedStream.read();
        } else {
          len = Math.min(RANDOM.nextInt(20 * 1024), result.length - i);
          int read = decompressedStream.read(result, i, len);
          assertEquals(len, read);
        }
      }
      assertEquals(result.length, i);
      assertEquals(-1, decompressedStream.read());
      assertEquals(0, decompressedStream.available());
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAvailable(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int i;
      int len = 0;
      for (i = 0; i < result.length; i += len) {
        len = decompressedStream.available();
        assertTrue(len > 0);
        int read = decompressedStream.read(result, i, len);
        assertEquals(len, read);
      }
      assertEquals(result.length, i);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamClose(Mode mode, Algorithm algo, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    decompressedStream.close();
    try {
      int r = decompressedStream.read(result);
      fail("Failed to catch IOException. Return was " + r);
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAfterClose(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    decompressedStream.close();
    try {
      int r = decompressedStream.read();
      fail("Failed to catch IOException. Returned was " + r);
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamAvailableAfterClose(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    decompressedStream.close();
    try {
      decompressedStream.available();
      fail("Failed to catch IOException!");
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamDoubleClose(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, bufferSize, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    decompressedStream.close();
    decompressedStream.close();
    assertTrue(true);
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmSkipLengthParams")
  public void testInputStreamSkip(Mode mode, Algorithm algo, int bytesToSkip) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      compressedStream.write(src);
      compressedStream.write(new byte[bytesToSkip]);
      compressedStream.write(src);
    }
    byte[] srcBytes = outputStream.toByteArray();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(srcBytes);
    byte[] result1 = new byte[src.length];
    byte[] result2 = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int read = decompressedStream.read(result1);
      assertTrue(result1.length == read);
      long skipped = decompressedStream.skip(bytesToSkip);
      assertTrue(skipped == bytesToSkip);
      read = decompressedStream.read(result2);
      assertTrue(result2.length == read);
    }
    assertTrue(Arrays.equals(src, result1));
    assertTrue(Arrays.equals(src, result2));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamSkipNegative(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int skipped = (int) decompressedStream.skip(-5);
      assertEquals(0, skipped);
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
      assertEquals(-1, decompressedStream.read());
      assertEquals(0, decompressedStream.available());
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadBadOffset(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        decompressedStream.read(result, -33, 100);
        fail("Failed to catch IndexOutOfBoundsException");
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadNullArray(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        decompressedStream.read(null, result.length - 1, -100);
        fail("Failed to catch NullPointerException");
      } catch (NullPointerException npe) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadBadLength(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        decompressedStream.read(result, result.length - 1, -100);
        fail("Failed to catch IndexOutOfBoundsException");
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadBadOffsetAndLength(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        int r = decompressedStream.read(result, result.length - 1, 100);
        fail("Failed to catch IndexOutOfBoundsException. Returned " + r);
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamRead3EOF(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    byte[] result2 = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
      read = decompressedStream.read(result2);
      assertEquals(-1, read);
      assertEquals(0, decompressedStream.available());
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadEOF(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(selectInputBytes(algo));
    byte[] result = new byte[src.length];
    try (QatDecompressorInputStream decompressedStream =
        new QatDecompressorInputStream(
            inputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
      read = decompressedStream.read();
      assertEquals(-1, read);
      assertEquals(0, decompressedStream.available());
    }
    assertTrue(Arrays.equals(src, result));
  }
}
