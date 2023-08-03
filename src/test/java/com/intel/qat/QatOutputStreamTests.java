/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.intel.qat.QatZipper.Algorithm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class QatOutputStreamTests {
  private QatZipper qzip;

  private Random rnd = new Random();

    @AfterEach
    public void cleanupSession() {
        if (qzip != null)
        qzip.end();
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamWriteAll1(Algorithm algo) throws IOException {
        qzip = new QatZipper(algo);
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo)) {
            compressedStream.write(src);
        }
        byte[] outputStreamBuf = outputStream.toByteArray();
		byte[] result = new byte[src.length];
		int decompressedLen = qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);
		
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamWriteAll3(Algorithm algo) throws IOException {
        qzip = new QatZipper(algo);
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo)) {
            int i; int len = 0;
            for (i = 0; i < src.length; i += len) {
                len = Math.min(rnd.nextInt(20 *1024), src.length - i);
                compressedStream.write(src, i, len);
            }
            assertEquals(src.length, i);
        }
        byte[] outputStreamBuf = outputStream.toByteArray();
		byte[] result = new byte[src.length];
		int decompressedLen = qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);
		
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamWriteByte(Algorithm algo) throws IOException {
        qzip = new QatZipper(algo);
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo)) {
            int i; int len = 0; 
            for (i = 0; i < src.length; i += len) {
                if (i % 10 == 0) { //doWriteByte
                    len = 1;
                    compressedStream.write((int)src[i]);
                } else {
                    len = Math.min(rnd.nextInt(20 *1024), src.length - i);
                    compressedStream.write(src, i, len);
                }
            }
            assertEquals(src.length, i);
        }
        byte[] outputStreamBuf = outputStream.toByteArray();
		byte[] result = new byte[src.length];
		int decompressedLen = qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);
		
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamWriteFlush(Algorithm algo) throws IOException {
        qzip = new QatZipper(algo);
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo)) {
            int i; int len = 0; 
            for (i = 0; i < src.length; i += len) {
                if (i > 0 && i % 10 == 0) { //doFlush
                    compressedStream.flush();
                } 
                len = Math.min(rnd.nextInt(20 *1024), src.length - i);
                compressedStream.write(src, i, len);
            }
            assertEquals(src.length, i);
        }
        byte[] outputStreamBuf = outputStream.toByteArray();
		byte[] result = new byte[src.length];
		int decompressedLen = qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);
		
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamClose(Algorithm algo) throws IOException {
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo);
        compressedStream.close();
        try {
            compressedStream.write(src);
            fail("Failed to catch IOException!");
        } catch (IOException ioe) {
            assertTrue(true);
        }
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamDoubleClose(Algorithm algo) throws IOException {
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo);
        compressedStream.close();
        compressedStream.close();
        assertTrue(true);
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOutputStreamFlushOnClose(Algorithm algo) throws IOException {
        QatZipper qzip = new QatZipper(algo);
		byte[] src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] preResult;
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, algo)) {
            compressedStream.write(src);
            preResult = outputStream.toByteArray();
        }

        byte[] outputStreamBuf = outputStream.toByteArray();
        assertFalse(Arrays.equals(outputStreamBuf, preResult));
		byte[] result = new byte[src.length];
		int decompressedLen = qzip.decompress(outputStreamBuf, 0, outputStreamBuf.length, result, 0, result.length);
		
        assertTrue(Arrays.equals(src, result));
    }
}


