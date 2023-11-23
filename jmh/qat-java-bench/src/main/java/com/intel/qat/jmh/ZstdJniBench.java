/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

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
    byte[] dst;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {
        // Read input
        src = Files.readAllBytes(Paths.get(file));
        dst = new byte[src.length];

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
