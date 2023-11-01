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
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class JavaZipStreamBenchmark {
  private static final int COMPRESSION_LEVEL = 6;

  @Param({""})
  static String corpus;

  static final int BUFFER_SIZE = 1 << 16; // 64KB

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] dst;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {
        // Read input
        src = Files.readAllBytes(Paths.get(corpus));

        // Compress input using streams
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
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
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Benchmark
  public void compress(ThreadState state) throws IOException {
    ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
    DeflaterOutputStream outputStream =
        new DeflaterOutputStream(compressedOutput, new Deflater(COMPRESSION_LEVEL), BUFFER_SIZE);
    outputStream.write(state.src, 0, state.src.length);
    outputStream.close();
  }

  @Benchmark
  public void decompress(ThreadState state) throws IOException {
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(state.compressed);
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
