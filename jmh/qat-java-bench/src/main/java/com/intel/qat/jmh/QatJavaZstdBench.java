/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import com.intel.qat.QatZipper;
import com.intel.qat.QatZipper.Algorithm;
import com.intel.qat.QatZipper.Mode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class QatJavaZstdBench {
  private static AtomicBoolean flag = new AtomicBoolean(false);

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @Param({"65536"})
  static int chunkSize;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {

        QatZipper qzip =
            new QatZipper.Builder()
                .setMode(Mode.HARDWARE)
                .setAlgorithm(Algorithm.DEFLATE)
                .setLevel(level)
                .build();

        // Read input
        src = Files.readAllBytes(Paths.get(file));

        int maxCompressedSize = qzip.maxCompressedLength(chunkSize);
        byte[] compressedChunk = new byte[maxCompressedSize];
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        for (int offset = 0; offset < src.length; offset += chunkSize) {
          int length = Math.min(chunkSize, src.length - offset);

          int compressedSize =
              qzip.compress(src, offset, length, compressedChunk, 0, maxCompressedSize);

          // Write simple header for each chunk.
          compressedOut.write(intToBytes(length));
          compressedOut.write(intToBytes(compressedSize));

          // Write only valid compressed bytes.
          compressedOut.write(compressedChunk, 0, compressedSize);
        }
        compressed = compressedOut.toByteArray();

        ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
        int compressedChunkSize = 0;
        for (int offset = 0; offset < compressed.length; offset += compressedChunkSize) {
          int uncompressedChunkSize = bytesToInt(compressed, offset);
          compressedChunkSize = bytesToInt(compressed, offset + 4);
          offset += 8;

          byte[] decompressedChunk = new byte[uncompressedChunkSize];
          qzip.decompress(
              compressed, offset, compressedChunkSize, decompressedChunk, 0, uncompressedChunkSize);
          decompressedOut.write(decompressedChunk);
        }
        decompressed = decompressedOut.toByteArray();

        // End session
        qzip.end();

        if (flag.compareAndSet(false, true)) {
          System.out.println("\n------------------------");
          System.out.printf("Compression ratio: %.2f%n", (double) src.length / compressed.length);
          System.out.println("------------------------");
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Benchmark
  public void compress(ThreadState state) throws IOException {
    QatZipper qzip =
        new QatZipper.Builder()
            .setMode(Mode.HARDWARE)
            .setAlgorithm(Algorithm.DEFLATE)
            .setLevel(level)
            .build();
    int maxCompressedSize = qzip.maxCompressedLength(chunkSize);
    byte[] compressedChunk = new byte[maxCompressedSize];
    ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
    for (int offset = 0; offset < state.src.length; offset += chunkSize) {
      int length = Math.min(chunkSize, state.src.length - offset);

      // Compress the chunk.
      int compressedSize =
          qzip.compress(state.src, offset, length, compressedChunk, 0, maxCompressedSize);

      // Write simple header for each chunk.
      compressedOut.write(intToBytes(length));
      compressedOut.write(intToBytes(compressedSize));

      // Write only valid compressed bytes.
      compressedOut.write(compressedChunk, 0, compressedSize);
    }
    state.compressed = compressedOut.toByteArray();
    qzip.end();
  }

  @Benchmark
  public static void decompress(ThreadState state) throws IOException {
    QatZipper qzip =
        new QatZipper.Builder()
            .setMode(Mode.HARDWARE)
            .setAlgorithm(Algorithm.DEFLATE)
            .setLevel(level)
            .build();
    ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
    int uncompressedChunkSize = 0;
    int compressedChunkSize = 0;
    for (int offset = 0; offset < state.compressed.length; offset += compressedChunkSize) {
      uncompressedChunkSize = bytesToInt(state.compressed, offset);
      compressedChunkSize = bytesToInt(state.compressed, offset + 4);
      offset += 8;

      byte[] decompressedChunk = new byte[uncompressedChunkSize];
      qzip.decompress(
          state.compressed,
          offset,
          compressedChunkSize,
          decompressedChunk,
          0,
          uncompressedChunkSize);

      decompressedOut.write(decompressedChunk);
    }
    state.decompressed = decompressedOut.toByteArray();
    qzip.end();
  }

  private static byte[] intToBytes(int value) {
    return ByteBuffer.allocate(4).putInt(value).array();
  }

  private static int bytesToInt(byte[] bytes, int offset) {
    return ByteBuffer.wrap(bytes, offset, 4).getInt();
  }
}
