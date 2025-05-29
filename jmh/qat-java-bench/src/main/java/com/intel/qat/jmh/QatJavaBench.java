/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import com.intel.qat.QatZipper;
import com.intel.qat.QatZipper.Algorithm;
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
public class QatJavaBench {
  private static AtomicBoolean flag = new AtomicBoolean(false);

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @Param({"65536"})
  static int blockSize;

  @Param({"DEFLATE", "LZ4"})
  static String algorithm;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] compressed;
    byte[] decompressed;
    Algorithm algorithm;

    public ThreadState() {
      try {

        switch (QatJavaBench.algorithm) {
          case "DEFLATE":
            algorithm = Algorithm.DEFLATE;
            break;
          case "LZ4":
            algorithm = Algorithm.LZ4;
            break;
          default:
            throw new IllegalArgumentException("Invalid algorithm. Supported are DEFLATE and LZ4.");
        }

        QatZipper qzip = new QatZipper.Builder().setAlgorithm(algorithm).setLevel(level).build();

        // Read input
        src = Files.readAllBytes(Paths.get(file));

        int maxCompressedSize = qzip.maxCompressedLength(blockSize);
        byte[] compressedBlock = new byte[maxCompressedSize];
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        for (int offset = 0; offset < src.length; offset += blockSize) {
          int length = Math.min(blockSize, src.length - offset);

          int compressedSize =
              qzip.compress(src, offset, length, compressedBlock, 0, maxCompressedSize);

          // Write the size of the compressed block and then the block itself.
          compressedOut.write(intToBytes(compressedSize));
          compressedOut.write(compressedBlock, 0, compressedSize);
        }
        compressed = compressedOut.toByteArray();

        ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
        int compressedBlockSize = 0;
        for (int offset = 0; offset < compressed.length; offset += compressedBlockSize) {
          compressedBlockSize = bytesToInt(compressed, offset);
          offset += 4;

          byte[] decompressedBlock = new byte[blockSize];
          qzip.decompress(compressed, offset, compressedBlockSize, decompressedBlock, 0, blockSize);
          decompressedOut.write(decompressedBlock);
        }
        decompressed = decompressedOut.toByteArray();

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
    QatZipper qzip = new QatZipper.Builder().setAlgorithm(state.algorithm).setLevel(level).build();
    int maxCompressedSize = qzip.maxCompressedLength(blockSize);
    byte[] compressedBlock = new byte[maxCompressedSize];
    ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
    for (int offset = 0; offset < state.src.length; offset += blockSize) {
      int length = Math.min(blockSize, state.src.length - offset);

      int compressedSize =
          qzip.compress(state.src, offset, length, compressedBlock, 0, maxCompressedSize);

      // Write the size of the compressed block and then the block itself.
      compressedOut.write(intToBytes(compressedSize));
      compressedOut.write(compressedBlock, 0, compressedSize);
    }
    state.compressed = compressedOut.toByteArray();
    qzip.end();
  }

  @Benchmark
  public static void decompress(ThreadState state) throws IOException {
    QatZipper qzip = new QatZipper.Builder().setAlgorithm(state.algorithm).setLevel(level).build();
    ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
    int compressedBlockSize = 0;
    for (int offset = 0; offset < state.compressed.length; offset += compressedBlockSize) {
      compressedBlockSize = bytesToInt(state.compressed, offset);
      offset += 4;

      byte[] decompressedBlock = new byte[blockSize];
      qzip.decompress(
          state.compressed, offset, compressedBlockSize, decompressedBlock, 0, blockSize);

      decompressedOut.write(decompressedBlock);
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
