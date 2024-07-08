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
  static int chunkSize;

  @Param({"DEFLATE", "LZ4", "ZSTD"})
  static String algorithm;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] buf;
    byte[] src;
    byte[] compressed;
    byte[] decompressed;
    int maxChunkSize;
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
          case "ZSTD":
            algorithm = Algorithm.ZSTD;
            break;
          default:
            throw new IllegalArgumentException("Invalid algo. Supported are DEFLATE and LZ4.");
        }

        // Create compressor/decompressor object
        QatZipper qzip = new QatZipper(algorithm, level);

        // Read input
        src = Files.readAllBytes(Paths.get(file));
        int srclen = src.length;

        // Max size of a compBuf block and compressed file
        maxChunkSize = qzip.maxCompressedLength(chunkSize);

        // Temporary buffer for compressed file
        buf = new byte[((srclen + chunkSize - 1) / chunkSize) * maxChunkSize];

        int off = 0;
        int pos = 0;
        while (off < srclen) {
          int c =
              qzip.compress(src, off, Math.min(chunkSize, srclen - off), buf, pos, maxChunkSize);
          pos += c;
          off += qzip.getBytesRead();
        }

        // Prepare compressed array of size EXACTLY compLen
        int clen = pos;
        compressed = new byte[clen];
        System.arraycopy(buf, 0, compressed, 0, compressed.length);

        // Buffer for decompression
        decompressed = new byte[src.length];

        // Decompress
        off = 0;
        pos = 0;
        while (off < clen) {
          int c =
              qzip.decompress(
                  compressed,
                  off,
                  Math.min(chunkSize, clen - off),
                  decompressed,
                  pos,
                  Math.min(chunkSize, decompressed.length - pos));
          pos += c;
          off += qzip.getBytesRead();
        }

        // End session
        qzip.end();

        if (flag.compareAndSet(false, true)) {
          System.out.println("\n------------------------");
          System.out.printf("Compression ratio: %.2f%n", (double) src.length / clen);
          System.out.println("------------------------");
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Benchmark
  public void compress(ThreadState state) {
    QatZipper qzip = new QatZipper(state.algorithm, level);
    int off = 0;
    int pos = 0;
    int srclen = state.src.length;
    while (off < srclen) {
      int c =
          qzip.compress(
              state.src,
              off,
              Math.min(chunkSize, srclen - off),
              state.buf,
              pos,
              state.maxChunkSize);
      pos += c;
      off += qzip.getBytesRead();
    }
    qzip.end();
  }

  @Benchmark
  public void decompress(ThreadState state) {
    QatZipper qzip = new QatZipper(state.algorithm);
    int off = 0;
    int pos = 0;
    int clen = state.compressed.length;
    while (off < clen) {
      int c =
          qzip.decompress(
              state.compressed,
              off,
              Math.min(chunkSize, clen - off),
              state.decompressed,
              pos,
              Math.min(chunkSize, state.decompressed.length - pos));
      pos += c;
      off += qzip.getBytesRead();
    }
    qzip.end();
  }
}
