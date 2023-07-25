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
import java.util.zip.Inflater;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.FileOutputStream;
import java.io.IOException;

public class BenchmarkWithFile {

  private static final int SOURCE_FILE_SIZE = 10192446;

  private static void writeToFile(byte[] bytes, int len, String fileName) throws IOException {
    byte[] tba = new byte[len];
    for (int i = 0; i < len; i++)
      tba[i] = bytes[i];

    try (FileOutputStream fos = new FileOutputStream(fileName)) {
      fos.write(tba);
    }        
  }

  @State(Scope.Thread)
  public static class QatCompressor {
    private QatZipper zipper;
    private byte[] src;
    private byte[] dst;

    @Param({"65536"})
    private int pinMemSize;

    @Param({""})
    private String fileName;

    @Setup(Level.Trial)
    public void setup() {
      System.out.println(pinMemSize);
      zipper = new QatZipper(Codec.DEFLATE, 6, QatZipper.Mode.HARDWARE, pinMemSize);
      try {
        src = Files.readAllBytes(Paths.get(fileName));
        dst = new byte[zipper.maxCompressedLength(src.length)];

        // compress and write to file one time
        int r = zipper.compress(src, dst);
        writeToFile(dst, r, fileName + ".qat.gz");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      zipper.end();
    }

    @Benchmark
    @Fork(warmups = 1, value = 1)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void compress() {
      zipper.compress(src, dst);
    }
  }

  @State(Scope.Thread)
  public static class QatDecompressor {
    private QatZipper zipper;
    private byte[] src;
    private byte[] dst;

    @Param({"65536"})
    private int pinMemSize;

    @Param({""})
    private String fileName;

    @Param({"0"})
    private int srcFileSize;

    @Setup(Level.Trial)
    public void setup() {
      System.out.println(pinMemSize);
      zipper = new QatZipper(Codec.DEFLATE, 6, QatZipper.Mode.HARDWARE, pinMemSize);

      try {
        src = Files.readAllBytes(Paths.get(fileName));
        dst = new byte[srcFileSize];

        // decompress and write to file to verify correctness
        int r = zipper.decompress(src, dst);
        writeToFile(dst, r, fileName + ".qat.decompressed");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      zipper.end();
    }

    @Benchmark
    @Fork(warmups = 1, value = 1)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void decompress() {
      zipper.decompress(src, dst);
    }
  }

  @State(Scope.Thread)
  public static class JavaZipDeflater {
    private Deflater deflater;
    private byte[] src;
    private byte[] dst;

    @Param({""})
    private String fileName;

    @Setup(Level.Trial)
    public void setup() {
      deflater = new Deflater(6);
      try {
        src = Files.readAllBytes(Paths.get(fileName));
        dst = new byte[2 * src.length];

        // compress and write to file one time
        deflater.setInput(src);
        int r = deflater.deflate(dst);
        deflater.reset();
        writeToFile(dst, r, fileName + ".javazip.gz");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      deflater.end();
    }

    @Benchmark
    @Fork(warmups = 1, value = 1)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void compress() {
      deflater.setInput(src);
      deflater.deflate(dst);
      deflater.reset();
    }
  }

  @State(Scope.Thread)
  public static class JavaZipInflater {
    private Inflater inflater;
    private byte[] src;
    private byte[] dst;

    @Param({""})
    private String fileName;
    
    @Param({"0"})
    private int srcFileSize;

    @Setup(Level.Trial)
    public void setup() {
      inflater = new Inflater();
      try {
        src = Files.readAllBytes(Paths.get(fileName));
        dst = new byte[srcFileSize];

        // inflate to file to verify correctness
        inflater.setInput(src);
        int r = inflater.inflate(dst);
        inflater.reset();
        writeToFile(dst, r, fileName + ".javazip.decompressed");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      inflater.end();
    }

    @Benchmark
    @Fork(warmups = 1, value = 1)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void decompress() {
      try {
        inflater.setInput(src);
        inflater.inflate(dst);
        inflater.reset();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  public static void main(String... args) throws RunnerException {
    Options opts = new OptionsBuilder()
                       .include(".*")
                       .jvmArgs("-Xms1g", "-Xmx1g")
                       .build();
    new Runner(opts).run();
  }
}
