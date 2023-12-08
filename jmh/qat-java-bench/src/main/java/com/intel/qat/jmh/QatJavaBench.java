/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import com.intel.qat.QatZipper;
import com.intel.qat.QatZipper.Algorithm;
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

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] dst;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {
        // Create compressor/decompressor object
        QatZipper qzip = new QatZipper(Algorithm.DEFLATE, level);

        // Read input
        src = Files.readAllBytes(Paths.get(file));

        decompressed = new byte[src.length];
        dst = new byte[qzip.maxCompressedLength(src.length)];

        // Compress input
        int compressedLength = qzip.compress(src, dst);

        // Prepare compressed array of size EXACTLY compressedLength
        compressed = new byte[compressedLength];
        System.arraycopy(dst, 0, compressed, 0, compressedLength);

        // Do decompression
        decompressed = new byte[src.length];
        int decompressedLength = qzip.decompress(compressed, decompressed);
        assert decompressedLength == src.length;

        // Print compressed length and ratio
        System.out.println("\n-------------------------");
        System.out.printf(
            "Input size: %d, Compressed size: %d, ratio: %.2f\n",
            src.length, compressedLength, src.length * 1.0 / compressedLength);
        System.out.println("-------------------------");

        // Close QatZipper
        qzip.end();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @Benchmark
    public void compressWithDeflate(ThreadState state) {
      QatZipper qzip = new QatZipper(Algorithm.DEFLATE, level);
      qzip.compress(state.src, state.dst);
      qzip.end();
    }

    @Benchmark
    public void decompressWithDeflate(ThreadState state) {
      QatZipper qzip = new QatZipper(Algorithm.DEFLATE, level);
      qzip.decompress(state.compressed, state.decompressed);
      qzip.end();
    }
  }
}
