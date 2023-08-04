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
 * Intel (R) QuickAssist Technology (QAT). The Qat-Java library is dependent on 
 * and uses the <ahref="https://github.com/intel/QATzip">QATZip library</a>.
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

  @Override
  public void write(int b) throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (!inputBuffer.hasRemaining()) {
      flush();
    }
    inputBuffer.put((byte) b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

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
