/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;

import com.intel.qat.QatZipper.Builder;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * This class implements an OutputStream that compresses data using Intel &reg; QuickAssist
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
   * Creates a new output stream with the given parameters.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param builder pre configured QatZipper builder
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize, Builder builder) {
    super(out);
    if (bufferSize <= 0) throw new IllegalArgumentException();
    Objects.requireNonNull(out);
    qzip = builder.build();
    inputBuffer = new byte[bufferSize];
    outputBuffer = new byte[qzip.maxCompressedLength(bufferSize)];
    closed = false;
  }

  /**
   * Creates a new output stream with the given parameters.
   *
   * @param out the output stream
   * @param algorithm the compression algorithm
   */
  public QatCompressorOutputStream(OutputStream out, Algorithm algorithm) {
    this(out, DEFAULT_BUFFER_SIZE, algorithm);
  }

  /**
   * Creates a new output stream with the given parameters.
   *
   * @param out the output stream
   * @param bufferSize the output buffer size
   * @param algorithm the compression algorithm .
   */
  public QatCompressorOutputStream(OutputStream out, int bufferSize, Algorithm algorithm) {
    this(out, bufferSize, new Builder().algorithm(algorithm));
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
