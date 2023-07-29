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

import com.intel.qat.QatZipper;
import com.intel.qat.QatZipper.Codec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class QatJavaBench {
  QatZipper zipper;
  byte[] src;
  byte[] dst;
  byte[] compressed;
  byte[] decompressed;
  int compressedLength;
  int decompressedLength;

  @Param({""}) String fileName;

  @Setup
  public void prepare() {
    zipper = new QatZipper(Codec.DEFLATE, 6, QatZipper.Mode.HARDWARE);
    try {
      src = Files.readAllBytes(Paths.get(fileName));
      dst = new byte[zipper.maxCompressedLength(src.length)];

      // Compress bytes
      compressedLength = zipper.compress(src, dst);

      // Prepare compressed array of size compressedLength
      // important! JNI behavior is dependent of size of array
      compressed = new byte[compressedLength];
      System.arraycopy(dst, 0, compressed, 0, compressedLength);

      decompressed = new byte[src.length];
      decompressedLength = zipper.decompress(compressed, decompressed);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void compress() {
    zipper.compress(src, dst);
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void decompress() {
    zipper.decompress(compressed, decompressed);
  }

  @TearDown
  public void end() {
    zipper.end();
  }

  public static void main(String[] args) throws RunnerException {
    if (args.length < 2)
      throw new IllegalArgumentException("Input file required.");

    Options opts = new OptionsBuilder()
                       .include(QatJavaBench.class.getSimpleName())
                       .include(JavaZipBench.class.getSimpleName())
                       .forks(1)
                       .param("fileName", args[1])
                       .jvmArgs("-Xms4g", "-Xmx4g")
                       .build();

    Collection<RunResult> results = new Runner(opts).run();
    System.out.println("-------------------------");

    long fileSize = new File(args[1]).length();
    for (RunResult rr : results) {
      BenchmarkResult ar = rr.getAggregatedResult();
      Result r = ar.getPrimaryResult();
      double speed = r.getScore() * fileSize / (1024 * 1024);
      System.out.printf(
          "%s\t%.2f MB/sec\n", rr.getParams().getBenchmark(), speed);
    }
  }
}
