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
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ZstdJniBench {
  private static AtomicBoolean flag = new AtomicBoolean(false);

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {
        // Read input
        src = Files.readAllBytes(Paths.get(file));

        // Compress input
        compressed = Zstd.compress(src, level);

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
    state.compressed = Zstd.compress(state.src, level);
  }

  @Benchmark
  public void decompress(ThreadState state) {
    state.decompressed = Zstd.decompress(state.compressed, state.src.length);
  }
}
