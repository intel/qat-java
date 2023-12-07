package com.intel.qat.jmh;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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
public class ZstdSoftwareBench {
  private byte[] src;
  private byte[][] srcChunks;
  private int chunkCompressBound;

  @Param({""})
  String fileName;

  @Param({"5"})
  String zstdLevel;

  @Param({"16384"})
  String chunkSize;

  protected ZstdCompressCtx newCctx() {
    ZstdCompressCtx cctx = new ZstdCompressCtx();
    cctx.setLevel(Integer.parseInt(zstdLevel));
    cctx.setWorkers(0);
    cctx.setSearchForExternalRepcodes(Zstd.ParamSwitch.DISABLE);
    return cctx;
  }

  @Setup
  public void prepare() {
    try {
      // Read input
      src = Files.readAllBytes(Paths.get(fileName));

      // Split into chunks
      int intChunkSize = Integer.parseInt(chunkSize);
      assert src.length > 0;
      assert intChunkSize > 0;
      assert intChunkSize <= (1 << 30);
      int nChunks = (src.length + (intChunkSize - 1)) / intChunkSize;
      srcChunks = new byte[nChunks][];
      for (int i = 0; i < nChunks - 1; i++)
        srcChunks[i] = Arrays.copyOfRange(src, i * intChunkSize, (i + 1) * intChunkSize);
      srcChunks[nChunks - 1] = Arrays.copyOfRange(src, (nChunks - 1) * intChunkSize, src.length);
      chunkCompressBound = (int) Zstd.compressBound(intChunkSize);

      // Compress input
      try (ZstdCompressCtx cctx = newCctx(); ) {
        int compressedLength = 0;
        byte[] dst = new byte[chunkCompressBound];
        for (int i = 0; i < srcChunks.length; i++)
          compressedLength += cctx.compress(dst, srcChunks[i]);

        // Print compressed length and ratio
        System.out.println("\n-------------------------");
        System.out.printf(
            "Input size: %d, Compressed size: %d, ratio: %.2f\n",
            src.length, compressedLength, src.length * 1.0 / compressedLength);
        System.out.println("-------------------------");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Thread-local state. Stores the thread-specific zstd compression context. */
  @State(Scope.Thread)
  public static class ThreadState {
    private byte[] dst;
    ZstdCompressCtx cctx;

    @Setup
    public void prepare(ZstdSoftwareBench bench) {
      dst = new byte[bench.chunkCompressBound];
      cctx = bench.newCctx();
    }

    @TearDown
    public void end() {
      cctx.close();
    }
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void compress(ThreadState threadState) {
    // Compress all chunks
    for (int i = 0; i < srcChunks.length; i++)
      threadState.cctx.compress(threadState.dst, srcChunks[i]);
  }

  @TearDown
  public void end() {}
}
