/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.ref.Cleaner;
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
  private final String SAMPLE_TEXT_PATH = "src/test/resources/sample.txt";

  private QatZipper qzip;
  private static final Cleaner cleaner = Cleaner.create();
  private Cleaner.Cleanable cleanable;

  private Random rnd = new Random();

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

  public static Stream<Arguments> provideModeAlgorithmLengthParams() {
    return QatTestSuite.FORCE_HARDWARE
        ? Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 131072),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 524288),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 2097152),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 131072),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 524288),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 2097152),
            Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 131072),
            Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 524288),
            Arguments.of(Mode.HARDWARE, Algorithm.DEFLATE, 2097152),
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 131072),
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 524288),
            Arguments.of(Mode.HARDWARE, Algorithm.LZ4, 2097152))
        : Stream.of(
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 131072),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 524288),
            Arguments.of(Mode.AUTO, Algorithm.DEFLATE, 2097152),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 131072),
            Arguments.of(Mode.AUTO, Algorithm.LZ4, 524288));
  }

  private byte[] getRandomBytes(int len) {
    byte[] bytes = new byte[len];
    rnd.nextBytes(bytes);
    return bytes;
  }

  private byte[] readAllBytes(String fileName) throws IOException {
    return Files.readAllBytes(Path.of(fileName));
  }

  @AfterEach
  public void cleanupSession() {
    if (qzip != null) qzip.end();
  }

  @Test
  public void testDefaultConstructor() {
    try {
      qzip = new QatZipper();
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testEnd() {
    try {
      QatZipper qzip = new QatZipper();
      qzip.end();
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void duplicateEndHW() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      QatZipper qzip = new QatZipper(Algorithm.LZ4, 0, Mode.HARDWARE);
      qzip.end();
      qzip.end();
    } catch (IllegalStateException | IllegalArgumentException is) {
      assertTrue(true);
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testCompressWithNullByteBuffer() {
    try {
      qzip = new QatZipper();
      ByteBuffer buf = null;
      qzip.compress(buf, buf);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testCompressWithNullByteArray() {
    try {
      qzip = new QatZipper();
      qzip.compress(null, 0, 100, null, 0, 0);
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testDecompressWithNullByteBuffer() {
    try {
      qzip = new QatZipper();
      ByteBuffer buf = null;
      qzip.decompress(buf, buf);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testDecompressWithNullByteArray() {
    try {
      qzip = new QatZipper();
      int compressedSize = qzip.decompress(null, 0, 100, null, 0, 0);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  public void testSingleArgConstructorMode(Mode mode) {
    assumeFalse(mode.equals(Mode.HARDWARE) && !QatTestSuite.FORCE_HARDWARE);
    try {
      qzip = new QatZipper(mode);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testSingleArgConstructorAlgo(Algorithm algo) {
    try {
      qzip = new QatZipper(algo);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testTwoArgConstructorAlgoAndMode(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, mode);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testTwoArgConstructorAlgoAndLevel(Algorithm algo) {
    try {
      qzip = new QatZipper(algo, 9);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testThreeArgConstructorAlgoLevelMode(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, 9, mode);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testFourArgConstructorAlgoLevelModeRetryCount(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, 9, mode, 10);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideAlgorithmLevelParams")
  public void testHelloWorld(Algorithm algo, int level) {
    try {
      String inputStr = "Hello World!";
      byte[] input = inputStr.getBytes();

      QatZipper qzip = new QatZipper(algo, level);
      // Create a buffer with enough size for compression
      byte[] output = new byte[qzip.maxCompressedLength(input.length)];

      // Compress the bytes
      int resultLen = qzip.compress(input, output);

      // Decompress the bytes into a String
      byte[] result = new byte[input.length];
      resultLen = qzip.decompress(output, result);

      // Release resources
      qzip.end();

      // Convert the bytes into a String
      String outputStr = new String(result, 0, resultLen);
      assertEquals(inputStr, outputStr);
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  public void testInvalidCompressionLevel(Algorithm algo) {
    try {
      qzip = new QatZipper(algo, 10);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testChunkedCompressionWithByteArray(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int compressedSize = qzip.compress(src, 0, src.length, dst, 0, dst.length);
      int decompressedSize = qzip.decompress(dst, 0, compressedSize, dec, 0, dec.length);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testChunkedCompressionWithByteArrayDiffOffset(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int compressedSize = qzip.compress(src, 3, src.length - 3, dst, 0, dst.length);

      int decompressedSize = qzip.decompress(dst, 0, compressedSize, dec, 3, dec.length - 3);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length - 3);

      String str = new String(src, StandardCharsets.UTF_8);
      assertTrue(
          str.substring(3).compareTo(new String(dec, StandardCharsets.UTF_8).substring(3)) == 0);

    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testChunkedCompressionWithByteBuffer(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer comBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src);
      srcBuf.flip();

      int compressedSize = qzip.compress(srcBuf, comBuf);
      comBuf.flip();

      int decompressedSize = qzip.decompress(comBuf, decBuf);
      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmParams")
  public void testChunkedCompressionWithWrappedByteBuffer(Mode mode, Algorithm algo) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer comBuf = ByteBuffer.allocate(qzip.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocate(src.length);

      srcBuf.put(src);
      srcBuf.flip();

      int compressedSize = qzip.compress(srcBuf, comBuf);
      comBuf.flip();

      int decompressedSize = qzip.decompress(comBuf, decBuf);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testWrappedBuffers(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.wrap(src);
      ByteBuffer dstBuf = ByteBuffer.wrap(dst);
      ByteBuffer decBuf = ByteBuffer.wrap(dec);

      int compressedSize = qzip.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);
      assertNotNull(dstBuf);

      dstBuf.flip();

      int decompressedSize = qzip.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testArrayBackedBuffersWithAllocate(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocate(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocate(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      int compressedSize = qzip.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testDirectByteBufferSrcCompression(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocate(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocate(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      int compressedSize = qzip.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testDirectByteBufferDstCompression(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocate(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      int compressedSize = qzip.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testDirectByteBufferDstDecompression(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocate(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocateDirect(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      int compressedSize = qzip.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testIndirectBuffersReadOnly(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocate(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocate(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      int compressedSize = qzip.compress(srcBuf.asReadOnlyBuffer(), dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf.asReadOnlyBuffer(), decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);
      assertTrue(Arrays.equals(dec, src));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressionDecompressionWithByteArray(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];

      int compressedSize = qzip.compress(src, 0, src.length, dst, 0, dst.length);
      assertNotNull(dst);

      qzip.decompress(dst, 0, compressedSize, dec, 0, dec.length);
      assertNotNull(dec);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException
        | IllegalStateException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressionWithInsufficientDestBuff(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[src.length / 10];

      qzip.compress(src, 0, src.length, dst, 0, dst.length);
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testDecompressionWithInsufficientDestBuff(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length / 2];
      byte[] dst = new byte[src.length];

      qzip.compress(src, 0, src.length, dst, 0, dst.length);
      qzip.decompress(dst, 0, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressionDecompressionWithDirectByteBuffer(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qzip.compress(srcBuf, dstBuf);
      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf, decBuf);
      assertNotNull(dec);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressionDecompressionWithDirectByteBufferNoPinnedMem(
      Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qzip.compress(srcBuf, dstBuf);
      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qzip.decompress(dstBuf, decBuf);
      assertNotNull(dec);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressionReadOnlyDestination(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      qzip.compress(srcBuf, dstBuf.asReadOnlyBuffer());

      fail();
    } catch (ReadOnlyBufferException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testDecompressionReadOnlyDestination(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qzip.compress(srcBuf, dstBuf);
      dstBuf.flip();
      qzip.decompress(dstBuf, decBuf.asReadOnlyBuffer());

      fail();
    } catch (ReadOnlyBufferException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressionDecompressionReadOnlyByteBuffer(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBufRW = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(2 * src.length);
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
      srcBufRW.put(src, 0, src.length);
      srcBufRW.flip();

      ByteBuffer srcBufRO = srcBufRW.asReadOnlyBuffer();
      qzip.compress(srcBufRO, dstBuf);
      assertNotNull(dstBuf);
      dstBuf.flip();

      int decompressedSize = qzip.decompress(dstBuf, decBuf);
      assertNotNull(dec);
      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testIllegalStateException(Mode mode, Algorithm algo, int len) {
    try {
      QatZipper qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[2 * src.length];

      qzip.end();
      qzip.compress(src, 0, src.length, dst, 0, dst.length);
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void compressByteArrayPostTearDown(Mode mode, Algorithm algo, int len) {
    try {
      QatZipper qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[2 * src.length];

      qzip.end();
      qzip.compress(src, 0, src.length, dst, 0, dst.length);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void compressByteBufferPostTearDown(Mode mode, Algorithm algo, int len) {
    try {
      QatZipper qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qzip.end();
      qzip.compress(srcBuf, dstBuf);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void decompressByteArrayPostTearDown(Mode mode, Algorithm algo, int len) {
    try {
      QatZipper qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[2 * src.length];
      qzip.compress(src, 0, src.length, dst, 0, dst.length);

      qzip.end();
      qzip.decompress(src, 0, src.length, dst, 0, dst.length);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void decompressByteBufferPostTearDown(Mode mode, Algorithm algo, int len) {
    try {
      QatZipper qzip = new QatZipper(algo, mode);

      byte[] src = getRandomBytes(len);

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qzip.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qzip.compress(srcBuf, dstBuf);

      dstBuf.flip();
      qzip.end();
      qzip.decompress(dstBuf, decBuf);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testCompressorText(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);

      byte[] data = getRandomBytes(len);
      ByteBuffer src = ByteBuffer.allocate(data.length);
      src.put(data);
      src.flip();

      // Prepend some random bytes to the output and compress
      final int outOffset = 3;
      byte[] garbage = new byte[outOffset + qzip.maxCompressedLength(data.length)];

      new Random().nextBytes(garbage);
      ByteBuffer dst = ByteBuffer.allocate(outOffset + qzip.maxCompressedLength(data.length));
      dst.put(garbage);
      dst.clear();
      dst.position(outOffset);

      int compressedSize = qzip.compress(src, dst);
      int comLen = dst.position() - outOffset;
      assertEquals(compressedSize, comLen);

      ByteBuffer result = ByteBuffer.allocate(data.length + 100);
      dst.flip();
      dst.position(outOffset);

      int decompressedSize = qzip.decompress(dst, result);
      src.flip();
      result.flip();

      assertEquals(decompressedSize, data.length);
      assertTrue(result.compareTo(src) == 0);

    } catch (QatException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testVaryingOffset(QatZipper.Mode mode, QatZipper.Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);
      byte[] data = getRandomBytes(len);

      final int inOffset = 2;
      ByteBuffer src = ByteBuffer.allocate(inOffset + len + inOffset);
      src.position(inOffset);
      src.put(data, 0, len);
      src.flip().position(inOffset);

      int outOffset = 5;
      ByteBuffer compressed =
          ByteBuffer.allocate(outOffset + qzip.maxCompressedLength(data.length) + outOffset);
      byte[] garbage = new byte[compressed.capacity()];
      new Random().nextBytes(garbage);
      compressed.put(garbage);
      compressed.position(outOffset).limit(compressed.capacity() - outOffset);

      int compressedSize = qzip.compress(src, compressed);
      assertEquals(inOffset + len, src.position());
      assertEquals(inOffset + len, src.limit());
      assertEquals(compressed.capacity() - outOffset, compressed.limit());
      compressed.flip().position(outOffset);
      int remaining = compressed.remaining();

      ByteBuffer result = ByteBuffer.allocate(inOffset + len + inOffset);
      result.position(inOffset).limit(result.capacity() - inOffset);
      qzip.decompress(compressed, result);
      assertEquals(outOffset + remaining, compressed.position());
      assertEquals(outOffset + remaining, compressed.limit());
      assertEquals(result.capacity() - inOffset, result.limit());

      int decompressed = result.position() - inOffset;
      assert decompressed == len : "Failed uncompressed size";
      for (int i = 0; i < len; ++i)
        assert data[i] == result.get(inOffset + i) : "Failed comparison on index: " + i;

    } catch (QatException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testVaryingOffsetWithReadOnlyBuffer(
      QatZipper.Mode mode, QatZipper.Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, mode);
      byte[] data = getRandomBytes(len);

      final int inOffset = 2;
      ByteBuffer src = ByteBuffer.allocate(inOffset + len + inOffset);
      src.position(inOffset);
      src.put(data, 0, len);
      src.flip().position(inOffset);
      ByteBuffer readOnlySrc = src.asReadOnlyBuffer();

      int outOffset = 5;
      ByteBuffer compressed =
          ByteBuffer.allocate(outOffset + qzip.maxCompressedLength(data.length) + outOffset);
      byte[] garbage = new byte[compressed.capacity()];
      new Random().nextBytes(garbage);
      compressed.put(garbage);
      compressed.position(outOffset).limit(compressed.capacity() - outOffset);

      int compressedSize = qzip.compress(readOnlySrc, compressed);
      assertEquals(inOffset + len, readOnlySrc.position());
      assertEquals(inOffset + len, readOnlySrc.limit());
      assertEquals(compressed.capacity() - outOffset, compressed.limit());
      compressed.flip().position(outOffset);
      int remaining = compressed.remaining();

      ByteBuffer result = ByteBuffer.allocate(inOffset + len + inOffset);
      result.position(inOffset).limit(result.capacity() - inOffset);

      ByteBuffer readOnlyCompressed = compressed.asReadOnlyBuffer();
      qzip.decompress(readOnlyCompressed, result);
      assertEquals(outOffset + remaining, readOnlyCompressed.position());
      assertEquals(outOffset + remaining, readOnlyCompressed.limit());
      assertEquals(result.capacity() - inOffset, result.limit());

      int decompressed = result.position() - inOffset;
      assert decompressed == len : "Failed uncompressed size";
      for (int i = 0; i < len; ++i)
        assert data[i] == result.get(inOffset + i) : "Failed comparison on index: " + i;

    } catch (QatException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInvalidCompressionOffsets(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qzip.compress(src, -1, src.length, dst, 0, dst.length);
      qzip.decompress(dst, 0, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInvalidCompressionLargeOffsets(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qzip.compress(src, src.length + 1, src.length, dst, 0, dst.length);
      qzip.decompress(dst, 0, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInvaliDecompressionOffsets(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qzip.compress(src, 0, src.length, dst, 0, dst.length);
      qzip.decompress(dst, -1, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideModeAlgorithmLengthParams")
  public void testInvaliDecompressionLargeOffsets(Mode mode, Algorithm algo, int len) {
    try {
      qzip = new QatZipper(algo, 9, mode, 0);

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[qzip.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qzip.compress(src, 0, src.length, dst, 0, dst.length);
      qzip.decompress(dst, dst.length + 1, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }
}
