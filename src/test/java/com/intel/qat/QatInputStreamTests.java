/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

public class QatInputStreamTests {
  private static byte[] src;
  private static byte[] deflateBytes;
  private static byte[] lz4Bytes;
  private Random rnd = new Random();

  @BeforeAll
  public static void setup() throws IOException {
    src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
    QatZipper qzip = new QatZipper(Algorithm.DEFLATE);
    deflateBytes = new byte[qzip.maxCompressedLength(src.length)];
    int deflateLen =
        qzip.compress(src, 0, src.length, deflateBytes, 0, deflateBytes.length);
    deflateBytes = Arrays.copyOfRange(deflateBytes, 0, deflateLen);
    qzip.end(); 
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatOutputStream compressedStream =
             new QatOutputStream(outputStream, 16 * 1024, Algorithm.LZ4)) {
      compressedStream.write(src);
    }
    lz4Bytes = outputStream.toByteArray();
  }

  public static Stream<Arguments> provideModeAlgorithmParams() {
    return QatTestSuite.FORCE_HARDWARE
        ? Stream.of(Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
            Arguments.of(Mode.AUTO, Algorithm.LZ4),
            Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE),
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4))
        : Stream.of(Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
            Arguments.of(Mode.AUTO, Algorithm.LZ4));
  }

    public static Stream<Arguments> provideModeAlgorithmLengthParams() {
    return QatTestSuite.FORCE_HARDWARE
        ? Stream.of(Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
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
        : Stream.of(Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
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
        ? Stream.of(Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 0),
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
        : Stream.of(Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 0),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 1024),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 16384),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 0),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 1024),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 16384));
  }

  public static Stream<Arguments> provideAlgorithmLevelParams() {
    return Stream.of(Arguments.of(Algorithm.DEFLATE, 1),
        Arguments.of(Algorithm.DEFLATE, 2), Arguments.of(Algorithm.DEFLATE, 3),
        Arguments.of(Algorithm.DEFLATE, 4), Arguments.of(Algorithm.DEFLATE, 5),
        Arguments.of(Algorithm.DEFLATE, 6), Arguments.of(Algorithm.DEFLATE, 7),
        Arguments.of(Algorithm.DEFLATE, 8), Arguments.of(Algorithm.DEFLATE, 9),
        Arguments.of(Algorithm.LZ4, 1), Arguments.of(Algorithm.LZ4, 2),
        Arguments.of(Algorithm.LZ4, 3), Arguments.of(Algorithm.LZ4, 4),
        Arguments.of(Algorithm.LZ4, 5), Arguments.of(Algorithm.LZ4, 6),
        Arguments.of(Algorithm.LZ4, 7), Arguments.of(Algorithm.LZ4, 8),
        Arguments.of(Algorithm.LZ4, 9));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testConstructor(Algorithm algo) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try {
     try(QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)){}
    } catch (IOException | IllegalArgumentException | QatException e) {
        fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideAlgorithmLevelParams")
  public void testConstructor2(Algorithm algo, int level) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try {
     try(QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo, level)){}
    } catch (IOException | IllegalArgumentException | QatException e) {
        fail(e.getMessage());
    }
  }


  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInputStreamReadAll1(Mode mode, Algorithm algo, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
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
  public void testInputStreamReadAll3(Mode mode, Algorithm algo, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
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
  public void testInputStreamReadByte(Mode mode, Algorithm algo, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
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
  public void testInputStreamReadAvailable(Mode mode, Algorithm algo, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
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
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode);
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
  public void testInputStreamDoubleClose(Mode mode, Algorithm algo, int bufferSize) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    QatInputStream decompressedStream =
        new QatInputStream(inputStream, bufferSize, algo, mode);
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
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
    byte[] result = new byte[src.length];
    try (QatInputStream decompressedStream =
             new QatInputStream(inputStream, 16 * 1024, algo, mode)) {
      int skipped = (int)decompressedStream.skip(-5);
      assertEquals(0, skipped);
      int read = decompressedStream.read(result);
      assertEquals(result.length, read);
    }
    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testInputStreamReadEOF(Mode mode, Algorithm algo) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
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
