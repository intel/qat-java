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

public class QatInputStreamTests {
  private static byte[] src;
  private static byte[] deflateBytes;
  private static byte[] lz4Bytes;
  private Random rnd = new Random();

  @BeforeAll
  public static void setup() throws IOException {
    src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatOutputStream compressedStream =
        new QatOutputStream(outputStream, 16 * 1024, Algorithm.LZ4)) {
      compressedStream.write(src);
    }
    lz4Bytes = outputStream.toByteArray();
    ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream();
    try (QatOutputStream compressedStream =
        new QatOutputStream(outputStream2, 16 * 1024, Algorithm.DEFLATE)) {
      compressedStream.write(src);
    }
    deflateBytes = outputStream2.toByteArray();
  }

  public static Stream<Arguments> provideModeAlgorithmParams() {
    return QatTestSuite.FORCE_HARDWARE
        ? Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
            Arguments.of(Mode.AUTO, Algorithm.LZ4),
            Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE),
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4))
        : Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE), Arguments.of(Mode.AUTO, Algorithm.LZ4));
  }

  public static Stream<Arguments> provideModeAlgorithmLengthParams() {
    return QatTestSuite.FORCE_HARDWARE
        ? Stream.of(
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
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 1048576))
        : Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 65536),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 524288),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1048576),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 65536),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 524288),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 1048576));
  }

  public static Stream<Arguments> provideModeAlgorithmSkipLengthParams() {
    return QatTestSuite.FORCE_HARDWARE
        ? Stream.of(
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
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 16384))
        : Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 0),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1024),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 0),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 1024),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384));
  }

  @Test
  public void testConstructor() {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(deflateBytes);
    byte[] result = new byte[src.length];
    try {
      try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testOneArgConstructor() {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(deflateBytes);
    byte[] result = new byte[src.length];
    try {
      try (QatInputStream decompressedStream = new QatInputStream(inputStream)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testConstructor1(Algorithm algo) {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try {
      try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testConstructor2(Mode mode, Algorithm algo) {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try {
      try (QatInputStream decompressedStream =
          new QatInputStream(inputStream, 16 * 1024, algo, mode)) {}
    } catch (IOException | IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testReset(Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {
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
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {
      assertFalse(decompressedStream.markSupported());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAll1(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode)) {
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAll3(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode)) {
      int i;
      int len = 0;
      for (i = 0; i < result.length; i += len) {
        len = Math.min(rnd.nextInt(20 * 1024), result.length - i);
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
    try (QatOutputStream outputStream = new QatOutputStream(outStream, bufferSize, algo, mode)) {
      outputStream.write(newSrc);
    }
    byte[] compressedBytes = outStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBytes);
    byte[] result = new byte[bufferSize];
    int read = 0;
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode)) {
      read = decompressedStream.read(result);
    }
    assertTrue(Arrays.equals(newSrc, Arrays.copyOf(result, read)));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadShort2(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    byte[] newSrc = Arrays.copyOf(src, bufferSize);
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    try (QatOutputStream outputStream = new QatOutputStream(outStream, bufferSize, algo, mode)) {
      outputStream.write(newSrc);
    }
    byte[] compressedBytes = outStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBytes);
    byte[] result = new byte[bufferSize];
    int read = 0;
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode)) {
      read = decompressedStream.read(result);
    }
    assertTrue(Arrays.equals(newSrc, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadByte(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode)) {
      int i;
      int len = 0;
      for (i = 0; i < result.length; i += len) {
        if (i % 10 == 0) { // doReadByte
          len = 1;
          result[i] = (byte) decompressedStream.read();
        } else {
          len = Math.min(rnd.nextInt(20 * 1024), result.length - i);
          int read = decompressedStream.read(result, i, len);
          assertEquals(len, read);
        }
      }
      assertEquals(result.length, i);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAvailable(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode)) {
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
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    QatInputStream decompressedStream = new QatInputStream(inputStream, bufferSize, algo, mode);
    decompressedStream.close();
    try {
      decompressedStream.read(result);
      fail("Failed to catch IOException!");
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAfterClose(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    QatInputStream decompressedStream = new QatInputStream(inputStream, bufferSize, algo, mode);
    decompressedStream.close();
    try {
      decompressedStream.read();
      fail("Failed to catch IOException!");
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamAvailableAfterClose(Mode mode, Algorithm algo, int bufferSize)
      throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    QatInputStream decompressedStream = new QatInputStream(inputStream, bufferSize, algo, mode);
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
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    QatInputStream decompressedStream = new QatInputStream(inputStream, bufferSize, algo, mode);
    decompressedStream.close();
    decompressedStream.close();
    assertTrue(true);
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmSkipLengthParams")
  public void testInputStreamSkip(Mode mode, Algorithm algo, int bytesToSkip) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatOutputStream compressedStream =
        new QatOutputStream(outputStream, 16 * 1024, algo, mode)) {
      compressedStream.write(src);
      compressedStream.write(new byte[bytesToSkip]);
      compressedStream.write(src);
    }
    byte[] srcBytes = outputStream.toByteArray();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(srcBytes);
    byte[] result1 = new byte[src.length];
    byte[] result2 = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
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
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
      int skipped = (int) decompressedStream.skip(-5);
      assertEquals(0, skipped);
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadBadOffset(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
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
  public void testInputStreamReadBadLength(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
      try {
        decompressedStream.read(result, result.length - 1, 100);
        fail("Failed to catch IndexOutOfBoundsException");
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamRead3EOF(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    byte[] result2 = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
      read = decompressedStream.read(result2);
      assertEquals(-1, read);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadEOF(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
        new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
      read = decompressedStream.read();
      assertEquals(-1, read);
    }
    assertTrue(Arrays.equals(src, result));
  }
}
