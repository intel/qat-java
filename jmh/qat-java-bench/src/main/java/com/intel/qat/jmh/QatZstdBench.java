package com.intel.qat.jmh;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.intel.qat.QatZstdSequenceProducer;
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
public class QatZstdBench {
  private byte[] src;
  private byte[][] srcChunks;
  private int chunkCompressBound;

  @Param({""})
  static String file;

  @Param({"9"})
  static int zstdLevel;

  @Param({"16384"})
  static int chunkSize;

  protected ZstdCompressCtx newCctx() {
    ZstdCompressCtx cctx = new ZstdCompressCtx();
    cctx.setLevel(zstdLevel);
    cctx.setWorkers(0);
    QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
    cctx.registerSequenceProducer(seqprod);
    cctx.setSequenceProducerFallback(false);
    cctx.setSearchForExternalRepcodes(Zstd.ParamSwitch.DISABLE);
    cctx.setEnableLongDistanceMatching(Zstd.ParamSwitch.DISABLE);
    return cctx;
  }

  @Setup
  public void prepare() {
    try {
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
      QatZstdSequenceProducer.startDevice();
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
    } finally {
      QatZstdSequenceProducer.stopDevice();
    }
  }

  /** Thread-local state. Stores the thread-specific zstd compression context. */
  @State(Scope.Thread)
  public static class ThreadState {
    private byte[] dst;
    ZstdCompressCtx cctx;
    ZstdDecompressCtx dctx;

    @Setup
    public void prepare(QatZstdBench bench) {
      QatZstdSequenceProducer.startDevice();
      dst = new byte[bench.chunkCompressBound];
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
      threadState.cctx.compress(threadState.dst, srcChunks[i]);
  }

  @Benchmark
  public void decompress(ThreadState threadState) {
    threadState.dctx.decompress(threadState.dst, src.length);
  }

  @TearDown
  public void end() {
    QatZstdSequenceProducer.stopDevice();
  }
}
