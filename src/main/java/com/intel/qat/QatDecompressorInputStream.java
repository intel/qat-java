/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;

import com.github.luben.zstd.ZstdException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.util.Objects;

/**
 * This class implements an InputStream that decompresses data using Intel &reg; QuickAssist
 * Technology (QAT).
 */
public class QatDecompressorInputStream extends FilterInputStream {
  private byte[] inputBuffer;
  private byte[] outputBuffer;
  private int inputPosition;
  private int outputPosition;
  private int inputBufferLimit;
  private int outputBufferLimit;
  private QatZipper qzip;
  private boolean closed;
  private boolean eof;

  /** The default size in bytes of the input buffer (64KB). */
  public static final int DEFAULT_BUFFER_SIZE = 1 << 16;

  /** The maximum buffer size in bytes of the input buffer (512KB). */
  public static int MAX_BUFFER_SIZE = 512 * 1024;

  /**
   * Creates a new input stream with the given parameters.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param algorithm the compression algorithm.
   */
  public QatDecompressorInputStream(InputStream in, int bufferSize, Algorithm algorithm) {
    this(in, bufferSize, new QatZipper.Builder().setAlgorithm(algorithm));
  }

  /**
   * Creates a new input stream with the given parameters.
   *
   * @param in the input stream
   * @param algorithm the compression algorithm.
   */
  public QatDecompressorInputStream(InputStream in, Algorithm algorithm) {
    this(in, DEFAULT_BUFFER_SIZE, algorithm);
  }

  /**
   * Creates a new input stream with the given parameters.
   *
   * @param in the input stream
   * @param bufferSize the input buffer size
   * @param builder pre configured QatZipper builder
   */
  public QatDecompressorInputStream(InputStream in, int bufferSize, QatZipper.Builder builder) {
    super(in);
    if (bufferSize <= 0) throw new IllegalArgumentException();
    Objects.requireNonNull(in);
    if (builder.getAlgorithm().equals(Algorithm.ZSTD)) MAX_BUFFER_SIZE = Integer.MAX_VALUE - 1;
    int inputBufferSize = 0;
    try {
      inputBufferSize = Math.max(bufferSize, in.available());
      inputBufferSize = Math.min(MAX_BUFFER_SIZE, inputBufferSize);
    } catch (IOException ioe) {
      inputBufferSize = bufferSize;
    }
    inputBuffer = new byte[inputBufferSize];
    outputBuffer = new byte[bufferSize];
    outputPosition = outputBuffer.length;
    inputBufferLimit = inputBuffer.length;
    outputBufferLimit = outputBuffer.length;
    closed = false;
    eof = false;
    this.qzip = builder.build();
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
    if (eof && outputPosition == outputBufferLimit) return -1;
    if (outputPosition == outputBufferLimit) {
      fill();
    }
    if (eof && outputPosition == outputBufferLimit) return -1;
    return Byte.toUnsignedInt(outputBuffer[outputPosition++]);
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
    if (eof && outputPosition == outputBufferLimit) return -1;
    int result = 0;
    int bytesToRead = 0;
    while (len > (bytesToRead = outputBufferLimit - outputPosition)) {
      System.arraycopy(outputBuffer, outputPosition, b, off, bytesToRead);
      outputPosition += bytesToRead;
      len -= bytesToRead;
      result += bytesToRead;
      off += bytesToRead;
      if (eof) {
        return result == 0 ? -1 : result;
      }
      fill();
    }
    System.arraycopy(outputBuffer, outputPosition, b, off, len);
    outputPosition += len;
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
    if (outputPosition != outputBufferLimit) return (outputBufferLimit - outputPosition);
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

  private void growOutputBuffer() {
    int oldSize = outputBuffer.length;
    if (oldSize == MAX_BUFFER_SIZE) throw new BufferOverflowException();
    int newSize = (oldSize > Integer.MAX_VALUE / 2) ? MAX_BUFFER_SIZE : oldSize * 2;
    outputBuffer = new byte[newSize];
    outputPosition = 0;
    outputBufferLimit = outputBuffer.length;
  }

  private void fill() throws IOException {
    if (eof) return;

    // Read from inputStream
    int bytesRead = 0;
    while (inputPosition < inputBufferLimit && bytesRead >= 0) {
      bytesRead = in.read(inputBuffer, inputPosition, inputBufferLimit - inputPosition);
      inputPosition += Math.max(0, bytesRead);
      if (bytesRead < 0 && inputPosition == 0) {
        eof = true;
        return;
      }
    }

    inputBufferLimit = inputPosition;
    inputPosition = 0;

    int decompressed = 0;
    outputPosition = 0;
    outputBufferLimit = outputBuffer.length;
    for (; (decompressed == 0 && inputPosition == 0); ) {
      try {
        decompressed =
            qzip.decompress(
                inputBuffer,
                inputPosition,
                inputBufferLimit,
                outputBuffer,
                outputPosition,
                outputBufferLimit - outputPosition);
        inputPosition += qzip.getBytesRead();
        outputPosition += decompressed;
      } catch (ZstdException zstde) {
        growOutputBuffer();
      }
      if (decompressed == 0 && inputPosition == 0) growOutputBuffer();
    }
    outputBufferLimit = outputPosition;
    outputPosition = 0;
    if (inputPosition != inputBufferLimit) {
      System.arraycopy(
          inputBuffer, inputPosition, inputBuffer, 0, inputBufferLimit - inputPosition);
      inputPosition = inputBufferLimit - inputPosition;
      inputBufferLimit = inputBuffer.length;
    } else if (bytesRead < 0 && inputPosition == inputBufferLimit) eof = true;
    else {
      inputPosition = 0;
      inputBufferLimit = inputBuffer.length;
    }
    if (decompressed == 0) {
      fill();
    }
  }
}
