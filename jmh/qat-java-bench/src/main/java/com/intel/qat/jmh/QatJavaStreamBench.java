/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Builder;

import com.intel.qat.QatCompressorOutputStream;
import com.intel.qat.QatDecompressorInputStream;
import com.intel.qat.QatZipper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class QatJavaStreamBench {
  private static final int BUFFER_SIZE = 1 << 16; // 64KB
  private static AtomicBoolean flag = new AtomicBoolean(false);

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @Param({"DEFLATE", "LZ4", "ZSTD"})
  static String algorithm;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] compressed;
    Algorithm algorithm;

    public ThreadState() {
      try {
        switch (QatJavaStreamBench.algorithm.toUpperCase()) {
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
            throw new IllegalArgumentException("Invalid algorithm. Supported are DEFLATE and LZ4.");
        }

        // Read input
        src = Files.readAllBytes(Paths.get(file));

        // Compress input using streams
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
        Builder builder = new QatZipper.Builder().setAlgorithm(algorithm).setLevel(level);
        QatCompressorOutputStream qatOutputStream =
            new QatCompressorOutputStream(compressedOutput, BUFFER_SIZE, builder);
        qatOutputStream.write(src);
        qatOutputStream.close();

        // Get compressed data from stream
        compressed = compressedOutput.toByteArray();

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
    ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
    Builder builder = new Builder().setAlgorithm(state.algorithm).setLevel(level);
    QatCompressorOutputStream qatOutputStream =
        new QatCompressorOutputStream(compressedOutput, BUFFER_SIZE, builder);
    qatOutputStream.write(state.src);
    qatOutputStream.close();
  }

  @Benchmark
  public void decompress(ThreadState state) throws IOException {
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(state.compressed);
    Builder builder = new Builder().setAlgorithm(state.algorithm).setLevel(level);
    QatDecompressorInputStream qatInputStream =
        new QatDecompressorInputStream(compressedInput, BUFFER_SIZE, builder);

    int bytesRead = 0;
    byte[] buffer = new byte[BUFFER_SIZE];
    while ((bytesRead = qatInputStream.read(buffer)) != -1)
      ;
    qatInputStream.close();
  }
}
