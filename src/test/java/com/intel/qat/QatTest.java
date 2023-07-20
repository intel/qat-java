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
  private QatZip qatSession;
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
      qatSession.end();
  }

  @Test
  public void testDefaultConstructor() {
    try {
      qatSession = new QatZip();
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testSingleArgConstructor() {
    try {
      qatSession = new QatZip(QatZip.Codec.LZ4);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testTwoArgConstructor() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testThreeArgConstructorAuto() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 1, QatZip.Mode.AUTO);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testThreeArgConstructorHW() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 1, QatZip.Mode.HARDWARE);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testFourArgConstructorHW() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.HARDWARE, QatZip.DEFAULT_RETRY_COUNT);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testTearDown() {
    try {
      QatZip qatSession = new QatZip();
      qatSession.end();
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void duplicateTearDown() {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      QatZip qatSession = new QatZip(QatZip.Codec.LZ4, 0, QatZip.Mode.HARDWARE);
      qatSession.end();
      qatSession.end();
    } catch (IllegalStateException is) {
      assertTrue(true);
    } catch (IllegalArgumentException | QatException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testCleaner() {
    QatZip qatSession = new QatZip();
    cleanable = cleaner.register(qatSession, qatSession.getCleaner());
    cleanable.clean();
  }

  @Test
  public void testCompressWithNullByteBuffer() {
    try {
      qatSession = new QatZip();
      ByteBuffer buf = null;
      qatSession.compress(buf, buf);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testCompressWithNullByteArray() {
    try {
      qatSession = new QatZip();
      qatSession.compress(null, 0, 100, null, 0, 0);
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testDecompressWithNullByteBuffer() {
    try {
      qatSession = new QatZip();
      ByteBuffer buf = null;
      int compressedSize = qatSession.decompress(buf, buf);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testDecompressWithNullByteArray() {
    try {
      qatSession = new QatZip();
      int compressedSize = qatSession.decompress(null, 0, 100, null, 0, 0);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidCompressionLevel() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 10, QatZip.Mode.AUTO, 6);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testInvalidRetryCount() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 10, QatZip.Mode.AUTO, -1);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void maxCompressedLengthPostTearDown() {
    try {
      QatZip qatSession = new QatZip();
      qatSession.end();
      qatSession.maxCompressedLength(100);
      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testChunkedCompressionWithByteArray() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/sample.txt"));
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int compressedSize = qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      int decompressedSize = qatSession.decompress(dst, 0, compressedSize, dec, 0, dec.length);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithByteArrayDiffOffset() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/sample.txt"));
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int compressedSize = qatSession.compress(src, 3, src.length - 3, dst, 0, dst.length);

      int decompressedSize = qatSession.decompress(dst, 0, compressedSize, dec, 3, dec.length - 3);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length - 3);

      String str = new String(src, StandardCharsets.UTF_8);
      assertTrue(str.substring(3).compareTo(new String(dec, StandardCharsets.UTF_8).substring(3)) == 0);

    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithByteBuffer() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/sample.txt"));
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer comBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src);
      srcBuf.flip();

      int compressedSize = qatSession.compress(srcBuf, comBuf);
      comBuf.flip();

      int decompressedSize = qatSession.decompress(comBuf, decBuf);
      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IOException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithWrappedByteBuffer() {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.AUTO);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/sample.txt"));
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer comBuf = ByteBuffer.allocate(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocate(src.length);

      srcBuf.put(src);
      srcBuf.flip();

      int compressedSize = qatSession.compress(srcBuf, comBuf);
      comBuf.flip();

      int decompressedSize = qatSession.decompress(comBuf, decBuf);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testChunkedCompressionWithWrappedByteBufferLZ4() {
    try {
      qatSession = new QatZip(QatZip.Codec.LZ4);

      byte[] src = Files.readAllBytes(Path.of("src/main/resources/sample.txt"));
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer comBuf = ByteBuffer.allocate(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocate(src.length);
      srcBuf.put(src);
      srcBuf.flip();

      int compressedSize = qatSession.compress(srcBuf, comBuf);
      comBuf.flip();

      int decompressedSize = qatSession.decompress(comBuf, decBuf);

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
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testWrappedBuffers(int len) {
    try {
      qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.wrap(src);
      ByteBuffer dstBuf = ByteBuffer.wrap(dst);
      ByteBuffer decBuf = ByteBuffer.wrap(dec);

      int compressedSize = qatSession.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);
      assertNotNull(dstBuf);

      dstBuf.flip();

      int decompressedSize = qatSession.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IllegalStateException | IllegalArgumentException | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testBackedArrayBuffersWithAllocate(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocate(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocate(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      int compressedSize = qatSession.compress(srcBuf, dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qatSession.decompress(dstBuf, decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IllegalStateException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testIndirectBuffersReadOnly(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocate(dst.length);
      ByteBuffer decBuf = ByteBuffer.allocate(dec.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      int compressedSize = qatSession.compress(srcBuf.asReadOnlyBuffer(), dstBuf);

      assertTrue(compressedSize > 0);

      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qatSession.decompress(dstBuf.asReadOnlyBuffer(), decBuf);

      assertNotNull(decBuf);
      assertTrue(decompressedSize > 0);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);
      assertTrue(Arrays.equals(dec, src));
    } catch (QatException | IllegalStateException | IllegalArgumentException | ReadOnlyBufferException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithByteArray(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];

      int compressedSize = qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      assertNotNull(dst);

      qatSession.decompress(dst, 0, compressedSize, dec, 0, dec.length);
      assertNotNull(dec);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IllegalStateException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithByteArrayLZ4(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.LZ4, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];

      int compressedSize = qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      assertNotNull(dst);

      qatSession.decompress(dst, 0, compressedSize, dec, 0, dec.length);
      assertNotNull(dec);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException | IllegalStateException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionHW(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.HARDWARE, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];

      int compressedSize = qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      assertNotNull(dst);

      qatSession.decompress(dst, 0, compressedSize, dec, 0, dec.length);

      assertNotNull(dec);
      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionWithInsufficientDestBuff(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.AUTO, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[src.length / 10];

      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionWithInsufficientDestBuffHW(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.HARDWARE, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[src.length / 10];

      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testDecompressionWithInsufficientDestBuff(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.HARDWARE, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length / 2];
      byte[] dst = new byte[src.length];

      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, 0, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | IndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithDirectByteBuffer(int len) {
    try {
      qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qatSession.compress(srcBuf, dstBuf);
      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qatSession.decompress(dstBuf, decBuf);
      assertNotNull(dec);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionWithDirectByteBufferNoPinnedMem(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qatSession.compress(srcBuf, dstBuf);
      assertNotNull(dstBuf);

      dstBuf.flip();
      int decompressedSize = qatSession.decompress(dstBuf, decBuf);
      assertNotNull(dec);

      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionReadOnlyDestination(int len) {
    try {
      qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();
      qatSession.compress(srcBuf, dstBuf.asReadOnlyBuffer());

      fail();
    } catch (ReadOnlyBufferException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testDecompressionReadOnlyDestination(int len) {
    try {
      qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qatSession.compress(srcBuf, dstBuf);
      dstBuf.flip();
      qatSession.decompress(dstBuf, decBuf.asReadOnlyBuffer());

      fail();
    } catch (ReadOnlyBufferException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressionDecompressionReadOnlyByteBuffer(int len) {
    try {
      qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      byte[] dec = new byte[src.length];

      ByteBuffer srcBufRW = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(2 * src.length);
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
      srcBufRW.put(src, 0, src.length);
      srcBufRW.flip();

      ByteBuffer srcBufRO = srcBufRW.asReadOnlyBuffer();
      qatSession.compress(srcBufRO, dstBuf);
      assertNotNull(dstBuf);
      dstBuf.flip();

      int decompressedSize = qatSession.decompress(dstBuf, decBuf);
      assertNotNull(dec);
      decBuf.flip();
      decBuf.get(dec, 0, decompressedSize);

      assertTrue(Arrays.equals(src, dec));
    } catch (QatException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testIllegalStateException(int len) {
    try {
      QatZip qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[2 * src.length];

      qatSession.end();
      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testIllegalStateExceptionHW(int len) {
    assumeTrue(QatTestSuite.FORCE_HARDWARE);

    try {
      QatZip qatSession = new QatZip(QatZip.Codec.DEFLATE, QatZip.DEFAULT_COMPRESS_LEVEL, QatZip.Mode.HARDWARE);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[2 * src.length];

      qatSession.end();
      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      fail();
    } catch (IllegalStateException is) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void compressByteArrayPostTearDown(int len) {
    try {
      QatZip qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[2 * src.length];

      qatSession.end();
      qatSession.compress(src, 0, src.length, dst, 0, dst.length);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void compressByteBufferPostTearDown(int len) {
    try {
      QatZip qatSession = new QatZip();

      byte[] src = getSourceArray(len);

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qatSession.end();
      qatSession.compress(srcBuf, dstBuf);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void decompressByteArrayPostTearDown(int len) {
    try {
      QatZip qatSession = new QatZip();

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[2 * src.length];
      qatSession.compress(src, 0, src.length, dst, 0, dst.length);

      qatSession.end();
      qatSession.decompress(src, 0, src.length, dst, 0, dst.length);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void decompressByteBufferPostTearDown(int len) {
    try {
      QatZip qatSession = new QatZip();

      byte[] src = getSourceArray(len);

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      ByteBuffer dstBuf = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(src.length));
      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);

      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      qatSession.compress(srcBuf, dstBuf);

      dstBuf.flip();
      qatSession.end();
      qatSession.decompress(dstBuf, decBuf);

      fail();
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testCompressorText(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 6, QatZip.Mode.AUTO);

      byte[] data = getSourceArray(len);
      ByteBuffer src = ByteBuffer.allocate(data.length);
      src.put(data);
      src.flip();

      // Prepend some random bytes to the output and compress
      final int outOffset = 3;
      byte[] garbage = new byte[outOffset + qatSession.maxCompressedLength(data.length)];

      new Random().nextBytes(garbage);
      ByteBuffer dst = ByteBuffer.allocate(outOffset + qatSession.maxCompressedLength(data.length));
      dst.put(garbage);
      dst.clear();
      dst.position(outOffset);

      int compressedSize = qatSession.compress(src, dst);
      int comLen = dst.position() - outOffset;
      assertEquals(compressedSize, comLen);

      ByteBuffer result = ByteBuffer.allocate(data.length + 100);
      dst.flip();
      dst.position(outOffset);

      int decompressedSize = qatSession.decompress(dst, result);
      src.flip();
      result.flip();

      assertEquals(decompressedSize, data.length);
      assertTrue(result.compareTo(src) == 0);

    } catch (QatException | IllegalArgumentException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidCompressionOffsets(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qatSession.compress(src, -1, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, 0, dst.length, dec, 0, dec.length);

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
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qatSession.compress(src, -1, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, 0, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidCompressionLargeOffsets(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qatSession.compress(src, src.length + 1, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, 0, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidecompressionOffsets(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, -1, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 2048, 2097152})
  public void testInvalidecompressionLargeOffsets(int len) {
    try {
      qatSession = new QatZip(QatZip.Codec.DEFLATE, 9, QatZip.Mode.AUTO, 0, 0);

      byte[] src = getSourceArray(len);
      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, dst.length + 1, dst.length, dec, 0, dec.length);

      fail();
    } catch (QatException | ArrayIndexOutOfBoundsException e) {
      assertTrue(true);
    }
  }
}
