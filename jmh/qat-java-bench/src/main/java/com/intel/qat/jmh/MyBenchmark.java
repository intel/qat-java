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
import com.intel.qat.QatZip;
import java.util.Random;
import java.util.zip.Deflater;

public class MyBenchmark {

  @State(Scope.Thread)
  public static class QatCompressor {

    private static int ASCII_LOW = 33;
    private static int ASCII_HIGH = 126;
    
    /*
    @Param({"0", "4096", "65536", "491520"})
    private static int pinMemSize;
    */

    @Param({"128", "1024", "4096", "65536", "134072", "262144", "524288", "1048576"})
    private static int srcLen;

    private QatZip qatSession;

    private byte[] src;
    private byte[] dst;

    @Setup(Level.Trial)
    public void setup() {
      qatSession = new QatZip();

      src = new byte[srcLen];
      dst = new byte[qatSession.maxCompressedLength(srcLen)];
      
      Random r = new Random(123456789L);
      for (int i = 0; i < srcLen; i++)
        src[i] = (byte) (r.nextInt(ASCII_HIGH - ASCII_LOW + 1) + ASCII_LOW);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      qatSession.endSession();
    }

    @Benchmark
    @Fork(warmups = 1, value = 2)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    @BenchmarkMode(Mode.Throughput)  
    public void compress() {
      qatSession.compress(src, 0, src.length, dst, 0, dst.length);
    }
  }

  @State(Scope.Thread)
  public static class JavaZipDeflater {

    private static int ASCII_LOW = 33;
    private static int ASCII_HIGH = 126;
    
    @Param({"128", "1024", "4096", "65536", "134072", "262144", "524288", "1048576"})
    private static int srcLen;
    
    private Deflater deflater;

    private byte[] src;
    private byte[] dst;

    @Setup(Level.Trial)
    public void setup() {
      src = new byte[srcLen];
      dst = new byte[2 * srcLen];
      
      Random r = new Random(123456789L);
      for (int i = 0; i < srcLen; i++)
        src[i] = (byte) (r.nextInt(ASCII_HIGH - ASCII_LOW + 1) + ASCII_LOW);

      deflater = new Deflater();
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
