/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH benchmark for Java standard library compression (java.util.zip). Measures compression and
 * decompression performance using Deflater/Inflater.
 *
 * <p>Parameters: - inputFilePath: Path to the file to compress - compressionLevel: Compression
 * level (0-9, default: 6) - blockSizeBytes: Size of each compression block (default: 65536)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class DeflaterBench {
  private static final AtomicBoolean resultsLogged = new AtomicBoolean(false);
  private static final double COMPRESSION_BUFFER_OVERHEAD = 1.25;

  /**
   * Thread-local state for each benchmark thread. Maintains input data and
   * compression/decompression buffers.
   */
  @State(Scope.Thread)
  public static class ThreadState {
    @Param({""})
    public String inputFilePath;

    @Param({"6"})
    public int compressionLevel;

    @Param({"65536"})
    public int blockSizeBytes;

    private byte[] inputData;
    private byte[] compressionBuffer;
    private byte[] compressedData;
    private byte[] decompressedData;
    private int compressedDataLength;

    @Setup
    public void setup() throws IOException, DataFormatException {
      validateInputFile();
      loadAndPrepareBuffers();
      compressInputData();
      verifyDecompression();
      logCompressionStatistics();
    }

    private void validateInputFile() throws IOException {
      if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
        throw new IllegalArgumentException("Input file path must be specified");
      }
      if (!Files.exists(Paths.get(inputFilePath))) {
        throw new IOException("Input file not found: " + inputFilePath);
      }
    }

    private void loadAndPrepareBuffers() throws IOException {
      inputData = Files.readAllBytes(Paths.get(inputFilePath));
      int inputLength = inputData.length;

      // Calculate buffer size with overhead for compression expansion
      int maxChunkSize = (int) (blockSizeBytes * COMPRESSION_BUFFER_OVERHEAD);
      int numberOfChunks = (inputLength + blockSizeBytes - 1) / blockSizeBytes;
      compressionBuffer = new byte[numberOfChunks * maxChunkSize];

      // Initialize decompression buffer
      decompressedData = new byte[inputLength];
    }

    private void compressInputData() {
      Deflater deflater = new Deflater(compressionLevel);
      int inputOffset = 0;
      int outputOffset = 0;
      int inputLength = inputData.length;

      try {
        while (inputOffset < inputLength) {
          int blockLength = Math.min(blockSizeBytes, inputLength - inputOffset);
          deflater.setInput(inputData, inputOffset, blockLength);
          deflater.finish();

          int compressedChunkSize =
              deflater.deflate(compressionBuffer, outputOffset, blockSizeBytes);
          outputOffset += compressedChunkSize;
          inputOffset += deflater.getBytesRead();
          deflater.reset();
        }

        compressedDataLength = outputOffset;
        compressedData = new byte[compressedDataLength];
        System.arraycopy(compressionBuffer, 0, compressedData, 0, compressedDataLength);
      } finally {
        deflater.end();
      }
    }

    private void verifyDecompression() throws DataFormatException {
      Inflater inflater = new Inflater();
      int compressedOffset = 0;
      int decompressedOffset = 0;

      try {
        while (compressedOffset < compressedDataLength) {
          int blockLength = Math.min(blockSizeBytes, compressedDataLength - compressedOffset);
          inflater.setInput(compressedData, compressedOffset, blockLength);

          int decompressedChunkSize =
              inflater.inflate(
                  decompressedData,
                  decompressedOffset,
                  Math.min(blockSizeBytes, decompressedData.length - decompressedOffset));

          decompressedOffset += decompressedChunkSize;
          compressedOffset += inflater.getBytesRead();
          inflater.reset();
        }
      } finally {
        inflater.end();
      }
    }

    private void logCompressionStatistics() {
      if (resultsLogged.compareAndSet(false, true)) {
        double compressionRatio = (double) inputData.length / compressedDataLength;
        double compressionPercentage = 100.0 * (1.0 - 1.0 / compressionRatio);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("COMPRESSION STATISTICS");
        System.out.println("=".repeat(60));
        System.out.printf("Compression Level: %d%n", compressionLevel);
        System.out.printf("Chunk Size:      %,d bytes%n", blockSizeBytes);
        System.out.printf("Original Size:   %,d bytes%n", inputData.length);
        System.out.printf("Compressed Size: %,d bytes%n", compressedDataLength);
        System.out.printf(
            "Compression Ratio: %.2f (%.1f%% reduction)%n",
            compressionRatio, compressionPercentage);
        System.out.println("=".repeat(60) + "\n");
      }
    }

    @TearDown
    public void tearDown() {
      // Clean up large buffers if needed
      inputData = null;
      compressionBuffer = null;
      compressedData = null;
      decompressedData = null;
    }
  }

  /**
   * Benchmarks block-by-block compression throughput.
   *
   * @param state Thread-local state containing input data and buffers
   * @return Compressed data length (prevents JIT optimization)
   */
  @Benchmark
  public int compress(ThreadState state) {
    Deflater deflater = new Deflater(state.compressionLevel);
    int inputOffset = 0;
    int outputOffset = 0;
    int inputLength = state.inputData.length;

    try {
      while (inputOffset < inputLength) {
        int blockLength = Math.min(state.blockSizeBytes, inputLength - inputOffset);
        deflater.setInput(state.inputData, inputOffset, blockLength);
        deflater.finish();

        int compressedChunkSize =
            deflater.deflate(
                state.compressionBuffer,
                outputOffset,
                (int) (state.blockSizeBytes * COMPRESSION_BUFFER_OVERHEAD));

        outputOffset += compressedChunkSize;
        inputOffset += deflater.getBytesRead();
        deflater.reset();
      }

      return outputOffset;
    } finally {
      deflater.end();
    }
  }

  /**
   * Benchmarks block-by-block decompression throughput.
   *
   * @param state Thread-local state containing compressed data and buffers
   * @return Decompressed data length (prevents JIT optimization)
   * @throws DataFormatException if decompression fails
   */
  @Benchmark
  public int decompress(ThreadState state) throws DataFormatException {
    Inflater inflater = new Inflater();
    int compressedOffset = 0;
    int decompressedOffset = 0;
    int compressedLength = state.compressedData.length;

    try {
      while (compressedOffset < compressedLength) {
        int blockLength = Math.min(state.blockSizeBytes, compressedLength - compressedOffset);
        inflater.setInput(state.compressedData, compressedOffset, blockLength);

        int decompressedChunkSize =
            inflater.inflate(
                state.decompressedData,
                decompressedOffset,
                Math.min(state.blockSizeBytes, state.decompressedData.length - decompressedOffset));

        decompressedOffset += decompressedChunkSize;
        compressedOffset += inflater.getBytesRead();
        inflater.reset();
      }

      return decompressedOffset;
    } finally {
      inflater.end();
    }
  }
}
