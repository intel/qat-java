/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class implements an OutputStream filter that compresses data using Intel &reg; QuickAssist
 * Technology (QAT).
 */
public class QatCompressorOutputStream extends FilterOutputStream {
  private ByteBuffer inputBuffer;
  private QatZipper qzip;
  private ByteBuffer outputBuffer;
  private boolean closed;

  /** The default size in bytes of the output buffer. */
  public static final int DEFAULT_BUFFER_SIZE = 512;

  /**
   * Creates a new output stream with {@link DEFAULT_BUFFER_SIZE}, {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link Mode#HARDWARE}.
   *
   * @param out the output stream
   */
  public QatCompressorOutputStream(OutputStream out) {
    this(
        out,
        DEFAULT_BUFFER_SIZE,
        Algorithm.DEFLATE,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        Mode.HARDWARE);
  }

  /**
   * Creates a new output stream with {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link Mode#HARDWARE}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize) {
    this(out, bufferSize, Algorithm.DEFLATE, QatZipper.DEFAULT_COMPRESS_LEVEL, Mode.HARDWARE);
  }

  /**
   * Creates a new output stream with given parameters, {@link QatZipper#DEFAULT_COMPRESS_LEVEL},
   * and {@link Mode#HARDWARE}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize, Algorithm algorithm) {
    this(out, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, Mode.HARDWARE);
  }

  /**
   * Creates a new output stream with the given parameters and {@link Mode#HARDWARE}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, int level) {
    this(out, bufferSize, algorithm, level, Mode.HARDWARE);
  }

  /**
   * Creates a new output stream with the given parameters and {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, Mode mode) {
    this(out, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, mode);
  }

  /**
   * Creates a new output stream with the given paramters.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, int level, Mode mode) {
    super(out);
    if (bufferSize <= 0) throw new IllegalArgumentException();
    Objects.requireNonNull(out);
    qzip = new QatZipper(algorithm, level, mode);
    inputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer = ByteBuffer.allocate(qzip.maxCompressedLength(bufferSize));
    closed = false;
  }

  /**
   * Writes a byte to the compressed output stream.
   *
   * @param b the data to be written
   * @throws IOException if this stream is closed
   */
  @Override
  public void write(int b) throws IOException {
    if (closed) throw new IOException("Stream is closed");
    if (!inputBuffer.hasRemaining()) {
      flush();
    }
    inputBuffer.put((byte) b);
  }

  /**
   * Writes data from the given byte array to the compressed output stream.
   *
   * @param b the data to be written
   * @throws IOException if this stream is closed
   */
  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  /**
   * Writes data from the given byte array to the compressed output stream.
   *
   * @param b the data to be written
   * @param off the starting offset of the data
   * @param len the length of the data
   * @throws IOException if this stream is closed
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (closed) throw new IOException("Stream is closed");
    Objects.requireNonNull(b);
    if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();

    int bytesToWrite = 0;
    while (len > (bytesToWrite = inputBuffer.remaining())) {
      inputBuffer.put(b, off, bytesToWrite);
      len -= bytesToWrite;
      off += bytesToWrite;
      flush();
    }
    inputBuffer.put(b, off, len);
  }

  /**
   * Flushes all buffered data to the compressed output stream. This method will compress and write
   * all buffered data to the output stream.
   *
   * @throws IOException if this stream is closed
   */
  @Override
  public void flush() throws IOException {
    if (closed) throw new IOException("Stream is closed");
    if (inputBuffer.position() == 0) return;
    inputBuffer.flip();
    int compressedBytes = qzip.compress(inputBuffer, outputBuffer);
    out.write(outputBuffer.array(), 0, compressedBytes);
    out.flush();
    inputBuffer.clear();
    outputBuffer.clear();
  }

  /**
   * Writes any remaining data to the compressed output stream and releases resources. This method
   * will close the underlying output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Override
  public void close() throws IOException {
    if (closed) return;
    flush();
    qzip.end();
    out.close();
    inputBuffer = null;
    outputBuffer = null;
    closed = true;
  }
}
