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

  private QatZipper qzip;
  private static byte[] src;

  private Random rnd = new Random();

  @BeforeAll
  public static void setup() throws IOException {
    src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
  }

  @AfterEach
  public void cleanupSession() {
    if (qzip != null) qzip.end();
  }

  public static Stream<Arguments> provideModeAlgorithmParams() {
    if (QatZipper.isQatAvailable()) {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE),
          Arguments.of(Mode.AUTO, Algorithm.LZ4),
          Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE),
          Arguments.of(Mode.HARDWARE, Algorithm.LZ4));
    } else {
      return Stream.of(
          Arguments.of(Mode.AUTO, Algorithm.DEFLATE), Arguments.of(Mode.AUTO, Algorithm.LZ4));
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

  public static Stream<Arguments> provideAlgorithmLevelParams() {
    return Stream.of(
        Arguments.of(Algorithm.DEFLATE, 1),
        Arguments.of(Algorithm.DEFLATE, 2),
        Arguments.of(Algorithm.DEFLATE, 3),
        Arguments.of(Algorithm.DEFLATE, 4),
        Arguments.of(Algorithm.DEFLATE, 5),
        Arguments.of(Algorithm.DEFLATE, 6),
        Arguments.of(Algorithm.DEFLATE, 7),
        Arguments.of(Algorithm.DEFLATE, 8),
        Arguments.of(Algorithm.DEFLATE, 9),
        Arguments.of(Algorithm.LZ4, 1),
        Arguments.of(Algorithm.LZ4, 2),
        Arguments.of(Algorithm.LZ4, 3),
        Arguments.of(Algorithm.LZ4, 4),
        Arguments.of(Algorithm.LZ4, 5),
        Arguments.of(Algorithm.LZ4, 6),
        Arguments.of(Algorithm.LZ4, 7),
        Arguments.of(Algorithm.LZ4, 8),
        Arguments.of(Algorithm.LZ4, 9));
  }

  @Test
  public void testOutputStreamNullStream() throws IOException {
    ByteArrayOutputStream outputStream = null;
    try {
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(outputStream, Algorithm.DEFLATE)) {}
      fail("Failed to catch NullPointerException");
    } catch (NullPointerException | IOException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testOutputStreamOneArgConstructor(Algorithm algo) throws IOException {
    if (!QatZipper.isQatAvailable()) {
      return;
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(outputStream, algo)) {}
    } catch (IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testOutputStreamConstructor1(Algorithm algo) throws IOException {
    if (!QatZipper.isQatAvailable()) {
      return;
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(outputStream, 16 * 1024, algo)) {}
    } catch (IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideAlgorithmLevelParams")
  public void testOutputStreamConstructor2(Algorithm algo, int level) throws IOException {
    if (!QatZipper.isQatAvailable()) {
      return;
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(
              outputStream,
              16 * 1024,
              new QatZipper.Builder().setAlgorithm(algo).setLevel(level))) {}
    } catch (IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testOutputStreamConstructor3(Mode mode, Algorithm algo) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(
              outputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {}
    } catch (IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testOutputStreamBadBufferSize() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      try (QatCompressorOutputStream compressedStream =
          new QatCompressorOutputStream(outputStream, 0, Algorithm.LZ4)) {
        fail("Failed to catch IllegalArgumentException");
      }
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamWriteAll1(Mode mode, Algorithm algo, int size) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      compressedStream.write(src);
    }
    byte[] outputStreamBuf = outputStream.toByteArray();
    byte[] result = new byte[src.length];
    qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);

    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamWriteAll3(Mode mode, Algorithm algo, int size) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int i;
      int len = 0;
      for (i = 0; i < src.length; i += len) {
        len = Math.min(rnd.nextInt(20 * 1024), src.length - i);
        compressedStream.write(src, i, len);
      }
      assertEquals(src.length, i);
    }
    byte[] outputStreamBuf = outputStream.toByteArray();
    byte[] result = new byte[src.length];
    qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);

    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamWriteByte(Mode mode, Algorithm algo, int size) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int i;
      int len = 0;
      for (i = 0; i < src.length; i += len) {
        if (i % 10 == 0) { // doWriteByte
          len = 1;
          compressedStream.write((int) src[i]);
        } else {
          len = Math.min(rnd.nextInt(20 * 1024), src.length - i);
          compressedStream.write(src, i, len);
        }
      }
      assertEquals(src.length, i);
    }
    byte[] outputStreamBuf = outputStream.toByteArray();
    byte[] result = new byte[src.length];
    qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);

    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamWriteFlush(Mode mode, Algorithm algo, int size) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      int i;
      int len = 0;
      for (i = 0; i < src.length; i += len) {
        if (i > 0 && i % 10 == 0) { // doFlush
          compressedStream.flush();
        }
        len = Math.min(rnd.nextInt(20 * 1024), src.length - i);
        compressedStream.write(src, i, len);
      }
      assertEquals(src.length, i);
    }
    byte[] outputStreamBuf = outputStream.toByteArray();
    byte[] result = new byte[src.length];
    qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);

    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamClose(Mode mode, Algorithm algo, int size) throws IOException {
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    compressedStream.close();
    try {
      compressedStream.write(src);
      fail("Failed to catch IOException!");
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamWriteAfterClose(Mode mode, Algorithm algo, int size)
      throws IOException {
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    compressedStream.close();
    try {
      compressedStream.write(src[0]);
      fail("Failed to catch IOException!");
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamFlushAfterClose(Mode mode, Algorithm algo, int size)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    compressedStream.close();
    try {
      compressedStream.flush();
      fail("Failed to catch IOException!");
    } catch (IOException ioe) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamDoubleClose(Mode mode, Algorithm algo, int size) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode));
    compressedStream.close();
    compressedStream.close();
    assertTrue(true);
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testOutputStreamFlushOnClose(Mode mode, Algorithm algo, int size) throws IOException {
    QatZipper qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    final byte[] preResult;
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, size, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      compressedStream.write(src);
      preResult = outputStream.toByteArray();
    }

    byte[] outputStreamBuf = outputStream.toByteArray();
    assertFalse(Arrays.equals(outputStreamBuf, preResult));
    byte[] result = new byte[src.length];
    qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);

    assertTrue(Arrays.equals(src, result));
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testOutputStreamWriteNullArray(Mode mode, Algorithm algo) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        compressedStream.write(null, 33, 100);
        fail("Failed to catch NullPointerException");
      } catch (NullPointerException npe) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testOutputStreamWriteBadOffset(Mode mode, Algorithm algo) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        compressedStream.write(src, -33, 100);
        fail("Failed to catch IndexOutOfBoundsException");
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testOutputStreamWriteBadLength(Mode mode, Algorithm algo) throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        compressedStream.write(src, src.length - 1, -100);
        fail("Failed to catch IndexOutOfBoundsException");
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testOutputStreamWriteBadOffsetAndLength(Mode mode, Algorithm algo)
      throws IOException {
    qzip = new QatZipper.Builder().setAlgorithm(algo).setMode(mode).build();
    byte[] src = Files.readAllBytes(Paths.get(SAMPLE_CORPUS));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (QatCompressorOutputStream compressedStream =
        new QatCompressorOutputStream(
            outputStream, 16 * 1024, new QatZipper.Builder().setAlgorithm(algo).setMode(mode))) {
      try {
        compressedStream.write(src, src.length - 1, 100);
        fail("Failed to catch IndexOutOfBoundsException");
      } catch (IndexOutOfBoundsException oob) {
        assertTrue(true);
      }
    }
  }
}
