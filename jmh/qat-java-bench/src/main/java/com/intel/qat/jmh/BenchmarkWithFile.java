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

import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.Runner;
import com.intel.qat.QatZipper;
import com.intel.qat.QatZipper.Codec;
import java.util.zip.Deflater;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BenchmarkWithFile {

  private static String fileName = "samples/dickens";

  @State(Scope.Thread)
  public static class QatCompressor {
    private QatZipper zipper;
    private byte[] src;
    private byte[] dst;

    @Setup(Level.Trial)
    public void setup() {
      zipper = new QatZipper(Codec.DEFLATE, 6, QatZipper.Mode.HARDWARE, 64 * 1024);
      try {
        src = Files.readAllBytes(Paths.get(fileName));
        dst = new byte[zipper.maxCompressedLength(src.length)];
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      zipper.end();
    }

    @Benchmark
    @Fork(warmups = 1, value = 2)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void compress() {
      zipper.compress(src, 0, src.length, dst, 0, dst.length);
    }
  }

  @State(Scope.Thread)
  public static class JavaZipDeflater {
    private Deflater deflater;
    private byte[] src;
    private byte[] dst;

    @Setup(Level.Trial)
    public void setup() {
      deflater = new Deflater(6);
      try {
        src = Files.readAllBytes(Paths.get(fileName));
        dst = new byte[2 * src.length];
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      deflater.end();
    }

    @Benchmark
    @Fork(warmups = 1, value = 2)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void compress() {
      deflater.setInput(src);
      deflater.deflate(dst);
      deflater.reset();
    }
  }

  public void compress(QatCompressor qatZip) {
    qatZip.compress();
  }

  public void compress(JavaZipDeflater deflate) {
    deflate.compress();
  }
}
