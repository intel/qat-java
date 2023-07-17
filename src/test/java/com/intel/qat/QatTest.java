/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class QatTest {
  private QatSession qatSession;
  private static final Cleaner cleaner = Cleaner.create();
  private Cleaner.Cleanable cleanable;

  private Random rnd = new Random();

  private byte[] getSourceArray(int len) {
    byte[] bytes = new byte[len];
    rnd.nextBytes(bytes);
    return bytes;
  }

  @AfterEach
  public void cleanupSession() {
    if (qatSession != null)
      qatSession.endSession();
  }

  @Test
  public void testDefaultConstructor() {
    try {
      qatSession = new QatSession();
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testSingleArgConstructor() {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.LZ4);
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testTwoArgConstructor() {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9);
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testThreeArgConstructorAuto() {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 1, QatSession.Mode.AUTO);
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testThreeArgConstructorHW() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 1, QatSession.Mode.HARDWARE);
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testFourArgConstructorHW() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 6,
          QatSession.Mode.HARDWARE, QatSession.DEFAULT_RETRY_COUNT);
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testTeardown() {
    QatSession qatSession = null;
    try {
      qatSession = new QatSession();
      qatSession.endSession();
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void duplicateTearDown() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    QatSession qatSession = null;
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.LZ4, 0, QatSession.Mode.HARDWARE);
      qatSession.endSession();
      qatSession.endSession();
    } catch (IllegalStateException is) {
      assertTrue(true);
    } catch (IllegalArgumentException | QatException ie) {
      fail(ie.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testWrappedBuffers(int len) {
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      byte[] uncomp = new byte[source.length];
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

      ByteBuffer srcBuffer = ByteBuffer.wrap(source);
      ByteBuffer destBuffer = ByteBuffer.wrap(dest);
      ByteBuffer uncompBuffer = ByteBuffer.wrap(uncomp);

      int compressedSize = qatSession.compress(srcBuffer, destBuffer);

      assertTrue(compressedSize > 0);

      assertNotNull(destBuffer);

      destBuffer.flip();

      int decompressedSize = qatSession.decompress(destBuffer, uncompBuffer);
      assertNotNull(uncompBuffer);

      assertTrue(decompressedSize > 0);
      assertTrue(Arrays.equals(source, uncomp));
    } catch (QatException | IllegalStateException | IllegalArgumentException
        | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
    assertTrue(true);
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testBackedArrayBuffersWithAllocate(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] source = getSourceArray(len);
      byte[] uncompressed = new byte[source.length];
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

      ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
      ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
      ByteBuffer uncompBuffer = ByteBuffer.allocate(uncompressed.length);

      srcBuffer.put(source, 0, source.length);
      srcBuffer.flip();
      int compressedSize = qatSession.compress(srcBuffer, destBuffer);

      assertTrue(compressedSize > 0);

      assertNotNull(destBuffer);

      destBuffer.flip();
      int decompressedSize = qatSession.decompress(destBuffer, uncompBuffer);
      assertNotNull(uncompBuffer);

      assertTrue(decompressedSize > 0);

      uncompBuffer.flip();
      uncompBuffer.get(uncompressed, 0, decompressedSize);

      assertTrue(Arrays.equals(source, uncompressed));
    } catch (QatException | IllegalStateException | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
    assertTrue(true);
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testIndirectBuffersReadOnly(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] source = getSourceArray(len);
      byte[] uncompressed = new byte[source.length];
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

      ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
      ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
      ByteBuffer uncompBuffer = ByteBuffer.allocate(uncompressed.length);

      srcBuffer.put(source, 0, source.length);
      srcBuffer.flip();

      int compressedSize =
          qatSession.compress(srcBuffer.asReadOnlyBuffer(), destBuffer);

      assertTrue(compressedSize > 0);

      assertNotNull(destBuffer);

      destBuffer.flip();
      int decompressedSize =
          qatSession.decompress(destBuffer.asReadOnlyBuffer(), uncompBuffer);
      assertNotNull(uncompBuffer);

      assertTrue(decompressedSize > 0);

      uncompBuffer.flip();
      uncompBuffer.get(uncompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(uncompressed, source));
    } catch (QatException | IllegalStateException | IllegalArgumentException
        | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
    assertTrue(true);
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithByteArray(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] source = getSourceArray(len);
      byte[] uncompressed = new byte[source.length];
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

      int compressedSize =
          qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      assertNotNull(dest);

      qatSession.decompress(
          dest, 0, compressedSize, uncompressed, 0, uncompressed.length);
      assertNotNull(uncompressed);

      assertTrue(Arrays.equals(source, uncompressed));

    } catch (QatException | IllegalStateException | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithByteArrayLZ4(int len) {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.LZ4, 9, QatSession.Mode.AUTO, 0, 0);

      byte[] source = getSourceArray(len);
      byte[] uncomp = new byte[source.length];
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

      int compressedSize =
          qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      assertNotNull(dest);

      qatSession.decompress(dest, 0, compressedSize, uncomp, 0, uncomp.length);
      assertNotNull(uncomp);
      assertTrue(Arrays.equals(source, uncomp));
    } catch (QatException | IllegalStateException | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressByteArrayWithByteBuff(int len) {
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];
      byte[] resultArray = new byte[100];

      ByteBuffer uncompressedBuffer = ByteBuffer.allocateDirect(source.length);
      ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(dest.length);
      ByteBuffer resultBuffer = ByteBuffer.allocateDirect(source.length);

      uncompressedBuffer.put(source);
      uncompressedBuffer.flip();

      int compressedSize =
          qatSession.compress(uncompressedBuffer, compressedBuffer);
      assertEquals(compressedBuffer.position(), compressedSize);

      int byteArrayCompSize =
          qatSession.compress(source, 0, source.length, dest, 0, dest.length);

      assertEquals(compressedSize, byteArrayCompSize);

      compressedBuffer.flip();
      byte[] compByteBufferArray = new byte[compressedBuffer.limit()];
      compressedBuffer.get(compByteBufferArray);

      for (int i = 0; i < compressedSize; i++) {
        if (dest[i] != compByteBufferArray[i])
          fail("compressed data not same");
      }
      compressedBuffer.flip();

      int decompressedSize =
          qatSession.decompress(compressedBuffer, resultBuffer);
      int byteArrayDecompSize = qatSession.decompress(
          dest, 0, byteArrayCompSize, resultArray, 0, resultArray.length);
      resultBuffer.flip();

      byte[] tmpResult = new byte[byteArrayDecompSize];
      resultBuffer.get(tmpResult);
      assertTrue(Arrays.equals(resultArray, tmpResult));
    } catch (QatException ie) {
      fail(ie.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testComppressionDecompressionHardwareMode(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 6,
          QatSession.Mode.HARDWARE, 0);

      byte[] source = getSourceArray(len);
      byte[] decompressed = new byte[source.length];
      byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

      int compressedSize =
          qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      assertNotNull(dest);

      qatSession.decompress(
          dest, 0, compressedSize, decompressed, 0, decompressed.length);
      assertNotNull(decompressed);
      assertTrue(Arrays.equals(source, decompressed));
    } catch (QatException ie) {
      fail(ie.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionWithInsufficientDestBuff(int len) {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 6, QatSession.Mode.AUTO, 0);

      byte[] source = getSourceArray(len);
      byte[] dest = new byte[source.length / 10];

      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionWithInsufficientDestBuffHW(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 6,
          QatSession.Mode.HARDWARE, 0);

      byte[] source = getSourceArray(len);
      byte[] dest = new byte[source.length / 10];

      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testDecompressionWithInsufficientDestBuff(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 6,
          QatSession.Mode.HARDWARE, 0);

      byte[] source = getSourceArray(len);
      byte[] uncomp = new byte[source.length / 2];
      byte[] dest = new byte[source.length];

      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      qatSession.decompress(dest, 0, dest.length, uncomp, 0, uncomp.length);

      fail();
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithDirectByteBuff(int len) {
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      byte[] decompressed = new byte[source.length];

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      qatSession.compress(srcBuff, destBuff);
      assertNotNull(destBuff);

      destBuff.flip();
      int decompressedSize = qatSession.decompress(destBuff, unCompBuff);
      assertNotNull(decompressed);

      unCompBuff.flip();

      unCompBuff.get(decompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(source, decompressed));
    } catch (QatException ie) {
      fail(ie.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithDirectByteBuffNoPinnedMem(
      int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] source = getSourceArray(len);
      byte[] decompressed = new byte[source.length];

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      qatSession.compress(srcBuff, destBuff);
      assertNotNull(destBuff);

      destBuff.flip();
      int decompressedSize = qatSession.decompress(destBuff, unCompBuff);
      assertNotNull(decompressed);

      unCompBuff.flip();

      unCompBuff.get(decompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(source, decompressed));
    } catch (QatException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testCompressWithNullByteBuff() {
    try {
      qatSession = new QatSession();

      int compressedSize = qatSession.compress(null, null);
      fail("testCompressWithNullByteBuff fails");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testCompressWithNullByteArray() {
    try {
      qatSession = new QatSession();
      qatSession.compress(null, 0, 100, null, 0, 0);
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testDecompressWithNullByteBuff() {
    try {
      qatSession = new QatSession();
      int compressedSize = qatSession.decompress(null, null);
      fail("testDecompressWithNullByteBuff fails");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testDecompressWithNullByteArray() {
    try {
      qatSession = new QatSession();
      int compressedSize = qatSession.decompress(null, 0, 100, null, 0, 0);
      fail("testDecompressWithNullByteArray fails");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionReadOnlyDestination(int len) {
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();
      qatSession.compress(srcBuff, destBuff.asReadOnlyBuffer());

      fail();
    } catch (ReadOnlyBufferException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testDecompressionReadOnlyDestination(int len) {
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      qatSession.compress(srcBuff, destBuff);
      destBuff.flip();
      qatSession.decompress(destBuff, unCompBuff.asReadOnlyBuffer());

      fail();
    } catch (ReadOnlyBufferException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompDecompDefaultModeReadOnlyByteBuff(int len) {
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      byte[] uncompressed = new byte[source.length];

      ByteBuffer srcBuffRW = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(2 * source.length);
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);
      srcBuffRW.put(source, 0, source.length);
      srcBuffRW.flip();

      ByteBuffer srcBuffRO = srcBuffRW.asReadOnlyBuffer();
      qatSession.compress(srcBuffRO, destBuff);
      assertNotNull(destBuff);
      destBuff.flip();

      int decompressedSize = qatSession.decompress(destBuff, unCompBuff);
      assertNotNull(uncompressed);
      unCompBuff.flip();
      unCompBuff.get(uncompressed, 0, decompressedSize);

      assertTrue(Arrays.equals(source, uncompressed));
    } catch (QatException ie) {
      fail(ie.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void replicateCassandra(int len) {
    final int offset = 2;
    byte[] source = getSourceArray(len);
    ByteBuffer src = ByteBuffer.allocate(source.length);
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testIllegalStateException(int len) {
    QatSession qatSession = null;

    byte[] source = getSourceArray(len);
    byte[] dest = new byte[2 * source.length];

    try {
      qatSession = new QatSession();
      qatSession.endSession();
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testIllegalStateExceptionHW(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    QatSession qatSession = null;

    byte[] source = getSourceArray(len);
    byte[] dest = new byte[2 * source.length];

    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE,
          QatSession.DEFAULT_DEFLATE_COMP_LEVEL, QatSession.Mode.HARDWARE);
      qatSession.endSession();
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      fail();
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidCompressionLevel() {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 10, QatSession.Mode.AUTO, 6);
      fail();
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidRetryCount() {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 10,
          QatSession.Mode.AUTO, -1);
      fail();
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void compressByteArrayPostTearDown(int len) {
    QatSession qatSession = null;
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      byte[] dest = new byte[2 * source.length];

      qatSession.endSession();
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);

      fail();
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void compressByteBufferPostTearDown(int len) {
    QatSession qatSession = null;
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      qatSession.endSession();
      qatSession.compress(srcBuff, destBuff);

      fail();
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void decompressByteArrayPostTearDown(int len) {
    QatSession qatSession = null;
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);
      byte[] dest = new byte[2 * source.length];
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);

      qatSession.endSession();
      qatSession.decompress(source, 0, source.length, dest, 0, dest.length);

      fail();
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void decompressByteBufferPostTearDown(int len) {
    QatSession qatSession = null;
    try {
      qatSession = new QatSession();

      byte[] source = getSourceArray(len);

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      qatSession.compress(srcBuff, destBuff);

      destBuff.flip();
      qatSession.endSession();
      qatSession.decompress(destBuff, unCompBuff);

      fail();
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void maxCompressedLengthPostTeardown() {
    QatSession qatSession = null;
    try {
      qatSession = new QatSession();
      qatSession.endSession();
      qatSession.maxCompressedLength(100);
      fail();
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testChunkedCompressionWithByteArray() {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 6, QatSession.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      int compressedSize =
          qatSession.compress(src, 0, src.length, dest, 0, dest.length);
      int decompressedSize = qatSession.decompress(
          dest, 0, compressedSize, decomp, 0, decomp.length);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, decomp));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithByteArrayDiffOffset() {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 6, QatSession.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      int compressedSize =
          qatSession.compress(src, 3, src.length - 3, dest, 0, dest.length);

      int decompressedSize = qatSession.decompress(
          dest, 0, compressedSize, decomp, 3, decomp.length - 3);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length - 3);

      assertTrue(book2.substring(3).compareTo(
                     new String(decomp, StandardCharsets.UTF_8).substring(3))
          == 0);

    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressorText(int len) {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 6, QatSession.Mode.AUTO);

      byte[] data = getSourceArray(len);
      ByteBuffer src = ByteBuffer.allocate(data.length);
      src.put(data);
      src.flip();

      // Prepend some random bytes to the output and compress
      final int outOffset = 3;
      byte[] garbage =
          new byte[outOffset + qatSession.maxCompressedLength(data.length)];

      new Random().nextBytes(garbage);
      ByteBuffer dest = ByteBuffer.allocate(
          outOffset + qatSession.maxCompressedLength(data.length));
      dest.put(garbage);
      dest.clear();
      dest.position(outOffset);

      int compressedSize = qatSession.compress(src, dest);
      int compressedLength = dest.position() - outOffset;
      assertEquals(compressedSize, compressedLength);

      ByteBuffer result = ByteBuffer.allocate(data.length + 100);
      dest.flip();
      dest.position(outOffset);

      int decompressedSize = qatSession.decompress(dest, result);
      src.flip();
      result.flip();

      assertEquals(decompressedSize, data.length);
      assertTrue(result.compareTo(src) == 0);

    } catch (QatException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithByteBuff() {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 6, QatSession.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      byte[] decomp = new byte[src.length];

      ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.length);
      ByteBuffer compressedBuffer =
          ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));
      ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(src.length);

      srcBuffer.put(src);
      srcBuffer.flip();

      int compressedSize = qatSession.compress(srcBuffer, compressedBuffer);
      compressedBuffer.flip();
      int decompressedSize =
          qatSession.decompress(compressedBuffer, decompressedBuffer);
      decompressedBuffer.flip();
      decompressedBuffer.get(decomp, 0, decompressedSize);
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, decomp));
    } catch (QatException | IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithWrappedByteBuff() {
    try {
      qatSession = new QatSession(
          QatSession.CompressionAlgorithm.DEFLATE, 6, QatSession.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      byte[] decomp = new byte[src.length];

      ByteBuffer srcBuffer = ByteBuffer.allocate(src.length);
      ByteBuffer compressedBuffer =
          ByteBuffer.allocate(qatSession.maxCompressedLength(src.length));
      ByteBuffer decompressedBuffer = ByteBuffer.allocate(src.length);

      srcBuffer.put(src);
      srcBuffer.flip();

      int compressedSize = qatSession.compress(srcBuffer, compressedBuffer);
      compressedBuffer.flip();
      int decompressedSize =
          qatSession.decompress(compressedBuffer, decompressedBuffer);

      decompressedBuffer.flip();
      decompressedBuffer.get(decomp, 0, decompressedSize);
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, decomp));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithWrappedByteBuffLZ4() {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.LZ4);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book1"));
      byte[] decomp = new byte[src.length];

      ByteBuffer srcBuffer = ByteBuffer.allocate(src.length);
      ByteBuffer compressedBuffer =
          ByteBuffer.allocate(qatSession.maxCompressedLength(src.length));
      ByteBuffer decompressedBuffer = ByteBuffer.allocate(src.length);
      srcBuffer.put(src);
      srcBuffer.flip();

      int compressedSize = qatSession.compress(srcBuffer, compressedBuffer);
      compressedBuffer.flip();
      int decompressedSize =
          qatSession.decompress(compressedBuffer, decompressedBuffer);

      decompressedBuffer.flip();
      decompressedBuffer.get(decomp, 0, decompressedSize);
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, decomp));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidCompressionOffsets(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);

      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      qatSession.compress(src, -1, src.length, dest, 0, dest.length);
      int decompressedSize =
          qatSession.decompress(dest, 0, dest.length, decomp, 0, decomp.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidCompressionOffsetsHW(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      qatSession.compress(src, -1, src.length, dest, 0, dest.length);
      int decompressedSize =
          qatSession.decompress(dest, 0, dest.length, decomp, 0, decomp.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidCompressionLargeOffsets(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      qatSession.compress(
          src, src.length + 1, src.length, dest, 0, dest.length);
      int decompressedSize =
          qatSession.decompress(dest, 0, dest.length, decomp, 0, decomp.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidecompressionOffsets(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      qatSession.compress(src, 0, src.length, dest, 0, dest.length);
      int decompressedSize = qatSession.decompress(
          dest, -1, dest.length, decomp, 0, decomp.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidecompressionLargeOffsets(int len) {
    try {
      qatSession = new QatSession(QatSession.CompressionAlgorithm.DEFLATE, 9,
          QatSession.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dest = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] decomp = new byte[src.length];

      qatSession.compress(src, 0, src.length, dest, 0, dest.length);
      int decompressedSize = qatSession.decompress(
          dest, dest.length + 1, dest.length, decomp, 0, decomp.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testCleaner() {
    QatSession qatSession = new QatSession();
    this.cleanable = cleaner.register(qatSession, qatSession.cleanUp());
    cleanable.clean();
  }
}
