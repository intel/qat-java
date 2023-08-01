/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.IOException;

public class QatInputStream extends InputStream {
	InputStream in;
	ByteBuffer inputBuffer;
	ByteBuffer outputBuffer;
	QatZipper qzip;

	public QatInputStream(InputStream in, int bufferSize, QatZipper.Algorithm algorithm) {
		this.in = in;
		inputBuffer = ByteBuffer.allocate(bufferSize);
		outputBuffer = ByteBuffer.allocate(bufferSize);
		outputBuffer.position(outputBuffer.capacity());
		qzip = new QatZipper(algorithm);
	}

	@Override
	public int read() throws IOException {
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
	public void close() throws IOException {
		qzip.end();
		in.close();
	}

	public void fill() throws IOException {
		outputBuffer.flip();
		// System.out.println("before refill input" +inputBuffer);
		int bytesRead = in.read(inputBuffer.array(), inputBuffer.position(), inputBuffer.remaining());
		if (bytesRead < 0) inputBuffer.limit(inputBuffer.position());
		// System.out.println("post refill input. got "+bytesRead+" more bytes");
		// System.out.println("*****about to call decompress. in => "+inputBuffer+" output=> "+outputBuffer);
		int decompressedLength = qzip.decompress(inputBuffer.rewind(), outputBuffer);
		// System.out.println("*****just called decompress. in => "+inputBuffer+" output=> "+outputBuffer);
		outputBuffer.flip();
		inputBuffer.compact();
	}
}
