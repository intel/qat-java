/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import com.github.luben.zstd.Zstd;
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
 * JMH benchmark for ZSTD compression via zstd-jni. Measures compression and decompression
 * performance using Zstd.compressByteArray and Zstd.decompressByteArray methods with block-by-block
 * processing.
 *
 * <p>Parameters: - inputFilePath: Path to the file to compress - compressionLevel: Compression
 * level (1-22, default: 3) - blockSizeBytes: Size of each compression block (default: 65536)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class ZstdBench {
  private static final AtomicBoolean resultsLogged = new AtomicBoolean(false);

  /**
   * Thread-local state for each benchmark thread. Maintains input data and
   * compression/decompression buffers.
   */
  @State(Scope.Thread)
  public static class ThreadState {
    @Param({""})
    public String inputFilePath;

    @Param({"3"})
    public int compressionLevel;

    @Param({"65536"})
    public int blockSizeBytes;

    private byte[] inputData;
    private byte[] compressionBuffer;
    private byte[] compressedData;
    private byte[] decompressedData;
    private int[] compressedBlockSizes;
    private int[] compressedBlockOffsets;
    private int totalCompressedSize;
    private int numberOfBlocks;

    @Setup
    public void setup() throws IOException {
      validateInputFile();
      loadAndPrepareBuffers();
      compressInputDataInBlocks();
      buildOffsetTable();
      verifyCompressionResults();
      logCompressionStatistics();
    }

    @TearDown
    public void tearDown() {
      // Clean up to allow garbage collection
      inputData = null;
      compressionBuffer = null;
      compressedData = null;
      decompressedData = null;
      compressedBlockSizes = null;
      compressedBlockOffsets = null;
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

      numberOfBlocks = calculateNumberOfBlocks(inputLength, blockSizeBytes);
      compressedBlockSizes = new int[numberOfBlocks];
      compressedBlockOffsets = new int[numberOfBlocks];

      // Calculate max compressed size for all blocks
      long maxCompressedSize = 0;
      for (int i = 0; i < numberOfBlocks; i++) {
        int blockLength = Math.min(blockSizeBytes, inputLength - i * blockSizeBytes);
        maxCompressedSize += Zstd.compressBound(blockLength);
      }

      compressionBuffer = new byte[(int) maxCompressedSize];
      decompressedData = new byte[inputLength];
    }

    private int calculateNumberOfBlocks(int dataLength, int blockSize) {
      return (dataLength + blockSize - 1) / blockSize;
    }

    private void compressInputDataInBlocks() {
      int currentCompressedOffset = 0;
      int inputLength = inputData.length;

      for (int blockIndex = 0; blockIndex < numberOfBlocks; blockIndex++) {
        int inputOffset = blockIndex * blockSizeBytes;
        int blockLength = Math.min(blockSizeBytes, inputLength - inputOffset);

        // Compress the block using compressByteArray
        int compressedBlockSize =
            (int)
                Zstd.compressByteArray(
                    compressionBuffer,
                    currentCompressedOffset,
                    compressionBuffer.length - currentCompressedOffset,
                    inputData,
                    inputOffset,
                    blockLength,
                    compressionLevel);

        compressedBlockSizes[blockIndex] = compressedBlockSize;
        currentCompressedOffset += compressedBlockSize;
      }

      totalCompressedSize = currentCompressedOffset;
      compressedData = new byte[totalCompressedSize];
      System.arraycopy(compressionBuffer, 0, compressedData, 0, totalCompressedSize);
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
      int inputLength = inputData.length;

      for (int blockIndex = 0; blockIndex < numberOfBlocks; blockIndex++) {
        int compressedOffset = compressedBlockOffsets[blockIndex];
        int compressedBlockSize = compressedBlockSizes[blockIndex];
        int blockLength = Math.min(blockSizeBytes, inputLength - blockIndex * blockSizeBytes);

        // Decompress the block using decompressByteArray
        int decompressedSize =
            (int)
                Zstd.decompressByteArray(
                    decompressedData,
                    currentDecompressedOffset,
                    blockLength,
                    compressedData,
                    compressedOffset,
                    compressedBlockSize);

        if (decompressedSize != blockLength) {
          throw new IllegalStateException(
              "Decompression size mismatch for block "
                  + blockIndex
                  + ": expected "
                  + blockLength
                  + ", got "
                  + decompressedSize);
        }

        currentDecompressedOffset += decompressedSize;
      }
    }

    private void logCompressionStatistics() {
      if (resultsLogged.compareAndSet(false, true)) {
        double compressionRatio = (double) inputData.length / totalCompressedSize;
        double compressionPercentage = 100.0 * (1.0 - 1.0 / compressionRatio);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("ZSTD COMPRESSION STATISTICS");
        System.out.println("=".repeat(60));
        System.out.printf("Compression Level: %d%n", compressionLevel);
        System.out.printf("Block Size:        %,d bytes%n", blockSizeBytes);
        System.out.printf("Original Size:     %,d bytes%n", inputData.length);
        System.out.printf("Compressed Size:   %,d bytes%n", totalCompressedSize);
        System.out.printf("Number of Blocks:  %d%n", numberOfBlocks);
        System.out.printf(
            "Compression Ratio: %.2f (%.1f%% reduction)%n",
            compressionRatio, compressionPercentage);
        System.out.println("=".repeat(60) + "\n");
      }
    }
  }

  /**
   * Benchmarks ZSTD compression throughput with block-by-block processing. Measures the performance
   * of compressing data in fixed-size blocks using compressByteArray.
   *
   * @param state Thread-local state containing input data and buffers
   * @return Total compressed size (prevents JIT optimization)
   */
  @Benchmark
  public int compress(ThreadState state) {
    int currentCompressedOffset = 0;
    int inputLength = state.inputData.length;

    for (int blockIndex = 0; blockIndex < state.numberOfBlocks; blockIndex++) {
      int inputOffset = blockIndex * state.blockSizeBytes;
      int blockLength = Math.min(state.blockSizeBytes, inputLength - inputOffset);

      int compressedBlockSize =
          (int)
              Zstd.compressByteArray(
                  state.compressionBuffer,
                  currentCompressedOffset,
                  state.compressionBuffer.length - currentCompressedOffset,
                  state.inputData,
                  inputOffset,
                  blockLength,
                  state.compressionLevel);

      currentCompressedOffset += compressedBlockSize;
    }

    return currentCompressedOffset;
  }

  /**
   * Benchmarks ZSTD decompression throughput with block-by-block processing. Measures the
   * performance of decompressing data in fixed-size blocks using decompressByteArray.
   *
   * @param state Thread-local state containing compressed data and buffers
   * @return Total decompressed size (prevents JIT optimization)
   */
  @Benchmark
  public int decompress(ThreadState state) {
    int currentDecompressedOffset = 0;
    int inputLength = state.inputData.length;

    for (int blockIndex = 0; blockIndex < state.numberOfBlocks; blockIndex++) {
      int compressedOffset = state.compressedBlockOffsets[blockIndex];
      int compressedBlockSize = state.compressedBlockSizes[blockIndex];
      int blockLength =
          Math.min(state.blockSizeBytes, inputLength - blockIndex * state.blockSizeBytes);

      int decompressedSize =
          (int)
              Zstd.decompressByteArray(
                  state.decompressedData,
                  currentDecompressedOffset,
                  blockLength,
                  state.compressedData,
                  compressedOffset,
                  compressedBlockSize);

      currentDecompressedOffset += decompressedSize;
    }

    return currentDecompressedOffset;
  }
}
