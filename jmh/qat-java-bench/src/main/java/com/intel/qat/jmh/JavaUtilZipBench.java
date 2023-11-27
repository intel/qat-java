/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class JavaUtilZipBench {
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
        // Create compressor and decompressor objects
        Deflater deflater = new Deflater(level);
        Inflater inflater = new Inflater();

        // Read input
        src = Files.readAllBytes(Paths.get(file));

        decompressed = new byte[src.length];
        dst = new byte[src.length];

        // Compress input
        deflater.setInput(src);
        int compressedLength = deflater.deflate(dst);
        deflater.end();

        // Prepare compressed array of size EXACTLY compressedLength
        compressed = new byte[compressedLength];
        System.arraycopy(dst, 0, compressed, 0, compressedLength);

        if (flag.compareAndSet(false, true)) {
          System.out.println("\n------------------------");
          System.out.printf("Compression ratio: %.2f%n", (double) src.length / compressed.length);
          System.out.println("------------------------");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Benchmark
  public void compress(ThreadState state) {
    Deflater deflater = new Deflater(level);
    deflater.setInput(state.src);
    deflater.deflate(state.dst);
    deflater.end();
  }

  @Benchmark
  public void decompress(ThreadState state) throws java.util.zip.DataFormatException {
    Inflater inflater = new Inflater();
    inflater.setInput(state.compressed);
    inflater.inflate(state.decompressed);
    inflater.end();
  }
}
