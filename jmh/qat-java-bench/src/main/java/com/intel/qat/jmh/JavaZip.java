/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class JavaZip {
  private byte[] src;
  private byte[] dst;
  private byte[] compressed;
  private byte[] decompressed;

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @Setup
  public void setup() {
    try {
      // Create compressor and decompressor objects
      Deflater deflater = new Deflater(level);
      Inflater inflater = new Inflater();

      // Read input
      src = Files.readAllBytes(Paths.get(file));
      dst = new byte[src.length];

      // Compress input
      deflater.setInput(src);
      int compressedLength = deflater.deflate(dst);
      deflater.end();

      // Prepare compressed array of size EXACTLY compressedLength
      compressed = new byte[compressedLength];
      System.arraycopy(dst, 0, compressed, 0, compressedLength);

      // Do decompression
      decompressed = new byte[src.length];
      inflater.setInput(compressed);
      inflater.inflate(decompressed);
      inflater.end();

      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressedLength, src.length * 1.0 / compressedLength);
      System.out.println("-------------------------");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  public void compress() {
    Deflater deflater = new Deflater(level);
    deflater.setInput(src);
    deflater.deflate(dst);
    deflater.end();
  }

  @Benchmark
  public void decompress() throws java.util.zip.DataFormatException {
    Inflater inflater = new Inflater();
    inflater.setInput(compressed);
    inflater.inflate(decompressed);
    inflater.end();
  }
}
