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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class JavaZipBenchmark {

  private static final int COMPRESSION_LEVEL = 6;

  @Param({""})
  static String corpus;

  @State(Scope.Thread)
  public static class ThreadState {
    byte[] src;
    byte[] dst;
    byte[] compressed;
    byte[] decompressed;

    public ThreadState() {
      try {
        // Create compressor and decompressor objects
        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        Inflater inflater = new Inflater();

        // Read input
        src = Files.readAllBytes(Paths.get(corpus));
        dst = new byte[src.length];

        // Compress input
        deflater.setInput(src);
        int compressedLength = deflater.deflate(dst);
        deflater.end();

        // Prepare compressed array of size EXACTLY compressedLength
        compressed = new byte[compressedLength];
        System.arraycopy(dst, 0, compressed, 0, compressedLength);

        // Do decompression
        decompressed = new byte[src.length];
        inflater.setInput(compressed);
        int decompressedLength = inflater.inflate(decompressed);
        inflater.end();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Benchmark
  public void compress(ThreadState state) {
    Deflater deflater = new Deflater(COMPRESSION_LEVEL);
    deflater.setInput(state.src);
    deflater.deflate(state.dst);
    deflater.end();
  }

  @Benchmark
  public void decompress(ThreadState state) throws java.util.zip.DataFormatException {
    Inflater inflater = new Inflater();
    inflater.setInput(state.compressed);
    inflater.inflate(state.decompressed);
    inflater.end();
  }
}
