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

public class QatInputStream extends FilterInputStream {
  private InputStream in;
  private ByteBuffer inputBuffer;
  private ByteBuffer outputBuffer;
  private QatZipper qzip;
  private long totalDecompressed;
  private boolean closed;

  public QatInputStream(
      InputStream in, int bufferSize, QatZipper.Algorithm algorithm) {
    super(in);
    this.in = in;
    inputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer = ByteBuffer.allocate(bufferSize);
    outputBuffer.position(outputBuffer.capacity());
    qzip = new QatZipper(algorithm);
    closed = false;
  }

  @Override
  public int read() throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
    if (!outputBuffer.hasRemaining()) {
      fill();
    }
    return outputBuffer.get();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (closed)
      throw new IOException("Stream is closed");
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
    return read(new byte[(int) n]);
  }

  private void fill() throws IOException {
    outputBuffer.flip();
    int bytesRead = in.read(
        inputBuffer.array(), inputBuffer.position(), inputBuffer.remaining());
    if (bytesRead < 0)
      inputBuffer.limit(inputBuffer.position());
    else
      inputBuffer.limit(inputBuffer.position() + bytesRead);
    inputBuffer.rewind();
    totalDecompressed += qzip.decompress(inputBuffer, outputBuffer);
    outputBuffer.flip();
    inputBuffer.compact();
  }
}
