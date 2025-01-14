/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static com.intel.qat.QatZipper.PollingMode;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * This class implements an OutputStream filter that compresses data using Intel &reg; QuickAssist
 * Technology (QAT).
 */
public class QatCompressorOutputStream extends FilterOutputStream {
  private byte[] inputBuffer;
  private int inputPosition;
  private QatZipper qzip;
  private byte[] outputBuffer;
  private int outputPosition;
  private boolean closed;

  /** The default size in bytes of the output buffer (64KB). */
  public static final int DEFAULT_BUFFER_SIZE = 1 << 16;

  /**
   * Creates a new output stream with the given paramters.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   * @param pmode the polling mode
   */
  public QatCompressorOutputStream(
      OutputStream out,
      int bufferSize,
      Algorithm algorithm,
      int level,
      Mode mode,
      PollingMode pmode) {
    super(out);
    if (bufferSize <= 0) throw new IllegalArgumentException();
    Objects.requireNonNull(out);
    qzip =
        new QatZipper.Builder()
            .setAlgorithm(algorithm)
            .setLevel(level)
            .setMode(mode)
            .setPollingMode(pmode)
            .build();
    inputBuffer = new byte[bufferSize];
    outputBuffer = new byte[qzip.maxCompressedLength(bufferSize)];
    closed = false;
  }

  /**
   * Creates a new output stream with {@link DEFAULT_BUFFER_SIZE}, {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, {@link QatZipper#DEFAULT_MODE}, and {@link
   * PollingMode#BUSY}.
   *
   * @param out the output stream
   */
  public QatCompressorOutputStream(OutputStream out) {
    this(
        out,
        DEFAULT_BUFFER_SIZE,
        Algorithm.DEFLATE,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        QatZipper.DEFAULT_MODE,
        PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, {@link QatZipper#DEFAULT_MODE}, and {@link
   * PollingMode#BUSY}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize) {
    this(
        out,
        bufferSize,
        Algorithm.DEFLATE,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        QatZipper.DEFAULT_MODE,
        PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with given parameters, {@link QatZipper#DEFAULT_COMPRESS_LEVEL},
   * {@link QatZipper#DEFAULT_MODE}, and {@link PollingMode#BUSY}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize, Algorithm algorithm) {
    this(
        out,
        bufferSize,
        algorithm,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        QatZipper.DEFAULT_MODE,
        PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with given parameters, {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link PollingMode#BUSY}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize, Mode mode) {
    this(
        out,
        bufferSize,
        Algorithm.DEFLATE,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        mode,
        PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with given parameters, {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link PollingMode#BUSY}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param pmode the polling mode
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize, PollingMode pmode) {
    this(
        out,
        bufferSize,
        Algorithm.DEFLATE,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        QatZipper.DEFAULT_MODE,
        pmode);
  }

  /**
   * Creates a new output stream with the given parameters, {@link QatZipper#DEFAULT_MODE}, and
   * {@link PollingMode#BUSY}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, int level) {
    this(out, bufferSize, algorithm, level, QatZipper.DEFAULT_MODE, PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with the given parameters, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link PollingMode#BUSY}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, Mode mode) {
    this(out, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, mode, PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with the given parameters, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link QatZipper#DEFAULT_MODE}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param pmode the polling mode
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, PollingMode pmode) {
    this(
        out,
        bufferSize,
        algorithm,
        QatZipper.DEFAULT_COMPRESS_LEVEL,
        QatZipper.DEFAULT_MODE,
        pmode);
  }

  /**
   * Creates a new output stream with the given parameters and {@link PollingMode#BUSY}.
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
    this(out, bufferSize, algorithm, level, mode, PollingMode.BUSY);
  }

  /**
   * Creates a new output stream with the given parameters and {@link QatZipper#DEFAULT_MODE}.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param pmode the polling mode
   */
  public QatCompressorOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, int level, PollingMode pmode) {
    this(out, bufferSize, algorithm, level, QatZipper.DEFAULT_MODE, pmode);
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
    if (inputPosition == inputBuffer.length) {
      flush();
    }
    inputBuffer[inputPosition++] = (byte) b;
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
    while (len > (bytesToWrite = (inputBuffer.length - inputPosition))) {
      System.arraycopy(b, off, inputBuffer, inputPosition, bytesToWrite);
      inputPosition += bytesToWrite;
      len -= bytesToWrite;
      off += bytesToWrite;
      flush();
    }
    System.arraycopy(b, off, inputBuffer, inputPosition, len);
    inputPosition += len;
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
    if (inputPosition == 0) return;
    int currentPosition = inputPosition;
    inputPosition = 0;
    int compressedBytes =
        qzip.compress(
            inputBuffer,
            inputPosition,
            currentPosition,
            outputBuffer,
            outputPosition,
            outputBuffer.length - outputPosition);
    out.write(outputBuffer, 0, compressedBytes);
    out.flush();
    outputPosition = 0;
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
