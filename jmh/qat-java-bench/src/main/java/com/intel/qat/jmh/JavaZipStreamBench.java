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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class JavaZipStreamBench {
  private static final int BUFFER_SIZE = 1 << 16; // 64KB

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

      // Decompress compressed data
      ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressed);
      InflaterInputStream inputStream =
          new InflaterInputStream(compressedInput, new Inflater(), BUFFER_SIZE);
      ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();

      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        decompressedOutput.write(buffer, 0, bytesRead);
      }
      inputStream.close();

      // Print compression ratio
      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressed.length, src.length * 1.0 / compressed.length);
      System.out.println("-------------------------");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  public void compress() throws IOException {
    ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
    DeflaterOutputStream outputStream =
        new DeflaterOutputStream(compressedOutput, new Deflater(level), BUFFER_SIZE);
    outputStream.write(src, 0, src.length);
    outputStream.close();
  }

  @Benchmark
  public void decompress() throws IOException {
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressed);
    InflaterInputStream inputStream =
        new InflaterInputStream(compressedInput, new Inflater(), BUFFER_SIZE);
    ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();

    byte[] buffer = new byte[BUFFER_SIZE];
    int bytesRead;
    while ((bytesRead = inputStream.read(buffer)) != -1) {
      decompressedOutput.write(buffer, 0, bytesRead);
    }
    inputStream.close();
  }
}
