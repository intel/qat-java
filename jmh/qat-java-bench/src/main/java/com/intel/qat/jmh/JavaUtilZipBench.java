/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
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

  @Param({"65536"})
  static int chunkSize;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] buf;
    byte[] src;
    byte[] compressed;
    byte[] decompressed;
    int maxChunkSize;

    public ThreadState() {
      try {
        // Read input
        src = Files.readAllBytes(Paths.get(file));
        int srclen = src.length;

        // Max chunk size is 25% more, just in case.
        maxChunkSize = (int) (chunkSize * 1.25);

        // Temporary buffer for compressed file
        buf = new byte[((srclen + chunkSize - 1) / chunkSize) * maxChunkSize];

        // Compress
        Deflater deflater = new Deflater(level);
        int off = 0;
        int pos = 0;
        while (off < srclen) {
          int csize = Math.min(chunkSize, srclen - off);
          deflater.setInput(src, off, csize);
          deflater.finish();
          int c = deflater.deflate(buf, pos, csize);
          pos += c;
          off += deflater.getBytesRead();
          deflater.reset();
        }
        deflater.end();

        // Prepare compressed array of size EXACTLY compLen
        int clen = pos;
        compressed = new byte[clen];
        System.arraycopy(buf, 0, compressed, 0, compressed.length);

        // Buffer for decompression
        decompressed = new byte[src.length];

        // Decompress
        Inflater inflater = new Inflater();
        off = 0;
        pos = 0;
        while (off < clen) {
          inflater.setInput(compressed, off, Math.min(chunkSize, clen - off));
          int c =
              inflater.inflate(decompressed, pos, Math.min(chunkSize, decompressed.length - pos));
          pos += c;
          off += inflater.getBytesRead();
          inflater.reset();
        }
        inflater.end();

        if (flag.compareAndSet(false, true)) {
          System.out.println("\n------------------------");
          System.out.printf("Compression ratio: %.2f%n", (double) src.length / clen);
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
    int off = 0;
    int pos = 0;
    int srclen = state.src.length;
    while (off < srclen) {
      int csize = Math.min(chunkSize, srclen - off);
      deflater.setInput(state.src, off, csize);
      deflater.finish();
      int c = deflater.deflate(state.buf, pos, csize);
      pos += c;
      off += deflater.getBytesRead();
      deflater.reset();
    }
    deflater.end();
  }

  @Benchmark
  public void decompress(ThreadState state) throws DataFormatException {
    Inflater inflater = new Inflater();
    int off = 0;
    int pos = 0;
    int clen = state.compressed.length;
    while (off < clen) {
      inflater.setInput(state.compressed, off, Math.min(chunkSize, clen - off));
      int c =
          inflater.inflate(
              state.decompressed, pos, Math.min(chunkSize, state.decompressed.length - pos));
      pos += c;
      off += inflater.getBytesRead();
      inflater.reset();
    }
    inflater.end();
  }
}
