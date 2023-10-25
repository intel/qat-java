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

import static com.intel.qat.QatZipper.Algorithm;

import com.intel.qat.QatCompressorOutputStream;
import com.intel.qat.QatDecompressorInputStream;
import com.intel.qat.QatZipper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class QatJavaStreamBench {
  private static final int COMPRESSION_LEVEL = 6;

  private byte[] src;
  private byte[] compressed;

  @Param({""})
  String fileName;

  @Param({"512", "4096"})
  private int bufferSize;

  @Setup
  public void prepare() {
    try {
      // Read input
      src = Files.readAllBytes(Paths.get(fileName));

      // Compress input using streams
      ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
      QatCompressorOutputStream qatOutputStream =
          new QatCompressorOutputStream(
              compressedOutput, bufferSize, Algorithm.DEFLATE, QatZipper.Mode.HARDWARE);
      qatOutputStream.write(src);
      qatOutputStream.close();

      // Get compressed data from stream
      compressed = compressedOutput.toByteArray();

      // Decompress compressed data
      ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressed);
      QatDecompressorInputStream qatInputStream =
          new QatDecompressorInputStream(
              compressedInput, bufferSize, Algorithm.DEFLATE, QatZipper.Mode.HARDWARE);
      ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();

      byte[] buffer = new byte[bufferSize];
      int bytesRead;
      while ((bytesRead = qatInputStream.read(buffer)) != -1) {
        decompressedOutput.write(buffer, 0, bytesRead);
      }
      qatInputStream.close();

      // Print compressed length and ratio
      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressed.length, src.length * 1.0 / compressed.length);
      System.out.println("-------------------------");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void compress() throws IOException {
    ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
    QatCompressorOutputStream qatOutputStream =
        new QatCompressorOutputStream(
            compressedOutput, bufferSize, Algorithm.DEFLATE, QatZipper.Mode.HARDWARE);
    qatOutputStream.write(src);
    qatOutputStream.close();
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void decompress() throws IOException {
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressed);
    QatDecompressorInputStream qatInputStream =
        new QatDecompressorInputStream(
            compressedInput, bufferSize, Algorithm.DEFLATE, QatZipper.Mode.HARDWARE);
    ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();

    byte[] buffer = new byte[bufferSize];
    int bytesRead;
    while ((bytesRead = qatInputStream.read(buffer)) != -1) {
      decompressedOutput.write(buffer, 0, bytesRead);
    }
    qatInputStream.close();
  }

  @TearDown
  public void end() {
    // Do nothing
  }
}
