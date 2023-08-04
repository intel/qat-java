/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * This class implements an InputStream filter that decompresses data using
 * Intel &reg; QuickAssist Technology (QAT).
 **/
public class QatInputStream extends FilterInputStream {
  private ByteBuffer inputBuffer;
  private ByteBuffer outputBuffer;
  private QatZipper qzip;
  private long totalDecompressed;
  private boolean closed;
  private boolean eof;

  /**
   * Creates a new input stream with {@link Algorithm#DEFLATE}, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link Mode#AUTO}.
   * @param in the input stream
   * @param bufferSize the input buffer size
   *     hardware with a software failover.)
   **/
  public QatInputStream(InputStream in, int bufferSize) {
    this(in, bufferSize, Algorithm.DEFLATE, QatZipper.DEFAULT_COMPRESS_LEVEL,
        Mode.AUTO);
  }

  /**
   * Creates a new input stream with the given algorithm, {@link
   * QatZipper#DEFAULT_COMPRESS_LEVEL}, and {@link Mode#AUTO}.
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   *     hardware with a software failover.)
   **/
  public QatInputStream(InputStream in, int bufferSize, Algorithm algorithm) {
    this(
        in, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, Mode.AUTO);
  }

  /**
   * Creates a new input stream with the given paramters and {@link Mode#AUTO}
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   **/
  public QatInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, int level) {
    this(in, bufferSize, algorithm, level, Mode.AUTO);
  }

  /**
   * Creates a new input stream with the given parameters and {@link QatZipper
   * #DEFAULT_COMPRESS_LEVEL}
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO -
   *     hardware with a software failover.)
   **/
  public QatInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, Mode mode) {
    this(in, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, mode);
  }

  /**
   * Creates a new input stream with the given parameters.
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO -
   *     hardware with a software failover.)
   **/
  public QatInputStream(InputStream in, int bufferSize, Algorithm algorithm,
      int level, Mode mode) {
    super(in);
    inputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer.position(outputBuffer.capacity());
    qzip = new QatZipper(algorithm, level, mode);
    closed = false;
    eof = false;
  }

  /**
   * Reads the next byte of uncompressed data.
   * @return the next byte of data or -1 if the end of the stream is reached.
   * @throws IOException if the stream is closed
   **/
  @Override
  public int read() throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (eof && !outputBuffer.hasRemaining())
      return -1;
    if (!outputBuffer.hasRemaining()) {
      fill();
    }
    return outputBuffer.get();
  }

  /**
   * Reads uncompressed data into the provided array.
   * @param b the array into which the data is read
   * @return the number of bytes read
   * @throws IOException if the stream is closed
   **/
  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  /**
   * Reads uncompressed data into the provided array.
   * @param b the array into which the data is read
   * @param off the starting offset in the array
   * @param len the maximum number of bytes to be read
   * @return the number of bytes read
   * @throws IOException if the stream is closed
   **/
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (off < 0 || off + len > b.length)
      throw new IndexOutOfBoundsException();
    if (eof && !outputBuffer.hasRemaining())
      return -1;

    int result = 0;
    int bytesToRead = 0;
    while (len > (bytesToRead = outputBuffer.remaining())) {
      outputBuffer.get(b, off, bytesToRead);
      len -= bytesToRead;
      result += bytesToRead;
      off += bytesToRead;
      if (eof)
        return result;
      fill();
    }
    outputBuffer.get(b, off, len);
    result += len;
    return result;
  }

  /**
   * Returns an estimate of the number of uncompressed bytes that can be read.
   * @return 0 if and only if the end of the stream is reached
   * @throws IOException if the stream is closed
   **/
  @Override
  public int available() throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (outputBuffer.hasRemaining())
      return outputBuffer.remaining();
    if (eof)
      return 0;
    else
      return 1;
  }

  /**
   * Closes this input stream and releases resources. This method
   * will close the underlying output stream.
   * @throws IOException if an I/O error occurs
   **/
  @Override
  public void close() throws IOException {
    if (closed)
      return;
    qzip.end();
    in.close();
    closed = true;
  }

  /**
   * Marks the current position in this input stream. This method
   * does nothing.
   **/
  @Override
  public void mark(int readLimit) {}

  /**
   * Repositions this stream to the position at the time the mark method was
   *last called. This method does nothing but throw an IOException.
   * @throws IOException when invoked.
   **/
  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  /**
   * Tests if this input stream supports the mark and reset methods. This method
   *unconditionally returns false
   * @return false
   **/
  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Skips up to n bytes of compressed bytes.
   * @param n the maximum number of bytes to skip
   * @return the number of bytes skipped or 0 if n is negative.
   **/
  @Override
  public long skip(long n) throws IOException {
    if (n < 0)
      return 0;
    return read(new byte[(int) n]);
  }

  private void fill() throws IOException {
    if (eof)
      return;
    outputBuffer.flip();
    int bytesRead = in.read(
        inputBuffer.array(), inputBuffer.position(), inputBuffer.remaining());
    if (bytesRead < 0) {
      inputBuffer.limit(inputBuffer.position());
    } else
      inputBuffer.limit(inputBuffer.position() + bytesRead);
    inputBuffer.rewind();
    if (bytesRead < 0 && inputBuffer.remaining() == 0) {
      eof = true;
      return;
    }
    int decompressed = qzip.decompress(inputBuffer, outputBuffer);
    if (decompressed > 0)
      outputBuffer.flip();
    if (inputBuffer.hasRemaining())
      inputBuffer.compact();
    else if (bytesRead < 0 && inputBuffer.remaining() == 0)
      eof = true;
    else
      inputBuffer.clear();
  }
}
