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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class QatJavaBenchmark {
  private byte[] src;
  private byte[] dst;
  private byte[] compressed;
  private byte[] decompressed;

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @Setup
  public void prepare() {
    try {
      // Create compressor/decompressor object
      QatZipper qzip = new QatZipper(Algorithm.DEFLATE, level);

      // Read input
      src = Files.readAllBytes(Paths.get(file));
      dst = new byte[qzip.maxCompressedLength(src.length)];

      // Compress input
      int compressedLength = qzip.compress(src, dst);

      // Prepare compressed array of size EXACTLY compressedLength
      compressed = new byte[compressedLength];
      System.arraycopy(dst, 0, compressed, 0, compressedLength);

      // Do decompression
      decompressed = new byte[src.length];
      int decompressedLength = qzip.decompress(compressed, decompressed);

      qzip.end();

      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressedLength, src.length * 1.0 / compressedLength);
      System.out.println("-------------------------");

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  public void compress() {
    QatZipper qzip = new QatZipper(Algorithm.DEFLATE, level);
    qzip.compress(src, dst);
    qzip.end();
  }

  @Benchmark
  public void decompress() {
    QatZipper qzip = new QatZipper(Algorithm.DEFLATE, level);
    qzip.decompress(compressed, decompressed);
    qzip.end();
  }
}
