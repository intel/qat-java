/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import com.intel.qat.QatZipper;
import com.intel.qat.QatZipper.Algorithm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * JMH benchmark for Intel QAT compression acceleration. Measures compression and decompression
 * performance across DEFLATE, LZ4, and ZSTD algorithms.
 *
 * <p>Parameters: - inputFilePath: Path to the file to compress - compressionLevel: Compression
 * level (0-9 depending on algorithm) - blockSizeBytes: Size of each compression block (default:
 * 65536) - algorithmName: Compression algorithm (DEFLATE, LZ4, ZSTD)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class QatJavaBench {
  private static final AtomicBoolean resultsLogged = new AtomicBoolean(false);

  /**
   * Thread-local state for each benchmark thread. Maintains input data, compressed/decompressed
   * buffers, and compression metadata.
   */
  @State(Scope.Thread)
  public static class ThreadState {
    @Param({""})
    public String inputFilePath;

    @Param({"6"})
    public int compressionLevel;

    @Param({"65536"})
    public int blockSizeBytes;

    @Param({"DEFLATE", "LZ4", "ZSTD"})
    public String algorithmName;

    private byte[] inputData;
    private byte[] compressedData;
    private byte[] decompressedData;
    private int[] compressedBlockSizes;
    private int[] compressedBlockOffsets;
    private int totalCompressedSize;
    private int numberOfBlocks;
    private Algorithm compressionAlgorithm;
    private QatZipper qzip;

    @Setup
    public void setup() throws IOException {
      validateInputFile();
      compressionAlgorithm = parseAlgorithm(algorithmName);
      qzip = createQatZipper(compressionAlgorithm, compressionLevel);

      loadAndCompressInputFile();
      verifyCompressionResults();
      logCompressionStatistics();
    }

    @TearDown
    public void tearDown() {
      if (qzip != null) {
        qzip.end();
      }
    }

    private void validateInputFile() throws IOException {
      if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
        throw new IllegalArgumentException("Input file path must be specified");
      }
      if (!Files.exists(Paths.get(inputFilePath))) {
        throw new IOException("Input file not found: " + inputFilePath);
      }
    }

    private Algorithm parseAlgorithm(String algorithmName) {
      switch (algorithmName.toUpperCase()) {
        case "DEFLATE":
          return Algorithm.DEFLATE;
        case "LZ4":
          return Algorithm.LZ4;
        case "ZSTD":
          return Algorithm.ZSTD;
        default:
          throw new IllegalArgumentException(
              "Invalid algorithm: " + algorithmName + ". Supported: DEFLATE, LZ4, ZSTD");
      }
    }

    private QatZipper createQatZipper(Algorithm algorithm, int level) {
      return new QatZipper.Builder().setAlgorithm(algorithm).setLevel(level).build();
    }

    private void loadAndCompressInputFile() throws IOException {
      inputData = Files.readAllBytes(Paths.get(inputFilePath));
      decompressedData = new byte[inputData.length];

      numberOfBlocks = calculateNumberOfBlocks(inputData.length, blockSizeBytes);
      compressedBlockSizes = new int[numberOfBlocks];
      compressedBlockOffsets = new int[numberOfBlocks];

      int maxCompressedBlockSize = qzip.maxCompressedLength(blockSizeBytes);
      compressedData = new byte[numberOfBlocks * maxCompressedBlockSize];

      compressBlocks();
      buildOffsetTable();
    }

    private int calculateNumberOfBlocks(int dataLength, int blockSize) {
      return (dataLength + blockSize - 1) / blockSize;
    }

    private void compressBlocks() {
      int currentCompressedOffset = 0;

      for (int blockIndex = 0; blockIndex < numberOfBlocks; blockIndex++) {
        int inputOffset = blockIndex * blockSizeBytes;
        int blockLength = Math.min(blockSizeBytes, inputData.length - inputOffset);

        int blockCompressedSize =
            qzip.compress(
                inputData,
                inputOffset,
                blockLength,
                compressedData,
                currentCompressedOffset,
                qzip.maxCompressedLength(blockSizeBytes));

        compressedBlockSizes[blockIndex] = blockCompressedSize;
        currentCompressedOffset += blockCompressedSize;
      }

      totalCompressedSize = currentCompressedOffset;
    }

    private void buildOffsetTable() {
      int offset = 0;
      for (int i = 0; i < numberOfBlocks; i++) {
        compressedBlockOffsets[i] = offset;
        offset += compressedBlockSizes[i];
      }
    }

    private void verifyCompressionResults() {
      int currentDecompressedOffset = 0;

      for (int blockIndex = 0; blockIndex < numberOfBlocks; blockIndex++) {
        int compressedOffset = compressedBlockOffsets[blockIndex];
        int compressedBlockSize = compressedBlockSizes[blockIndex];
        int blockLength = Math.min(blockSizeBytes, inputData.length - blockIndex * blockSizeBytes);

        qzip.decompress(
            compressedData,
            compressedOffset,
            compressedBlockSize,
            decompressedData,
            currentDecompressedOffset,
            blockLength);

        currentDecompressedOffset += blockLength;
      }
    }

    private void logCompressionStatistics() {
      if (resultsLogged.compareAndSet(false, true)) {
        double compressionRatio = (double) inputData.length / totalCompressedSize;
        double compressionPercentage = 100.0 * (1.0 - 1.0 / compressionRatio);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("COMPRESSION STATISTICS");
        System.out.println("=".repeat(60));
        System.out.printf("Algorithm:       %s%n", algorithmName);
        System.out.printf("Compression Level: %d%n", compressionLevel);
        System.out.printf("Block Size:      %,d bytes%n", blockSizeBytes);
        System.out.printf("Original Size:   %,d bytes%n", inputData.length);
        System.out.printf("Compressed Size: %,d bytes%n", totalCompressedSize);
        System.out.printf("Number of Blocks: %d%n", numberOfBlocks);
        System.out.printf(
            "Compression Ratio: %.2f (%.1f%% reduction)%n",
            compressionRatio, compressionPercentage);
        System.out.println("=".repeat(60) + "\n");
      }
    }
  }

  /**
   * Benchmarks block-by-block compression throughput.
   *
   * @param state Thread-local state containing input data and buffers
   * @return Compressed data (prevents JIT optimization)
   */
  @Benchmark
  public byte[] compress(ThreadState state) {
    int maxCompressedBlockSize = state.qzip.maxCompressedLength(state.blockSizeBytes);
    int currentCompressedOffset = 0;

    for (int blockIndex = 0; blockIndex < state.numberOfBlocks; blockIndex++) {
      int inputOffset = blockIndex * state.blockSizeBytes;
      int blockLength = Math.min(state.blockSizeBytes, state.inputData.length - inputOffset);

      int blockCompressedSize =
          state.qzip.compress(
              state.inputData,
              inputOffset,
              blockLength,
              state.compressedData,
              currentCompressedOffset,
              maxCompressedBlockSize);

      currentCompressedOffset += blockCompressedSize;
    }

    return state.compressedData;
  }

  /**
   * Benchmarks block-by-block decompression throughput.
   *
   * @param state Thread-local state containing compressed data and buffers
   * @return Decompressed data (prevents JIT optimization)
   */
  @Benchmark
  public byte[] decompress(ThreadState state) {
    int currentDecompressedOffset = 0;

    for (int blockIndex = 0; blockIndex < state.numberOfBlocks; blockIndex++) {
      int compressedOffset = state.compressedBlockOffsets[blockIndex];
      int compressedBlockSize = state.compressedBlockSizes[blockIndex];
      int blockLength =
          Math.min(
              state.blockSizeBytes, state.inputData.length - blockIndex * state.blockSizeBytes);

      state.qzip.decompress(
          state.compressedData,
          compressedOffset,
          compressedBlockSize,
          state.decompressedData,
          currentDecompressedOffset,
          blockLength);

      currentDecompressedOffset += blockLength;
    }

    return state.decompressedData;
  }
}
