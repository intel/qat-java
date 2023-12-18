/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;
import static com.intel.qat.QatZipper.PollingMode;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class implements an InputStream filter that decompresses data using Intel &reg; QuickAssist
 * Technology (QAT).
 */
public class QatDecompressorInputStream extends FilterInputStream {
  private ByteBuffer inputBuffer;
  private ByteBuffer outputBuffer;
  private QatZipper qzip;
  private boolean closed;
  private boolean eof;

  /** The default size in bytes of the input buffer (64KB). */
  public static final int DEFAULT_BUFFER_SIZE = 1 << 16;

  /**
   * Creates a new input stream with {@link DEFAULT_BUFFER_SIZE}, {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_MODE}, and {@link PollingMode#BUSY}.
   *
   * @param in the input stream
   */
  public QatDecompressorInputStream(InputStream in) {
    this(in, DEFAULT_BUFFER_SIZE, Algorithm.DEFLATE, QatZipper.DEFAULT_MODE, PollingMode.BUSY);
  }

  /**
   * Creates a new input stream with {@link Algorithm#DEFLATE}, {@link QatZipper#DEFAULT_MODE}, and
   * {@link PollingMode#BUSY}.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   */
  public QatDecompressorInputStream(InputStream in, int bufferSize) {
    this(in, bufferSize, Algorithm.DEFLATE, QatZipper.DEFAULT_MODE, PollingMode.BUSY);
  }

  /**
   * Creates a new input stream with the given parameters, {@link QatZipper#DEFAULT_MODE}, and
   * {@link PollingMode#BUSY}.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   */
  public QatDecompressorInputStream(InputStream in, int bufferSize, Algorithm algorithm) {
    this(in, bufferSize, algorithm, QatZipper.DEFAULT_MODE, PollingMode.BUSY);
  }

  /**
   * Creates a new input stream with the given mode, {@link Algorithm#DEFLATE}, and {@link
   * PollingMode#BUSY}.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatDecompressorInputStream(InputStream in, int bufferSize, Mode mode) {
    this(in, bufferSize, Algorithm.DEFLATE, mode, PollingMode.BUSY);
  }

  /**
   * Creates a new input stream with the given polling mode, {@link Algorithm#DEFLATE} and {@link
   * QatZipper#DEFAULT_MODE}.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param pmode the polling mode
   */
  public QatDecompressorInputStream(InputStream in, int bufferSize, PollingMode pmode) {
    this(in, bufferSize, Algorithm.DEFLATE, QatZipper.DEFAULT_MODE, pmode);
  }

  /**
   * Creates a new input stream with the given parameters and {@link PollingMode#BUSY}.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatDecompressorInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, Mode mode) {
    this(in, bufferSize, algorithm, mode, PollingMode.BUSY);
  }

  /**
   * Creates a new input stream with the given parameters and {@link QatZipper#DEFAULT_MODE}.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param pmode the polling mode
   */
  public QatDecompressorInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, PollingMode pmode) {
    this(in, bufferSize, algorithm, QatZipper.DEFAULT_MODE, pmode);
  }

  /**
   * Creates a new input stream with the given parameters.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   * @param pmode the polling mode
   */
  public QatDecompressorInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, Mode mode, PollingMode pmode) {
    super(in);
    if (bufferSize <= 0) throw new IllegalArgumentException();
    Objects.requireNonNull(in);
    inputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer.position(outputBuffer.capacity());
    qzip = new QatZipper(algorithm, mode, pmode);
    closed = false;
    eof = false;
  }

  /**
   * Reads the next byte of uncompressed data.
   *
   * @return the next byte of data or -1 if the end of the stream is reached.
   * @throws IOException if the stream is closed
   */
  @Override
  public int read() throws IOException {
    if (closed) throw new IOException("Stream is closed");
    if (eof && !outputBuffer.hasRemaining()) return -1;
    if (!outputBuffer.hasRemaining()) {
      fill();
    }
    if (eof && !outputBuffer.hasRemaining()) return -1;
    return Byte.toUnsignedInt(outputBuffer.get());
  }

  /**
   * Reads uncompressed data into the provided array.
   *
   * @param b the array into which the data is read
   * @return the number of bytes read
   * @throws IOException if the stream is closed
   */
  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  /**
   * Reads uncompressed data into the provided array.
   *
   * @param b the array into which the data is read
   * @param off the starting offset in the array
   * @param len the maximum number of bytes to be read
   * @return the number of bytes read
   * @throws IOException if the stream is closed
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (closed) throw new IOException("Stream is closed");
    Objects.requireNonNull(b);
    if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
    if (eof && !outputBuffer.hasRemaining()) return -1;
    int result = 0;
    int bytesToRead = 0;
    while (len > (bytesToRead = outputBuffer.remaining())) {
      outputBuffer.get(b, off, bytesToRead);
      len -= bytesToRead;
      result += bytesToRead;
      off += bytesToRead;
      if (eof) {
        return result == 0 ? -1 : result;
      }
      fill();
    }
    outputBuffer.get(b, off, len);
    result += len;
    return result;
  }

  /**
   * Returns an estimate of the number of uncompressed bytes that can be read.
   *
   * @return 0 if and only if the end of the stream is reached
   * @throws IOException if the stream is closed
   */
  @Override
  public int available() throws IOException {
    if (closed) throw new IOException("Stream is closed");
    if (outputBuffer.hasRemaining()) return outputBuffer.remaining();
    if (eof) return 0;
    else return 1;
  }

  /**
   * Closes this input stream and releases resources. This method will close the underlying output
   * stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Override
  public void close() throws IOException {
    if (closed) return;
    qzip.end();
    in.close();
    inputBuffer = null;
    outputBuffer = null;
    closed = true;
  }

  /** Marks the current position in this input stream. This method does nothing. */
  @Override
  public void mark(int readLimit) {}

  /**
   * Repositions this stream to the position at the time the mark method was last called. This
   * method does nothing but throw an IOException.
   *
   * @throws IOException when invoked.
   */
  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  /**
   * Tests if this input stream supports the mark and reset methods. This method unconditionally
   * returns false
   *
   * @return false
   */
  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Skips up to n bytes of compressed bytes.
   *
   * @param n the maximum number of bytes to skip
   * @return the number of bytes skipped or 0 if n is negative.
   */
  @Override
  public long skip(long n) throws IOException {
    if (n < 0) return 0;
    return read(new byte[(int) n]);
  }

  private void fill() throws IOException {
    if (eof) return;
    int bytesRead = in.read(inputBuffer.array(), inputBuffer.position(), inputBuffer.remaining());
    inputBuffer.limit(inputBuffer.position() + Math.max(0, bytesRead));
    inputBuffer.rewind();
    if (bytesRead < 0 && inputBuffer.remaining() == 0) {
      eof = true;
      return;
    }
    outputBuffer.clear();
    int decompressed = qzip.decompress(inputBuffer, outputBuffer);
    outputBuffer.flip();
    if (inputBuffer.hasRemaining()) inputBuffer.compact();
    else if (bytesRead < 0 && inputBuffer.remaining() == 0) eof = true;
    else inputBuffer.clear();
    if (decompressed == 0) fill();
  }
}
