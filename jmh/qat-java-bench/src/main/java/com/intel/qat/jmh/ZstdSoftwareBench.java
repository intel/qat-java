package com.intel.qat.jmh;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import java.io.IOException;
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
  private int[] compressedLengths;
  private byte[][] compressedChunks;
  private int chunkCompressBound;

  @Param({""})
  static String file;

  @Param({"5"})
  static int zstdLevel;

  @Param({"16384"})
  static int chunkSize;

  protected ZstdCompressCtx newCctx() {
    ZstdCompressCtx cctx = new ZstdCompressCtx();
    cctx.setLevel(zstdLevel);
    cctx.setWorkers(0);
    cctx.setSearchForExternalRepcodes(Zstd.ParamSwitch.DISABLE);
    return cctx;
  }

  @Setup
  public void prepare() throws IOException {
    // Read input
    src = Files.readAllBytes(Paths.get(file));

    // Split into chunks
    assert src.length > 0;
    assert chunkSize > 0;
    assert chunkSize <= (1 << 30);
    int nChunks = (src.length + (chunkSize - 1)) / chunkSize;
    srcChunks = new byte[nChunks][];
    for (int i = 0; i < nChunks - 1; i++)
      srcChunks[i] = Arrays.copyOfRange(src, i * chunkSize, (i + 1) * chunkSize);
    srcChunks[nChunks - 1] = Arrays.copyOfRange(src, (nChunks - 1) * chunkSize, src.length);
    chunkCompressBound = (int) Zstd.compressBound(chunkSize);

    // Compress input
    try (ZstdCompressCtx cctx = newCctx(); ) {
      int compressedLength = 0;
      compressedLengths = new int[srcChunks.length];
      compressedChunks = new byte[srcChunks.length][chunkCompressBound];
      for (int i = 0; i < srcChunks.length; i++) {
        compressedLengths[i] = cctx.compress(compressedChunks[i], srcChunks[i]);
        compressedLength += compressedLengths[i];
      }

      // Print compressed length and ratio
      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressedLength, src.length * 1.0 / compressedLength);
      System.out.println("-------------------------");
    }
  }

  /** Thread-local state. Stores the thread-specific zstd compression context. */
  @State(Scope.Thread)
  public static class ThreadState {
    private byte[] cdst;
    private byte[] ddst;
    ZstdCompressCtx cctx;
    ZstdDecompressCtx dctx;

    @Setup
    public void prepare(ZstdSoftwareBench bench) {
      cdst = new byte[bench.chunkCompressBound];
      ddst = new byte[bench.src.length];
      cctx = bench.newCctx();
      dctx = new ZstdDecompressCtx();
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
      threadState.cctx.compress(threadState.cdst, srcChunks[i]);
  }

  @Benchmark
  public void decompress(ThreadState threadState) {
    // Decompress all chunks
    int pos = 0;
    for (int i = 0; i < srcChunks.length; i++) {
      threadState.dctx.decompressByteArray(
          threadState.ddst, pos, srcChunks[i].length, compressedChunks[i], 0, compressedLengths[i]);
      pos += srcChunks[i].length;
    }
  }

  @TearDown
  public void end() {}
}
