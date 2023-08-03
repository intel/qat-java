/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;

public class QatInputStream extends FilterInputStream {
  private ByteBuffer inputBuffer;
  private ByteBuffer outputBuffer;
  private QatZipper qzip;
  private long totalDecompressed;
  private boolean closed;
  private boolean eof;

  public QatInputStream(
      InputStream in, int bufferSize, Algorithm algorithm) {
    this(in, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, Mode.AUTO);
  }

  public QatInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, int level) {
    this(in, bufferSize, algorithm, level, Mode.AUTO);
  }

  public QatInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, Mode mode) {
    this(in, bufferSize, algorithm, QatZipper.DEFAULT_COMPRESS_LEVEL, mode);
  }

  public QatInputStream(
      InputStream in, int bufferSize, Algorithm algorithm, int level, Mode mode) {
    super(in);
    inputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer.position(outputBuffer.capacity());
    qzip = new QatZipper(algorithm, level, mode);
    closed = false;
    eof = false;
  }

  @Override
  public int read() throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (!outputBuffer.hasRemaining()) {
      fill();
    }
    return outputBuffer.hasRemaining() ? outputBuffer.get() : -1;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (off < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
    if (!outputBuffer.hasRemaining()) {
      fill();
    }
    int bytesToRead = Math.min(len, outputBuffer.remaining());
    outputBuffer.get(b, off, bytesToRead);
    if (bytesToRead != len) {
      fill();
      bytesToRead += read(b, off + bytesToRead, len - bytesToRead);
    }
    return bytesToRead;
  }

  @Override
  public int available() throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    return outputBuffer.remaining() + Math.min(1, in.available());
  }

  @Override
  public void close() throws IOException {
    if (closed)
      return;
    qzip.end();
    in.close();
    closed = true;
  }

  @Override
  public void mark(int readLimit) {}

  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public long skip(long n) throws IOException {
    if (n < 0) return 0;
    return read(new byte[(int) n]);
  }

  private void fill() throws IOException {
    outputBuffer.flip();
    int bytesRead = in.read(
        inputBuffer.array(), inputBuffer.position(), inputBuffer.remaining());
    if (bytesRead < 0) {
      eof = true;
      inputBuffer.limit(inputBuffer.position());
    }
    else
      inputBuffer.limit(inputBuffer.position() + bytesRead);
    inputBuffer.rewind();
    totalDecompressed += qzip.decompress(inputBuffer, outputBuffer);
    outputBuffer.flip();
    inputBuffer.compact();
  }
}
