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

import java.io.ByteArrayInputStream;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class QatInputStreamTests {
    private static byte[] src;
    private static byte[] deflateBytes;
    private static byte[] lz4Bytes;
    private Random rnd = new Random();

    @BeforeAll
    public static void setup() throws IOException {
		src = Files.readAllBytes(Paths.get("src/main/resources/sample.txt"));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, Algorithm.DEFLATE)) {
            compressedStream.write(src);
        }
        QatZipper qzip = new QatZipper(Algorithm.DEFLATE);
        deflateBytes = new byte[qzip.maxCompressedLength(src.length)];
        int deflateLen = qzip.compress(src, 0, src.length, deflateBytes, 0, deflateBytes.length); 
        deflateBytes = Arrays.copyOfRange(deflateBytes, 0, deflateLen);
        qzip.end(); //deflateBytes = outputStream.toByteArray();
		outputStream = new ByteArrayOutputStream();
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 16 * 1024, Algorithm.LZ4)) {
            compressedStream.write(src);
        }
        //qzip = new QatZipper(Algorithm.LZ4);
        //lz4Bytes = new byte[qzip.maxCompressedLength(src.length)];
        //int lz4Len = qzip.compress(src, 0, src.length, lz4Bytes, 0, lz4Bytes.length); 
        //lz4Bytes = Arrays.copyOfRange(lz4Bytes, 0, lz4Len);
        //qzip.end(); 
        lz4Bytes = outputStream.toByteArray();
    }

    /*@AfterEach
    public void cleanupSession() {
        if (qzip != null)
        qzip.end();
    }*/

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testInputStreamReadAll1(Algorithm algo) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
        byte[] result = new byte[src.length];
		try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {
            int read = decompressedStream.read(result);
            assertEquals(result.length, read);
        }
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testInputStreamReadAll3(Algorithm algo) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
		byte[] result = new byte[src.length];
		try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {
            int i; int len = 0;
            for (i = 0; i < result.length; i += len) {
                len = Math.min(rnd.nextInt(20 *1024), result.length - i);
                int read = decompressedStream.read(result, i, len);
                assertEquals(len, read);
            }
            assertEquals(result.length, i);
        }
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testInputStreamReadByte(Algorithm algo) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
		byte[] result = new byte[src.length];
		try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {
            int i; int len = 0; 
            for (i = 0; i < result.length; i += len) {
                if (i % 10 == 0) { //doReadByte
                    len = 1;
                    result[i] = (byte)decompressedStream.read();
                } else {
                    len = Math.min(rnd.nextInt(20 *1024), result.length - i);
                    int read = decompressedStream.read(result, i, len);
                    assertEquals(len, read);
                }
            }
            assertEquals(result.length, i);
        }
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testInputStreamReadAvailable(Algorithm algo) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
		byte[] result = new byte[src.length];
		try (QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo)) {
            int i; int len = 0; 
            for (i = 0; i < result.length; i += len) {
                len = decompressedStream.available();
                int read = decompressedStream.read(result, i, len);
                assertEquals(len, read);
            }
            assertEquals(result.length, i);
        }
        assertTrue(Arrays.equals(src, result));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testInputStreamClose(Algorithm algo) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
        byte[] result = new byte[src.length];
		QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo);
        decompressedStream.close();
        try {
            decompressedStream.read(result);
            fail("Failed to catch IOException!");
        } catch (IOException ioe) {
            assertTrue(true);
        }
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testInputStreamDoubleClose(Algorithm algo) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(algo.equals(Algorithm.LZ4) ? lz4Bytes : deflateBytes);
        byte[] result = new byte[src.length];
		QatInputStream decompressedStream = new QatInputStream(inputStream, 16 * 1024, algo);
        decompressedStream.close();
        decompressedStream.close();
        assertTrue(true);
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    //parameterize skipBytes
    public void testInputStreamSkip(Algorithm algo) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int bytesToSkip = 1 + rnd.nextInt(16 * 1024);
		try (QatOutputStream compressedStream = new QatOutputStream(outputStream, 512 * 1024, algo)) {
            compressedStream.write(src);
            compressedStream.write(new byte[bytesToSkip]);
            compressedStream.write(src);
        }
        byte[] srcBytes = outputStream.toByteArray();

		ByteArrayInputStream inputStream = new ByteArrayInputStream(srcBytes);
        byte[] result1 = new byte[src.length];
        byte[] result2 = new byte[src.length];
		try(QatInputStream decompressedStream = new QatInputStream(inputStream, 512 * 1024, algo)) {
            int read = decompressedStream.read(result1);
            assertTrue(result1.length == read); 
            long skipped = decompressedStream.skip(bytesToSkip);
            assertTrue(skipped == bytesToSkip);
            read = decompressedStream.read(result2);
            assertTrue(result2.length == read); 
        }
        assertTrue(Arrays.equals(src, result1));
        assertTrue(Arrays.equals(src, result2));
    }
}


