/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * JMH benchmark for ZSTD compression via zstd-jni streaming API. Measures compression and
 * decompression performance using ZstdOutputStream and ZstdInputStream.
 *
 * <p>Parameters: - inputFilePath: Path to the file to compress - compressionLevel: Compression
 * level (1-22, default: 3) - bufferSizeBytes: I/O buffer size (default: 65536)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class ZstdStreamBench {
  private static final AtomicBoolean resultsLogged = new AtomicBoolean(false);

  /**
   * Thread-local state for each benchmark thread. Maintains input data and stream-compressed
   * buffers.
   */
  @State(Scope.Thread)
  public static class ThreadState {
    @Param({""})
    public String inputFilePath;

    @Param({"3"})
    public int compressionLevel;

    @Param({"65536"})
    public int bufferSizeBytes;

    private byte[] inputData;
    private byte[] compressedData;

    @Setup
    public void setup() throws IOException {
      validateInputFile();
      loadInputData();
      compressInputDataUsingStream();
      verifyDecompression();
      logCompressionStatistics();
    }

    @TearDown
    public void tearDown() {
      // Clean up to allow garbage collection
      inputData = null;
      compressedData = null;
    }

    private void validateInputFile() throws IOException {
      if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
        throw new IllegalArgumentException("Input file path must be specified");
      }
      if (!Files.exists(Paths.get(inputFilePath))) {
        throw new IOException("Input file not found: " + inputFilePath);
      }
    }

    private void loadInputData() throws IOException {
      inputData = Files.readAllBytes(Paths.get(inputFilePath));
    }

    private void compressInputDataUsingStream() throws IOException {
      ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();

      try (ZstdOutputStream zstdOutputStream =
          new ZstdOutputStream(compressedOutput, compressionLevel)) {
        zstdOutputStream.write(inputData);
      }

      compressedData = compressedOutput.toByteArray();
    }

    private void verifyDecompression() throws IOException {
      ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedData);

      int totalBytesRead = 0;
      byte[] buffer = new byte[bufferSizeBytes];

      try (ZstdInputStream zstdInputStream = new ZstdInputStream(compressedInput)) {
        int bytesRead;
        while ((bytesRead = zstdInputStream.read(buffer)) != -1) {
          totalBytesRead += bytesRead;
        }
      }

      if (totalBytesRead != inputData.length) {
        throw new IllegalStateException(
            "Decompression size mismatch: expected "
                + inputData.length
                + ", got "
                + totalBytesRead);
      }
    }

    private void logCompressionStatistics() {
      if (resultsLogged.compareAndSet(false, true)) {
        double compressionRatio = (double) inputData.length / compressedData.length;
        double compressionPercentage = 100.0 * (1.0 - 1.0 / compressionRatio);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("ZSTD STREAMING COMPRESSION STATISTICS");
        System.out.println("=".repeat(60));
        System.out.printf("Compression Level: %d%n", compressionLevel);
        System.out.printf("Buffer Size:       %,d bytes%n", bufferSizeBytes);
        System.out.printf("Original Size:     %,d bytes%n", inputData.length);
        System.out.printf("Compressed Size:   %,d bytes%n", compressedData.length);
        System.out.printf(
            "Compression Ratio: %.2f (%.1f%% reduction)%n",
            compressionRatio, compressionPercentage);
        System.out.println("=".repeat(60) + "\n");
      }
    }
  }

  /**
   * Benchmarks ZSTD streaming compression throughput. Measures the performance of compressing data
   * using ZstdOutputStream.
   *
   * @param state Thread-local state containing input data
   * @return Compressed data (prevents JIT optimization)
   * @throws IOException if compression fails
   */
  @Benchmark
  public byte[] compress(ThreadState state) throws IOException {
    ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();

    try (ZstdOutputStream zstdOutputStream =
        new ZstdOutputStream(compressedOutput, state.compressionLevel)) {
      zstdOutputStream.write(state.inputData);
    }

    return compressedOutput.toByteArray();
  }

  /**
   * Benchmarks ZSTD streaming decompression throughput. Measures the performance of decompressing
   * data using ZstdInputStream.
   *
   * @param state Thread-local state containing compressed data
   * @return Total bytes decompressed (prevents JIT optimization)
   * @throws IOException if decompression fails
   */
  @Benchmark
  public int decompress(ThreadState state) throws IOException {
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(state.compressedData);

    int totalBytesRead = 0;
    byte[] buffer = new byte[state.bufferSizeBytes];

    try (ZstdInputStream zstdInputStream = new ZstdInputStream(compressedInput)) {
      int bytesRead;
      while ((bytesRead = zstdInputStream.read(buffer)) != -1) {
        totalBytesRead += bytesRead;
      }
    }

    return totalBytesRead;
  }
}
