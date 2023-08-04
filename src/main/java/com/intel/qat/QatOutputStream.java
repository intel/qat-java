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

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;

/**
 * This class implements an OutputStream filter that compresses data using
 * Intel(R) QuickAssist Technology (QAT). 
 **/
public class QatOutputStream extends FilterOutputStream {
  private ByteBuffer inputBuffer;
  private QatZipper qzip;
  private ByteBuffer outputBuffer;
  private boolean closed;
  
  /**
   * Creates a new output stream with default compression level and mode.
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   **/
  public QatOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm) {
    this(out, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL,
        Mode.AUTO);
  }

  /**
   * Creates a new output stream with default mode.
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   **/
  public QatOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, int level) {
    this(out, bufferSize, algorithm, level, Mode.AUTO);
  }

  /**
   * Creates a new output stream with default compression level.
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO -
   *     hardware with a software failover.)
   **/
  public QatOutputStream(
      OutputStream out, int bufferSize, Algorithm algorithm, Mode mode) {
    this(out, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, mode);
  }

  /**
   * Creates a new output stream.
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO -
   *     hardware with a software failover.)
   **/
  public QatOutputStream(OutputStream out, int bufferSize, Algorithm algorithm,
      int level, Mode mode) {
    super(out);
    qzip = new QatZipper(algorithm, level, mode);
    inputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer = ByteBuffer.allocate(qzip.maxCompressedLength(bufferSize));
    closed = false;
  }

  /**
   * Writes a byte to the compressed output stream.
   * @param b the data to be written
   * @throws IOException if this stream is closed
   **/
  @Override
  public void write(int b) throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (!inputBuffer.hasRemaining()) {
      flush();
    }
    inputBuffer.put((byte) b);
  }

  /**
   * Writes data from the given byte array to the compressed output stream.
   * @param b the data to be written
   * @throws IOException if this stream is closed
   **/
  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  /**
   * Writes data from the given byte array to the compressed output stream.
   * @param b the data to be written
   * @param off the starting offset of the data
   * @param len the length of the data
   * @throws IOException if this stream is closed
   **/
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (!inputBuffer.hasRemaining()) {
      flush();
    }
    int bytesToWrite = Math.min(len, inputBuffer.remaining());
    inputBuffer.put(b, off, bytesToWrite);
    if (bytesToWrite != len) {
      write(b, off + bytesToWrite, len - bytesToWrite);
    }
  }

  /**
   * Flushes all buffered data to the compressed output stream. This method
   * will compress and write all buffered data to the output stream.
   * @throws IOException if this stream is closed
   **/
  @Override
  public void flush() throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (inputBuffer.position() == 0)
      return;
    inputBuffer.flip();
    int compressedBytes = qzip.compress(inputBuffer, outputBuffer);
    out.write(outputBuffer.array(), 0, compressedBytes);
    inputBuffer.clear();
    outputBuffer.clear();
  }

  /**
   * Writes any remaining data to the compressed output stream and releases resources. This method
   * will close the underlying output stream.
   * @throws IOException if an I/O error occurs
   **/
  @Override
  public void close() throws IOException {
    if (closed)
      return;
    flush();
    qzip.end();
    out.close();
    closed = true;
  }
}
