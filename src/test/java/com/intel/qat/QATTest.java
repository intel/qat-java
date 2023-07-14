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

public class QATTest {

  private QATSession intQatSession;
  private static final Cleaner cleaner = Cleaner.create();
  private Cleaner.Cleanable cleanable;

  private final Random RANDOM = new Random();

  @AfterEach
  public void cleanupSession() {
    if (intQatSession != null)
      intQatSession.endSession();
  }
  @Test
  public void testDefaultConstructor() {
    try {
      intQatSession = new QATSession();
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }
  @Test
  public void testSingleArgConstructor() {
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4);
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testTwoArgConstructor() {
    try {
      intQatSession =
          new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 9);
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testThreeArgConstructorAuto() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 1, QATSession.Mode.AUTO);
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testThreeArgConstructorHW() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 1, QATSession.Mode.HARDWARE);
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testFourArgConstructorHW() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 6,
          QATSession.Mode.HARDWARE, QATSession.DEFAULT_RETRY_COUNT);
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  public void testTeardown() {
    QATSession qatSession = null;
    try {
      qatSession = new QATSession();
      qatSession.endSession();
    } catch (QATException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void duplicateTearDown() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    QATSession qatSession = null;
    try {
      qatSession = new QATSession(
          QATSession.CompressionAlgorithm.LZ4, 0, QATSession.Mode.HARDWARE);
      qatSession.endSession();
      qatSession.endSession();
    } catch (IllegalStateException is) {
      assertTrue(true);
    } catch (IllegalArgumentException | QATException ie) {
      fail(ie.getMessage());
    }
  }
  @Test
  void testWrappedBuffers() {
    try {
      intQatSession = new QATSession();
      // intQatSession.setIsPinnedMemAvailable();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncomp = new byte[source.length];
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

      ByteBuffer srcBuffer = ByteBuffer.wrap(source);
      ByteBuffer destBuffer = ByteBuffer.wrap(dest);
      ByteBuffer uncompBuffer = ByteBuffer.wrap(uncomp);

      int compressedSize = intQatSession.compress(srcBuffer, destBuffer);

      if (compressedSize < 0)
        fail("testWrappedBuffers compression fails");

      assertNotNull(destBuffer);

      destBuffer.flip();

      int decompressedSize = intQatSession.decompress(destBuffer, uncompBuffer);
      assertNotNull(uncompBuffer);

      if (decompressedSize < 0)
        fail("testWrappedBuffers decompression fails");

      String str = new String(uncomp, StandardCharsets.UTF_8);
      assertTrue(Arrays.equals(source, uncomp));
    } catch (QATException | IllegalStateException | IllegalArgumentException
        | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
    assertTrue(true);
  }

  @Test
  void testBackedArrayBuffersWithAllocate() {
    try {
      intQatSession = new QATSession();
      // intQatSession.setIsPinnedMemAvailable();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncompressed = new byte[source.length];
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

      ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
      ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
      ByteBuffer uncompBuffer = ByteBuffer.allocate(uncompressed.length);

      srcBuffer.put(source, 0, source.length);
      srcBuffer.flip();
      int compressedSize = intQatSession.compress(srcBuffer, destBuffer);

      if (compressedSize < 0)
        fail("testIndirectBuffers compression fails");

      assertNotNull(destBuffer);

      destBuffer.flip();
      int decompressedSize = intQatSession.decompress(destBuffer, uncompBuffer);
      assertNotNull(uncompBuffer);

      if (decompressedSize <= 0)
        fail("testWrappedBuffers decompression fails");
      uncompBuffer.flip();
      uncompBuffer.get(uncompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(source, uncompressed));
    } catch (QATException | IllegalStateException | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
    assertTrue(true);
  }

  @Test
  void testIndirectBuffersReadOnly() {
    try {
      intQatSession = new QATSession();
      // intQatSession.setIsPinnedMemAvailable();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncompressed = new byte[source.length];
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

      ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
      ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
      ByteBuffer uncompBuffer = ByteBuffer.allocate(uncompressed.length);

      srcBuffer.put(source, 0, source.length);
      srcBuffer.flip();

      int compressedSize =
          intQatSession.compress(srcBuffer.asReadOnlyBuffer(), destBuffer);

      if (compressedSize < 0)
        fail("testIndirectBuffers compression fails");

      assertNotNull(destBuffer);

      destBuffer.flip();
      int decompressedSize =
          intQatSession.decompress(destBuffer.asReadOnlyBuffer(), uncompBuffer);
      assertNotNull(uncompBuffer);

      if (decompressedSize <= 0)
        fail("testWrappedBuffers decompression fails");
      uncompBuffer.flip();
      uncompBuffer.get(uncompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(uncompressed, source));
    } catch (QATException | IllegalStateException | IllegalArgumentException
        | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
    assertTrue(true);
  }
  @Test
  void testCompressionDecompressionWithByteArray() {
    try {
      intQatSession = new QATSession();
      // intQatSession.setIsPinnedMemAvailable();

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncompressed = new byte[source.length];
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

      int compressedSize = intQatSession.compress(
          source, 0, source.length, dest, 0, dest.length);
      assertNotNull(dest);

      intQatSession.decompress(
          dest, 0, compressedSize, uncompressed, 0, uncompressed.length);
      assertNotNull(uncompressed);

      assertTrue(Arrays.equals(source, uncompressed));

    } catch (QATException | IllegalStateException | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }
  @Test
  void testCompressionDecompressionWithByteArrayLZ4() {
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4);
      // intQatSession.setIsPinnedMemAvailable();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncomp = new byte[source.length];
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

      int compressedSize = intQatSession.compress(
          source, 0, source.length, dest, 0, dest.length);
      assertNotNull(dest);

      intQatSession.decompress(
          dest, 0, compressedSize, uncomp, 0, uncomp.length);
      assertNotNull(uncomp);
      assertTrue(Arrays.equals(source, uncomp));
    } catch (QATException | IllegalStateException | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }
  @Test
  void testCompressByteArrayWithByteBuff() {
    try {
      intQatSession = new QATSession();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];
      byte[] resultArray = new byte[100];

      ByteBuffer uncompressedBuffer = ByteBuffer.allocateDirect(source.length);
      ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(dest.length);
      ByteBuffer resultBuffer = ByteBuffer.allocateDirect(source.length);

      uncompressedBuffer.put(source);
      uncompressedBuffer.flip();
      System.out.println("TEST: maxCompressed length " + dest.length);

      System.out.println("TEST: before bytebuffer compress  source position is "
          + uncompressedBuffer.position() + " and destination position is "
          + compressedBuffer.position());
      int compressedSize =
          intQatSession.compress(uncompressedBuffer, compressedBuffer);
      assertEquals(compressedBuffer.position(), compressedSize);

      System.out.println("TEST: after bytebuffer compress  source limit is "
          + uncompressedBuffer.limit() + " and compressed buffer position is "
          + compressedBuffer.position());

      int byteArrayCompSize = intQatSession.compress(
          source, 0, source.length, dest, 0, dest.length);

      System.out.println("TEST: byte buffer compressed size " + compressedSize);
      System.out.println(
          "TEST: byte array compressed size " + byteArrayCompSize);
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
          intQatSession.decompress(compressedBuffer, resultBuffer);
      int byteArrayDecompSize = intQatSession.decompress(
          dest, 0, byteArrayCompSize, resultArray, 0, resultArray.length);
      resultBuffer.flip();

      byte[] tmpResult = new byte[byteArrayDecompSize];
      resultBuffer.get(tmpResult);
      assertTrue(Arrays.equals(resultArray, tmpResult));

    } catch (QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  void testComppressionDecompressionHardwareMode() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 6,
          QATSession.Mode.HARDWARE, 0);

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] decompressed = new byte[source.length];
      byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

      int compressedSize = intQatSession.compress(
          source, 0, source.length, dest, 0, dest.length);
      assertNotNull(dest);

      intQatSession.decompress(
          dest, 0, compressedSize, decompressed, 0, decompressed.length);
      assertNotNull(decompressed);
      assertTrue(Arrays.equals(source, decompressed));
    } catch (QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  void testCompressionWithInsufficientDestBuff() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO, 0);

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] dest = new byte[source.length / 10];

      intQatSession.compress(source, 0, source.length, dest, 0, dest.length);
    } catch (QATException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  void testCompressionWithInsufficientDestBuffHW() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 6,
          QATSession.Mode.HARDWARE, 0);

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] dest = new byte[source.length / 10];

      intQatSession.compress(source, 0, source.length, dest, 0, dest.length);
    } catch (QATException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  void testDecompressionWithInsufficientDestBuff() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 6,
          QATSession.Mode.HARDWARE, 0);

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncomp = new byte[source.length / 2];
      byte[] dest = new byte[source.length];

      intQatSession.compress(source, 0, source.length, dest, 0, dest.length);
      intQatSession.decompress(dest, 0, dest.length, uncomp, 0, uncomp.length);
      fail("testInvalidDecompressionHardwareMode failed");
    } catch (QATException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  void testCompressionDecompressionWithDirectByteBuff() {
    try {
      intQatSession = new QATSession();

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] decompressed = new byte[source.length];

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          intQatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      intQatSession.compress(srcBuff, destBuff);
      assertNotNull(destBuff);

      destBuff.flip();
      int decompressedSize = intQatSession.decompress(destBuff, unCompBuff);
      assertNotNull(decompressed);

      unCompBuff.flip();

      unCompBuff.get(decompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(source, decompressed));
    } catch (QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  void testCompressionDecompressionWithDirectByteBuffNoPinnedMem() {
    try {
      intQatSession = new QATSession();
      // intQatSession.setIsPinnedMemAvailable();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] decompressed = new byte[source.length];

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          intQatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      intQatSession.compress(srcBuff, destBuff);
      assertNotNull(destBuff);

      destBuff.flip();
      int decompressedSize = intQatSession.decompress(destBuff, unCompBuff);
      assertNotNull(decompressed);

      unCompBuff.flip();

      unCompBuff.get(decompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(source, decompressed));
    } catch (QATException ie) {
      fail(ie.getMessage());
    }
  }

  @Test
  void testCompressWithNullByteBuff() {
    try {
      intQatSession = new QATSession();

      int compressedSize = intQatSession.compress(null, null);
      fail("testCompressWithNullByteBuff fails");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  void testCompressWithNullByteArray() {
    try {
      intQatSession = new QATSession();
      intQatSession.compress(null, 0, 100, null, 0, 0);
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  void testDecompressWithNullByteBuff() {
    try {
      intQatSession = new QATSession();
      int compressedSize = intQatSession.decompress(null, null);
      fail("testDecompressWithNullByteBuff fails");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  void testDecompressWithNullByteArray() {
    try {
      intQatSession = new QATSession();
      int compressedSize = intQatSession.decompress(null, 0, 100, null, 0, 0);
      fail("testDecompressWithNullByteArray fails");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  void testCompressionReadOnlyDestination() {
    try {
      intQatSession = new QATSession();

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          intQatSession.maxCompressedLength(source.length));

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();
      intQatSession.compress(srcBuff, destBuff.asReadOnlyBuffer());
      fail("testCompressionReadOnlyDestination failed");
    } catch (ReadOnlyBufferException ie) {
      assertTrue(true);
    }
  }

  @Test
  void testDecompressionReadOnlyDestination() {
    try {
      intQatSession = new QATSession();

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          intQatSession.maxCompressedLength(source.length));
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      intQatSession.compress(srcBuff, destBuff);
      destBuff.flip();
      intQatSession.decompress(destBuff, unCompBuff.asReadOnlyBuffer());
      fail("testDecompressionReadOnlyDestination failed");
    } catch (ReadOnlyBufferException ie) {
      assertTrue(true);
    }
  }

  @Test
  void testCompDecompDefaultModeReadOnlyByteBuff() {
    try {
      intQatSession = new QATSession();

      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] uncompressed = new byte[source.length];

      ByteBuffer srcBuffRW = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(2 * source.length);
      ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);
      srcBuffRW.put(source, 0, source.length);
      srcBuffRW.flip();

      ByteBuffer srcBuffRO = srcBuffRW.asReadOnlyBuffer();
      intQatSession.compress(srcBuffRO, destBuff);
      assertNotNull(destBuff);
      destBuff.flip();
      int decompressedSize = intQatSession.decompress(destBuff, unCompBuff);
      assertNotNull(uncompressed);
      unCompBuff.flip();
      unCompBuff.get(uncompressed, 0, decompressedSize);
      assertTrue(Arrays.equals(source, uncompressed));
    } catch (QATException ie) {
      fail(ie.getMessage());
    }
  }
  @Test
  void replicateCassandra() {
    final int offset = 2;
    byte[] source = new byte[100];
    RANDOM.nextBytes(source);
    ByteBuffer src = ByteBuffer.allocate(source.length);
  }

  @Test
  public void testIllegalStateException() {
    QATSession qatSession = null;
    byte[] source = new byte[100];
    RANDOM.nextBytes(source);
    byte[] dest = new byte[2 * source.length];

    try {
      qatSession = new QATSession();
      qatSession.endSession();
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @Test
  public void testIllegalStateExceptionHW() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    QATSession qatSession = null;
    byte[] source = new byte[100];
    RANDOM.nextBytes(source);
    byte[] dest = new byte[2 * source.length];

    try {
      qatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, QATSession.DEFAULT_DEFLATE_COMP_LEVEL, QATSession.Mode.HARDWARE);
      qatSession.endSession();
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      fail("testIllegalStateException fails");
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidCompressionLevel() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 10, QATSession.Mode.AUTO, 6);
      fail("testInvalidCompressionLevel failed");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidRetryCount() {
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,
          10, QATSession.Mode.AUTO, -1);
      fail("testInvalidRetryCount failed");
    } catch (IllegalArgumentException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void compressByteArrayPostTearDown() {
    QATSession qatSession = null;
    try {
      qatSession = new QATSession();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] dest = new byte[2 * source.length];

      qatSession.endSession();
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);
      fail("compressByteArrayPostTearDown failed");
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void compressByteBufferPostTearDown() {
    QATSession qatSession = null;
    try {
      qatSession = new QATSession();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);

      ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
      ByteBuffer destBuff = ByteBuffer.allocateDirect(
          qatSession.maxCompressedLength(source.length));

      srcBuff.put(source, 0, source.length);
      srcBuff.flip();

      qatSession.endSession();
      qatSession.compress(srcBuff, destBuff);
      fail("compressByteBufferPostTearDown failed");
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void decompressByteArrayPostTearDown() {
    QATSession qatSession = null;
    try {
      qatSession = new QATSession();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
      byte[] dest = new byte[2 * source.length];
      qatSession.compress(source, 0, source.length, dest, 0, dest.length);

      qatSession.endSession();
      qatSession.decompress(source, 0, source.length, dest, 0, dest.length);
      fail("decompressByteArrayPostTearDown failed");
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void decompressByteBufferPostTearDown() {
    QATSession qatSession = null;
    try {
      qatSession = new QATSession();
      byte[] source = new byte[100];
      RANDOM.nextBytes(source);
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
      fail("compressByteBufferPostTearDown failed");
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void maxCompressedLengthPostTeardown() {
    QATSession qatSession = null;
    try {
      qatSession = new QATSession();
      qatSession.endSession();
      qatSession.maxCompressedLength(100);
      fail("maxCompressedLengthPostTeardown failed");
    } catch (IllegalStateException ie) {
      assertTrue(true);
    }
  }

  @Test
  public void testChunkedCompressionWithByteArray() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO);
      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      int compressedSize =
          intQatSession.compress(src, 0, src.length, dest, 0, dest.length);
      System.out.println(
          "testChunkedCompressionWithByteArray : compression was successful");
      int decompressedSize = intQatSession.decompress(
          dest, 0, compressedSize, unCompressed, 0, unCompressed.length);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);

      assertTrue(
          book2.compareTo(new String(unCompressed, StandardCharsets.UTF_8))
          == 0);

    } catch (QATException | IOException e) {
      fail(e.getMessage());
    }
  }
  @Test
  public void testChunkedCompressionWithByteArrayDiffOffset() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO);
      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      int compressedSize =
          intQatSession.compress(src, 3, src.length - 3, dest, 0, dest.length);

      int decompressedSize = intQatSession.decompress(
          dest, 0, compressedSize, unCompressed, 3, unCompressed.length - 3);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length - 3);

      assertTrue(
          book2.substring(3).compareTo(
              new String(unCompressed, StandardCharsets.UTF_8).substring(3))
          == 0);

    } catch (QATException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  void testCompressorText() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO);
      byte[] data = new byte[1 << 20];
      new Random().nextBytes(data);
      ByteBuffer src = ByteBuffer.allocate(data.length);
      src.put(data);
      src.flip();

      // Prepend some random bytes to the output and compress
      final int outOffset = 3;
      byte[] garbage =
          new byte[outOffset + intQatSession.maxCompressedLength(data.length)];

      new Random().nextBytes(garbage);
      ByteBuffer dest = ByteBuffer.allocate(
          outOffset + intQatSession.maxCompressedLength(data.length));
      dest.put(garbage);
      dest.clear();
      dest.position(outOffset);

      int compressedSize = intQatSession.compress(src, dest);
      int compressedLength = dest.position() - outOffset;
      assertEquals(compressedSize, compressedLength);

      ByteBuffer result = ByteBuffer.allocate(data.length + 100);
      dest.flip();
      dest.position(outOffset);

      int decompressedSize = intQatSession.decompress(dest, result);
      src.flip();
      result.flip();

      assertEquals(decompressedSize, data.length);
      assertTrue(result.compareTo(src) == 0);

    } catch (QATException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithByteBuff() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO);
      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] unCompressed = new byte[src.length];

      ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.length);
      ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(
          intQatSession.maxCompressedLength(src.length));
      ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(src.length);

      srcBuffer.put(src);
      srcBuffer.flip();

      int compressedSize = intQatSession.compress(srcBuffer, compressedBuffer);
      compressedBuffer.flip();
      int decompressedSize =
          intQatSession.decompress(compressedBuffer, decompressedBuffer);
      decompressedBuffer.flip();
      decompressedBuffer.get(unCompressed, 0, decompressedSize);
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);

      assertTrue(
          book2.compareTo(new String(unCompressed, Charset.defaultCharset()))
          == 0);

    } catch (QATException | IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithWrappedByteBuff() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO);
      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] unCompressed = new byte[src.length];

      ByteBuffer srcBuffer = ByteBuffer.allocate(src.length);
      ByteBuffer compressedBuffer =
          ByteBuffer.allocate(intQatSession.maxCompressedLength(src.length));
      ByteBuffer decompressedBuffer = ByteBuffer.allocate(src.length);

      srcBuffer.put(src);
      srcBuffer.flip();

      int compressedSize = intQatSession.compress(srcBuffer, compressedBuffer);
      compressedBuffer.flip();
      int decompressedSize =
          intQatSession.decompress(compressedBuffer, decompressedBuffer);

      decompressedBuffer.flip();
      decompressedBuffer.get(unCompressed, 0, decompressedSize);
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);

      assertTrue(
          book2.compareTo(new String(unCompressed, Charset.defaultCharset()))
          == 0);

    } catch (QATException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithWrappedByteBuffLZ4() {
    try {
      intQatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4);
      byte[] src = Files.readAllBytes(Path.of("src/main/resources/book1"));
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] unCompressed = new byte[src.length];

      ByteBuffer srcBuffer = ByteBuffer.allocate(src.length);
      ByteBuffer compressedBuffer =
          ByteBuffer.allocate(intQatSession.maxCompressedLength(src.length));
      ByteBuffer decompressedBuffer = ByteBuffer.allocate(src.length);
      srcBuffer.put(src);
      srcBuffer.flip();

      int compressedSize = intQatSession.compress(srcBuffer, compressedBuffer);
      compressedBuffer.flip();
      int decompressedSize =
          intQatSession.decompress(compressedBuffer, decompressedBuffer);

      decompressedBuffer.flip();
      decompressedBuffer.get(unCompressed, 0, decompressedSize);
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);

      assertTrue(
          book2.compareTo(new String(unCompressed, Charset.defaultCharset()))
          == 0);

    } catch (QATException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testInvalidDCompressionOffsets() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.AUTO);
      byte[] src = new byte[100];
      RANDOM.nextBytes(src);
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      // intQatSession.setIsPinnedMemAvailable();

      intQatSession.compress(src, -1, src.length, dest, 0, dest.length);
      int decompressedSize = intQatSession.decompress(
          dest, 0, dest.length, unCompressed, 0, unCompressed.length);

      fail();
    } catch (QATException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidDCompressionOffsetsHW() {
    assumeTrue(QATTestSuite.FORCE_HARDWARE);
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.HARDWARE);
      byte[] src = new byte[100];
      RANDOM.nextBytes(src);
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      // intQatSession.setIsPinnedMemAvailable();

      intQatSession.compress(src, -1, src.length, dest, 0, dest.length);
      int decompressedSize = intQatSession.decompress(
          dest, 0, dest.length, unCompressed, 0, unCompressed.length);

      fail();
    } catch (QATException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidDCompressionLargeOffsets() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.HARDWARE);
      byte[] src = new byte[100];
      RANDOM.nextBytes(src);
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      // intQatSession.setIsPinnedMemAvailable();

      intQatSession.compress(
          src, src.length + 1, src.length, dest, 0, dest.length);
      int decompressedSize = intQatSession.decompress(
          dest, 0, dest.length, unCompressed, 0, unCompressed.length);

      fail();
    } catch (QATException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidDDecompressionOffsets() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.HARDWARE);
      byte[] src = new byte[100];
      RANDOM.nextBytes(src);
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      // intQatSession.setIsPinnedMemAvailable();

      intQatSession.compress(src, 0, src.length, dest, 0, dest.length);
      int decompressedSize = intQatSession.decompress(
          dest, -1, dest.length, unCompressed, 0, unCompressed.length);

      fail();
    } catch (QATException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidDDecompressionLargeOffsets() {
    try {
      intQatSession = new QATSession(
          QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.HARDWARE);
      byte[] src = new byte[100];
      RANDOM.nextBytes(src);
      String book2 = new String(src, StandardCharsets.UTF_8);
      byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
      byte[] unCompressed = new byte[src.length];

      // intQatSession.setIsPinnedMemAvailable();

      intQatSession.compress(src, 0, src.length, dest, 0, dest.length);
      int decompressedSize = intQatSession.decompress(dest, dest.length + 1,
          dest.length, unCompressed, 0, unCompressed.length);

      fail();
    } catch (QATException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }
  @Test
  public void testCleaner() {
    QATSession qatSession = new QATSession();
    this.cleanable = cleaner.register(qatSession, qatSession.cleanUp());
    cleanable.clean();
  }
}
