package com.intel.qat.jmh;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import java.nio.file.Files;
import java.nio.file.Paths;
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
  private static final int COMPRESSION_LEVEL = 3;

  private byte[] src;
  private byte[] dst;
  private byte[] compressed;
  private byte[] decompressed;
  ZstdCompressCtx cctx;
  ZstdDecompressCtx dctx;

  @Param({""})
  String fileName;

  // @Param({
  //   "-7", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
  //   "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22"
  // })
  // String levelStr;

  @Setup
  public void prepare() {
    // Create compressor and decompressor objects
    try {
      cctx = new ZstdCompressCtx();
      dctx = new ZstdDecompressCtx();
      // cctx.setLevel(Integer.parseInt(levelStr));
      cctx.setLevel(COMPRESSION_LEVEL);

      // Read input
      src = Files.readAllBytes(Paths.get(fileName));
      dst = new byte[(int) Zstd.compressBound(src.length)];

      // Compress input
      int compressedLength = cctx.compress(dst, src);

      // Prepare compressed array of size EXACTLY compressedLength
      compressed = new byte[compressedLength];
      System.arraycopy(dst, 0, compressed, 0, compressedLength);

      // Do decompression
      decompressed = new byte[src.length];
      int decompressedLength = dctx.decompress(decompressed, compressed);
      assert decompressedLength == src.length;

      // Print compressed length and ratio
      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressedLength, src.length * 1.0 / compressedLength);
      System.out.println("-------------------------");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  @Warmup(iterations = 3)
  @Measurement(iterations = 4)
  @BenchmarkMode(Mode.Throughput)
  public void compress() {
    cctx.compress(dst, src);
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void decompress() throws java.util.zip.DataFormatException {
    dctx.decompress(decompressed, compressed);
  }

  @TearDown
  public void end() {
    cctx.close();
    dctx.close();
  }
}
