/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class JavaUtilZipStreamBench {
  private static final int BUFFER_SIZE = 1 << 16; // 64KB
  private static AtomicBoolean flag = new AtomicBoolean(false);

  @Param({""})
  static String file;

  @Param({"6"})
  static int level;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] compressed;

    public ThreadState() {
      try {
        // Read input
        src = Files.readAllBytes(Paths.get(file));

        // Compress input using streams
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(level);
        DeflaterOutputStream outputStream =
            new DeflaterOutputStream(compressedOutput, deflater, BUFFER_SIZE);
        outputStream.write(src, 0, src.length);
        outputStream.close();

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
    DeflaterOutputStream outputStream =
        new DeflaterOutputStream(compressedOutput, new Deflater(level), BUFFER_SIZE);
    outputStream.write(state.src, 0, state.src.length);
    outputStream.close();
  }

  @Benchmark
  public void decompress(ThreadState state) throws IOException {
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(state.compressed);
    InflaterInputStream inputStream =
        new InflaterInputStream(compressedInput, new Inflater(), BUFFER_SIZE);

    int bytesRead = 0;
    byte[] buffer = new byte[BUFFER_SIZE];
    while ((bytesRead = inputStream.read(buffer)) != -1)
      ;
    inputStream.close();
  }
}
