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
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
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
public class JavaZipBench {
  Deflater deflater;
  Inflater inflater;
  byte[] src;
  byte[] dst;
  byte[] compressed;
  byte[] decompressed;
  int compressedLength;
  int decompressedLength;

  @Param({""}) String fileName;

  @Setup
  public void prepare() {
    deflater = new Deflater(6);
    inflater = new Inflater();
    try {
      src = Files.readAllBytes(Paths.get(fileName));
      dst = new byte[2 * src.length];

      // Compress bytes
      deflater.setInput(src);
      compressedLength = deflater.deflate(dst);
      deflater.reset();

      // Prepare compressed array of size compressedLength
      // important! JNI behavior is dependent of size of array
      compressed = new byte[compressedLength];
      System.arraycopy(dst, 0, compressed, 0, compressedLength);

      decompressed = new byte[src.length];
      inflater.setInput(compressed);
      decompressedLength = inflater.inflate(decompressed);
      inflater.reset();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void compress() {
    deflater.setInput(src);
    deflater.deflate(dst);
    deflater.reset();
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void decompress() throws java.util.zip.DataFormatException {
    inflater.setInput(compressed);
    inflater.inflate(decompressed);
    inflater.reset();
  }

  @TearDown
  public void end() {
    deflater.end();
    inflater.end();
  }
}
