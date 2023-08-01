/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.io.IOException;

public class QatOutputStream extends OutputStream {
	private ByteBuffer inputBuffer;
	private QatZipper qzip;
	private OutputStream out;
	private ByteBuffer outputBuffer;

	public QatOutputStream(OutputStream out, int bufferSize, QatZipper.Algorithm algorithm) {
		this.out = out;
		qzip = new QatZipper(algorithm);
		inputBuffer = ByteBuffer.allocate(bufferSize);
		outputBuffer = ByteBuffer.allocate(qzip.maxCompressedLength(bufferSize));
	}

	@Override
	public void write(int b) throws IOException {
		if (!inputBuffer.hasRemaining()) {
			internalFlush(false);
		}
		inputBuffer.put((byte)b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (!inputBuffer.hasRemaining()) {
			internalFlush(false);
		}
		int bytesToWrite = Math.min(len, inputBuffer.remaining());
		inputBuffer.put(b, off, bytesToWrite);
		if (bytesToWrite != len) {
			write(b, off +  bytesToWrite, len - bytesToWrite);
		}
	}

	@Override
	public void flush() throws IOException {
        internalFlush(true);
    }

	public void internalFlush(boolean last) throws IOException {
		if (inputBuffer.position() == 0) return;
		inputBuffer.flip();
		int compressedBytes = qzip.compress(inputBuffer, outputBuffer);
		out.write(outputBuffer.array(), 0, compressedBytes);
		inputBuffer.clear();
		outputBuffer.clear();
	}

	@Override
	public void close() throws IOException {
		flush();
		qzip.end();
		out.close();
	}
}
