/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Decompressor;
import net.jpountz.lz4.LZ4Factory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class Lz4JavaBench {
  private static AtomicBoolean flag = new AtomicBoolean(false);

  @Param({""})
  static String file;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] dst;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {
        // Create compressor/decompressor object
        LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

        // Read input
        src = Files.readAllBytes(Paths.get(file));

        decompressed = new byte[src.length];
        dst = new byte[compressor.maxCompressedLength(src.length)];

        // Compress input
        int compressedLength = compressor.compress(src, dst);

        // Prepare compressed array of size EXACTLY compressedLength
        compressed = new byte[compressedLength];
        System.arraycopy(dst, 0, compressed, 0, compressedLength);

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
  public void compress(ThreadState state) {
    LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    compressor.compress(state.src, state.dst);
  }

  @Benchmark
  public void decompress(ThreadState state) {
    LZ4Decompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    decompressor.decompress(state.compressed, 0, state.decompressed, 0, state.decompressed.length);
  }
}
