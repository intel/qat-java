/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

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

  // Note: ZSTD level range is [-7, 22], QAT-ZSTD level range is [1, 12]
  public static Stream<Arguments> provideLevelParams() {
    return Stream.of(Arguments.of(1), Arguments.of(6), Arguments.of(12));
  }

  public static Stream<Arguments> provideLengthParams() {
    return Stream.of(Arguments.of(131072), Arguments.of(524288), Arguments.of(2097152));
  }

  @Test
  public void testDefaultConstructor() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    assertTrue(seqprod != null);
  }

  @Test
  public void startAndStopQatDevice() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    int status;
    status = QatZstdSequenceProducer.startDevice();
    assertTrue(status == QatZstdSequenceProducer.Status.OK);
    QatZstdSequenceProducer.stopDevice();
  }

  // calling startDevice() when it has already been started is okay
  @Test
  public void doubleStartQatDevice() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    int status;
    status = QatZstdSequenceProducer.startDevice();
    assertTrue(status == QatZstdSequenceProducer.Status.OK);
    status = QatZstdSequenceProducer.startDevice();
    assertTrue(status == QatZstdSequenceProducer.Status.OK);
    QatZstdSequenceProducer.stopDevice();
  }

  // calling stopDevice() when it has not been started is okay
  @Test
  public void stopDeviceOnly() {
    QatZstdSequenceProducer.stopDevice();
  }

  // test start -> stop -> start sequence works
  @Test
  public void restartQatDevice() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    int status;
    status = QatZstdSequenceProducer.startDevice();
    assertTrue(status == QatZstdSequenceProducer.Status.OK);
    QatZstdSequenceProducer.stopDevice();
    status = QatZstdSequenceProducer.startDevice();
    assertTrue(status == QatZstdSequenceProducer.Status.OK);
    QatZstdSequenceProducer.stopDevice();
  }

  // getFunctionPointer() correctly returns a function pointer, which should never be NULL
  @Test
  public void getFunctionPointer() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    long funcPointer = seqprod.getFunctionPointer();
    assertTrue(funcPointer != 0);
  }

  // createState allocates memory and returns a pointer, which should never be NULL
  @Test
  public void createSeqProdState() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    long state = seqprod.createState();
    assertTrue(state != 0);
  }

  // freeState frees memory associated with the state struct, but does not change the pointer
  @Test
  public void freeSeqProdState() {
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    long state = seqprod.createState();
    seqprod.freeState(state);
    assertTrue(state != 0);
  }

  @Test
  public void testHelloWorld() {
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());

      String inputStr = "Hello, world!";
      byte[] src = inputStr.getBytes();
      byte[] dst = cctx.compress(src);
      byte[] dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (ZstdException e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("provideLevelParams")
  public void testSimpleCompressionByteArray(int level) {
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());
      cctx.setLevel(level);

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = cctx.compress(src);
      byte[] dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (IOException | ZstdException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testSimpleCompressionDefaultLevel() {
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
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
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
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
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
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
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
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
    try (ZstdCompressCtx cctx = new ZstdCompressCtx();
        ZstdDecompressCtx dctx = new ZstdDecompressCtx(); ) {
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
}
