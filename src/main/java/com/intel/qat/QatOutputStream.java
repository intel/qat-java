/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.io.IOException;

public class QatOutputStream extends FilterOutputStream {
	private ByteBuffer inputBuffer;
	private QatZipper qzip;
	private OutputStream out;
	private ByteBuffer outputBuffer;
    private boolean closed;

	public QatOutputStream(OutputStream out, int bufferSize, QatZipper.Algorithm algorithm) {
        super(out);
		this.out = out;
		qzip = new QatZipper(algorithm);
		inputBuffer = ByteBuffer.allocate(bufferSize);
		outputBuffer = ByteBuffer.allocate(qzip.maxCompressedLength(bufferSize));
        closed = false;
	}

	@Override
	public void write(int b) throws IOException {
        if (closed) throw new IOException("Stream is closed");
		if (!inputBuffer.hasRemaining()) {
			flush();
		}
		inputBuffer.put((byte)b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("Stream is closed");
		if (!inputBuffer.hasRemaining()) {
			flush();
		}
		int bytesToWrite = Math.min(len, inputBuffer.remaining());
		inputBuffer.put(b, off, bytesToWrite);
		if (bytesToWrite != len) {
			write(b, off +  bytesToWrite, len - bytesToWrite);
		}
	}

	@Override
	public void flush() throws IOException {
        if (closed) throw new IOException("Stream is closed");
		if (inputBuffer.position() == 0) return;
		inputBuffer.flip();
		int compressedBytes = qzip.compress(inputBuffer, outputBuffer);
		out.write(outputBuffer.array(), 0, compressedBytes);
		inputBuffer.clear();
		outputBuffer.clear();
	}

	@Override
	public void close() throws IOException {
        if (closed) return;
		flush();
		qzip.end();
		out.close();
        closed = true;
	}
}
