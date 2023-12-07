/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class QatZstdSequenceProducerTests {
  private final String SAMPLE_TEXT_PATH = "src/test/resources/sample.txt";

  private byte[] readAllBytes(String fileName) throws IOException {
    return Files.readAllBytes(Path.of(fileName));
  }

  private Random rnd = new Random();

  private byte[] getRandomBytes(int len) {
    byte[] bytes = new byte[len];
    rnd.nextBytes(bytes);
    return bytes;
  }

  // TODO: concerned that too many parameters make unit tests take a very long time to complete
  // Note: ZSTD levels are [-7, 22], QAT-ZSTD levels are [1-12]
  public static Stream<Arguments> provideLevelParams() {
    return Stream.of(
        Arguments.of(1),
        // Arguments.of(2),
        // Arguments.of(3),
        // Arguments.of(4),
        // Arguments.of(5),
        Arguments.of(6),
        // Arguments.of(7),
        Arguments.of(12));
  }

  public static Stream<Arguments> provideLengthParams() {
    return Stream.of(Arguments.of(131072), Arguments.of(524288), Arguments.of(2097152));
  }

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // test constructor
  @Test
  public void testDefaultConstructor() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      assertTrue(seqprod != null);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // test starting and stopping device
  @Test
  public void startAndStopQatDevice() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      int status;
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // calling startDevice() when it has already been started is okay
  @Test
  public void doubleStartQatDevice() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      int status;
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // calling stopDevice() when it has not been started is okay
  @Test
  public void stopDeviceOnly() {
    try {
      QatZstdSequenceProducer.stopDevice();
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  //  no need to call stopDevice() twice, same as stopping it once, no test

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // test start -> stop -> start sequence works
  @Test
  public void restartQatDevice() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      int status;
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
      QatZstdSequenceProducer.stopDevice();
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // getFunctionPointer() correctly returns a function pointer, which should never be NULL
  @Test
  public void getFunctionPointer() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      long funcPointer = seqprod.getFunctionPointer();
      assertTrue(funcPointer != 0);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // getFunctionPointer() works the same whether or not qatDevice was activated, no test

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // createState allocates memory and returns a pointer, which should never be NULL
  @Test
  public void createSeqProdState() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      long state = seqprod.createState();
      assertTrue(state != 0);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // createState() works the same whether or not qatDevice was activated, no test

  // create multiple states? - shouldn't be any difference, no test

  // freeState frees memory associated with the state struct, but does not change the pointer
  // TODO: make this test more meaningful, or remove it
  @Test
  public void freeSeqProdState() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      long state = seqprod.createState();
      seqprod.freeState(state);
      assertTrue(state != 0);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // free state when passed NULL correctly does nothing - should this be tested?

  // UNDEFINED BEHAVIOR, shouldn't test:
  // freeState when passed a random invalid pointer
  // freeState when passed a negative number

  // ZstdCCtx registerSequenceProducer with null object - not in qat-java, so don't test

  // ZstdCompressionCtx registerSequenceProducer with normal use cases

  @Test
  public void testHelloWorld() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());

      String inputStr = "Hello, world!";
      byte[] src = inputStr.getBytes();
      // TODO: we don't need to define these array sizes if the calls are 'dst = ...'
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      dst = cctx.compress(src);
      dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (ZstdException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideLevelParams")
  public void testSimpleCompression(int level) {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());
      cctx.setLevel(level);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      dst = cctx.compress(src);
      dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (IOException | ZstdException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testSimpleCompressionDefaultLevel() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());
      // Default ZSTD compression level is 3

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      dst = cctx.compress(src);
      dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (IOException | ZstdException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testInvalidCompressionLevelNoFallback() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());
      cctx.setLevel(13);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];

      dst = cctx.compress(src);
      fail();
    } catch (IOException e) {
      fail(e.getMessage());
    } catch (ZstdException e) {
      assertTrue(true);
    }
    QatZstdSequenceProducer.stopDevice();
  }

  @Test
  public void testInvalidCompressionLevelWithFallback() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());
      cctx.setSequenceProducerFallback(true);
      cctx.setLevel(13);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      dst = cctx.compress(src);
      dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (IOException | ZstdException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideLengthParams")
  public void testCompressionWithInsufficientDestBuff(int len) {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());

      byte[] src = getRandomBytes(len);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length) / 10];

      int compressedSize = cctx.compress(dst, src);

      fail();
    } catch (ZstdException e) {
      assertTrue(true);
    }
  }

  @ParameterizedTest
  @MethodSource("provideLevelParams")
  public void testRemovingSequenceProducer(int level) {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());
      cctx.setLevel(level);
      // Still compresses correctly, but no longer use QatZstdSequenceProducer
      cctx.registerSequenceProducer(null);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      dst = cctx.compress(src);
      dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (IOException | ZstdException e) {
      fail(e.getMessage());
    }
  }

  /**
   * TODO: remove this failing test or fix it
   *
   * <p>For some reason this test fails, with the AssertionFailedError: Src size is incorrect. This
   * is an example of what would be a more rigorous way to test the Cctx & Dctx functions, but for
   * some reason fails.
   *
   * <p>Specifically this comes from calling the two lines
   *
   * <p>int compressedSize = cctx.compress(dst, src); dctx.decompress(dec, dst);
   */
  @ParameterizedTest
  @MethodSource("provideLevelParams")
  public void testFailureToReturnSizes() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      int compressedSize = cctx.compress(dst, src);
      // this call to compress() causes an errir to occur in
      // decompress(). ZSTD_error_srcSize_wrong, error 72
      byte[] dst2 = Arrays.copyOf(dst, compressedSize);
      int decompressedSize = dctx.decompress(dec, dst2);

      // If these calls didn't create errors- we WOULD check these ints
      assertTrue(compressedSize > 0);
      assertEquals(decompressedSize, src.length);

      assertTrue(Arrays.equals(src, dec));
    } catch (IOException | ZstdException e) {
      fail(e.getMessage()); // TJP: this will fail for some reason
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // Do we need to further test using eadAllBytes(PATH) vs. getRandomBytes(length)

  // compress/decompress with both byte[] and ByteBuffer
  // Other files have many ByteBuffer tests: direct byte buffer, wrapped byte buffer, translate from
  // byte[] to byte buffer
  // NOTE: TJP - we shouldn't test with all the various ways to define/use ByteBuffers, since
  // zstd-jni truly handles those various conditions

  // zstd-jni has no offsets, no test

  /**
   * Question: in QatZipperTests, what is meaningful difference between
   * testChunkedCompressionWithByteArray vs. testCompressionDecompressionByteArray? (only obvious
   * thing is sample source file or random bytes)
   */
}
