/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QATTest {
   
    private QATSession intQatSession;

    @AfterEach
    public void cleanupSession(){
        if(intQatSession != null)
            intQatSession.teardown();
    }
    @Test
    public void testDefaultConstructor(){
        try {
            intQatSession = new QATSession();
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }
    @Test
    public void testSingleArgConstructor(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    public void testTwoArgConstructor(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    public void testThreeArgConstructorAuto(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.AUTO);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    public void testThreeArgConstructorHW(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    public void testFourArgConstructorHW(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE,QATSession.DEFAULT_RETRY_COUNT);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    public void testTeardown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            qatSession.teardown();
        }
        catch (QATException e){
            fail(e.getMessage());
        }
    }

    @Test
    public void duplicateTearDown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4,0, QATSession.Mode.HARDWARE);
            qatSession.teardown();
            qatSession.teardown();
        }
        catch (IllegalStateException is){
            assertTrue(true);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
    }
    @Test
    void testWrappedBuffers(){
        try {
            intQatSession = new QATSession();
            intQatSession.setIsPinnedMemAvailable();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

            ByteBuffer srcBuffer = ByteBuffer.wrap(source);
            ByteBuffer destBuffer = ByteBuffer.wrap(dest);
            ByteBuffer uncompBuffer = ByteBuffer.wrap(uncomp);

            int compressedSize = intQatSession.compress(srcBuffer,destBuffer);

            if(compressedSize < 0)
                fail("testWrappedBuffers compression fails");

            assertNotNull(destBuffer);

            destBuffer.flip();

            int decompressedSize = intQatSession.decompress(destBuffer,uncompBuffer);
            assertNotNull(uncompBuffer);

            if(decompressedSize < 0)
                fail("testWrappedBuffers decompression fails");

            String str = new String(uncomp, StandardCharsets.UTF_8);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (QATException|IllegalStateException|IllegalArgumentException|ReadOnlyBufferException e){
            fail(e.getMessage());
        }
        assertTrue(true);
    }

    @Test
    void testBackedArrayBuffersWithAllocate(){
        try {
            intQatSession = new QATSession();
            intQatSession.setIsPinnedMemAvailable();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

            ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
            ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
            ByteBuffer uncompBuffer = ByteBuffer.allocate(uncomp.length);

            srcBuffer.put(source,0,source.length);
            srcBuffer.flip();
            int compressedSize = intQatSession.compress(srcBuffer,destBuffer);

            if(compressedSize < 0)
                fail("testIndirectBuffers compression fails");

            assertNotNull(destBuffer);

            destBuffer.flip();
            int decompressedSize = intQatSession.decompress(destBuffer,uncompBuffer);
            assertNotNull(uncompBuffer);

            if(decompressedSize <= 0)
                fail("testWrappedBuffers decompression fails");

            String str = new String(uncompBuffer.array(), StandardCharsets.UTF_8);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (QATException|IllegalStateException|IllegalArgumentException|ArrayIndexOutOfBoundsException e){
            fail(e.getMessage());
        }
        assertTrue(true);
    }

    @Test
    void testIndirectBuffersReadOnly(){
        try {
            intQatSession = new QATSession();
            intQatSession.setIsPinnedMemAvailable();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

            ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
            ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
            ByteBuffer uncompBuffer = ByteBuffer.allocate(uncomp.length);

            srcBuffer.put(source,0,source.length);
            srcBuffer.flip();

            int compressedSize = intQatSession.compress(srcBuffer.asReadOnlyBuffer(),destBuffer);

            if(compressedSize < 0)
                fail("testIndirectBuffers compression fails");

            assertNotNull(destBuffer);

            destBuffer.flip();
            int decompressedSize = intQatSession.decompress(destBuffer.asReadOnlyBuffer(),uncompBuffer);
            assertNotNull(uncompBuffer);

            if(decompressedSize <= 0)
                fail("testWrappedBuffers decompression fails");

            String str = new String(uncompBuffer.array(), StandardCharsets.UTF_8);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (QATException|IllegalStateException|IllegalArgumentException|ReadOnlyBufferException e){
            fail(e.getMessage());
        }
        assertTrue(true);
    }
    @Test
    void testCompressionDecompressionWithByteArray(){
        try{
            intQatSession = new QATSession();
            intQatSession.setIsPinnedMemAvailable();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

            int compressedSize = intQatSession.compress(source,0, source.length, dest,0);
            assertNotNull(dest);

            intQatSession.decompress(dest,0, compressedSize, uncomp, 0);
            assertNotNull(uncomp);
            String str = new String(uncomp, StandardCharsets.UTF_8);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (QATException|IllegalStateException|IllegalArgumentException|ArrayIndexOutOfBoundsException e){
            fail(e.getMessage());
        }
    }

    @Test
    void testCompressByteArrayWithByteBuff(){

        try{
            intQatSession = new QATSession();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

            ByteBuffer uncompressedBuffer = ByteBuffer.allocateDirect(source.length);
            ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(dest.length);
            uncompressedBuffer.put(source);
            uncompressedBuffer.flip();

            int compressedSize = intQatSession.compress(uncompressedBuffer,compressedBuffer);
            int byteArrayCompSize = intQatSession.compress(source,0,source.length,dest,0);

            assertEquals(compressedSize, byteArrayCompSize);

            compressedBuffer.flip();

            byte[] compByteBufferArray = new byte[compressedBuffer.limit()];
            compressedBuffer.get(compByteBufferArray);

            for(int i = 0; i < compressedSize; i++){
                if(dest[i] != compByteBufferArray[i])
                    fail("compressed data is not same");
            }
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testComppressionDecompressionHardwareMode(){
        try{
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE,0);

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[intQatSession.maxCompressedLength(source.length)];

            int compressedSize = intQatSession.compress(source,0, source.length, dest,0);
            assertNotNull(dest);

            intQatSession.decompress(dest,0, compressedSize, uncomp, 0);
            assertNotNull(uncomp);
            String str = new String(uncomp, StandardCharsets.UTF_8);

            assertEquals(str.equals(uncompressed), true);
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testCompressionWithInsufficientDestBuff(){
        try{
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE,0);

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = new byte[uncompressed.getBytes(Charset.forName("UTF-8")).length];
            byte[] dest = new byte[source.length/10];

            intQatSession.compress(source,0, source.length, dest,0);
        }
        catch (QATException|IndexOutOfBoundsException e){
            assertTrue(true);
        }
    }

    @Test
    void testDecompressionWithInsufficientDestBuff(){
        try{
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE,0);

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = new byte[uncompressed.getBytes(Charset.forName("UTF-8")).length];
            byte[] uncomp = new byte[source.length/2];
            byte[] dest = new byte[source.length];

            intQatSession.compress(source,0, source.length, dest,0);
            intQatSession.decompress(dest,0,dest.length,uncomp,0);
            fail("testInvalidDecompressionHardwareMode failed");
        }
        catch (QATException|IndexOutOfBoundsException e){
            assertTrue(true);
        }
    }

    @Test
    void testCompressionDecompressionWithDirectByteBuff(){

        try{
            intQatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(intQatSession.maxCompressedLength(source.length));
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

            srcBuff.put(source,0,source.length);
            srcBuff.flip();

            intQatSession.compress(srcBuff,destBuff);
            assertNotNull(destBuff);

            destBuff.flip();
            int decompressedSize = intQatSession.decompress(destBuff,unCompBuff);
            assertNotNull(uncomp);

            unCompBuff.flip();

            unCompBuff.get(uncomp,0,decompressedSize);
            String str = new String(uncomp, StandardCharsets.UTF_8);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testCompressionDecompressionWithDirectByteBuffNoPinnedMem(){

        try{
            intQatSession = new QATSession();
            intQatSession.setIsPinnedMemAvailable();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(intQatSession.maxCompressedLength(source.length));
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

            srcBuff.put(source,0,source.length);
            srcBuff.flip();

            intQatSession.compress(srcBuff,destBuff);
            assertNotNull(destBuff);

            destBuff.flip();
            int decompressedSize = intQatSession.decompress(destBuff,unCompBuff);
            assertNotNull(uncomp);

            unCompBuff.flip();

            unCompBuff.get(uncomp,0,decompressedSize);
            String str = new String(uncomp, StandardCharsets.UTF_8);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testCompressWithNullByteBuff(){
        try{
            intQatSession = new QATSession();

            int compressedSize = intQatSession.compress(null,null);
            fail("testCompressWithNullByteBuff fails");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }
    }

    @Test
    void testCompressWithNullByteArray(){
        try{
            intQatSession = new QATSession();
            intQatSession.compress(null,0,100,null,0);
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }
    }

    @Test
    void testDecompressWithNullByteBuff(){
        try{
            intQatSession = new QATSession();
            int compressedSize = intQatSession.decompress(null,null);
            fail("testDecompressWithNullByteBuff fails");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }
    }

    @Test
    void testDecompressWithNullByteArray(){
        try{
            intQatSession = new QATSession();
            int compressedSize = intQatSession.decompress(null,0,100,null,0);
            fail("testDecompressWithNullByteArray fails");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }
    }

    @Test
    void testCompressionReadOnlyDestination(){
        try{
            intQatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(intQatSession.maxCompressedLength(source.length));

            srcBuff.put(source,0,source.length);
            srcBuff.flip();
            intQatSession.compress(srcBuff,destBuff.asReadOnlyBuffer());
            fail("testCompressionReadOnlyDestination failed");
        }
        catch (ReadOnlyBufferException ie){
            assertTrue(true);
        }
    }

    @Test
    void testDecompressionReadOnlyDestination(){
        try{
            intQatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(intQatSession.maxCompressedLength(source.length));
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

            srcBuff.put(source,0,source.length);
            srcBuff.flip();

            intQatSession.compress(srcBuff,destBuff);
            destBuff.flip();
            intQatSession.decompress(destBuff,unCompBuff.asReadOnlyBuffer());
            fail("testDecompressionReadOnlyDestination failed");
        }
        catch (ReadOnlyBufferException ie){
            assertTrue(true);
        }
    }


    @Test
    void testCompDecompDefaultModeReadOnlyByteBuff(){
        try{
            intQatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] uncomp = new byte[source.length];

            ByteBuffer srcBuffRW = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(2*source.length);
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);
            srcBuffRW.put(source,0,source.length);
            srcBuffRW.flip();

            ByteBuffer srcBuffRO = srcBuffRW.asReadOnlyBuffer();
            intQatSession.compress(srcBuffRO,destBuff);
            assertNotNull(destBuff);
            destBuff.flip();
            int decompressedSize = intQatSession.decompress(destBuff,unCompBuff);
            assertNotNull(uncomp);
            unCompBuff.flip();
            unCompBuff.get(uncomp,0,decompressedSize);
            String str = new String(uncomp, StandardCharsets.UTF_8);

            assertEquals(str.equals(uncompressed), true);
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }


    @Test
    public void testNativeByteBuffer(){
        try {
            intQatSession = new QATSession();
            assertNotNull(intQatSession.unCompressedBuffer);
            assertNotNull(intQatSession.compressedBuffer);
            assertTrue(intQatSession.unCompressedBuffer.isDirect());
            assertTrue(intQatSession.unCompressedBuffer.isDirect());
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        assertTrue(true);
    }

    @Test
    public void multipleQATSessions(){
        try{
            QATSession[] qatSessions = new QATSession[10];

            for(int i = 0; i < qatSessions.length; i++){
                qatSessions[i] = new QATSession();
            }
            for(int i = 0; i < qatSessions.length; i++){
                assertTrue(qatSessions[i].compressedBuffer.isDirect());
            }
            for(int i = 0; i < qatSessions.length; i++) {
                qatSessions[i].teardown();
            }
            assertTrue(true);
        }
        catch (Exception e){
            fail(e.getMessage());
        }
    }




    @Test
    public void testIllegalStateException(){
        QATSession qatSession = null;
        String uncompressed = "lorem opsum lorem opsum opsum lorem";
        byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
        byte[] dest = new byte[2 * source.length];

        try {
            qatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4,0, QATSession.Mode.HARDWARE);
            qatSession.teardown();
            qatSession.compress(source,0,source.length,dest,0);
            fail("testIllegalStateException fails");
        }
        catch (IllegalStateException is){
            assertTrue(true);
        }
    }

    @Test
    public void testInvalidCompressionLevel(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 10, QATSession.Mode.AUTO,6);
            fail("testInvalidCompressionLevel failed");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }
    }

    @Test
    public void testInvalidRetryCount(){
        try {
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 10, QATSession.Mode.AUTO,-1);
            fail("testInvalidRetryCount failed");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }

    }

    @Test
    public void compressByteArrayPostTearDown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] dest = new byte[2 * source.length];

            qatSession.teardown();
            qatSession.compress(source,0,source.length,dest,0);
            fail("compressByteArrayPostTearDown failed");
        }
        catch (IllegalStateException ie){
            assertTrue(true);
        }
    }

    @Test
    public void compressByteBufferPostTearDown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(source.length));

            srcBuff.put(source,0,source.length);
            srcBuff.flip();

            qatSession.teardown();
            qatSession.compress(srcBuff,destBuff);
            fail("compressByteBufferPostTearDown failed");
        }
        catch (IllegalStateException ie){
            assertTrue(true);
        }
    }

    @Test
    public void decompressByteArrayPostTearDown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));
            byte[] dest = new byte[2 * source.length];
            qatSession.compress(source,0,source.length,dest,0);

            qatSession.teardown();
            qatSession.decompress(source,0,source.length,dest,0);
            fail("decompressByteArrayPostTearDown failed");
        }
        catch (IllegalStateException ie){
            assertTrue(true);
        }
    }

    @Test
    public void decompressByteBufferPostTearDown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes(Charset.forName("UTF-8"));

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(source.length));
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

            srcBuff.put(source,0,source.length);
            srcBuff.flip();

            qatSession.compress(srcBuff,destBuff);

            destBuff.flip();
            qatSession.teardown();
            qatSession.decompress(destBuff,unCompBuff);
            fail("compressByteBufferPostTearDown failed");
        }
        catch (IllegalStateException ie){
            assertTrue(true);
        }
    }

    @Test
    public void maxCompressedLengthPostTeardown(){
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            qatSession.teardown();
            qatSession.maxCompressedLength(100);
            fail("maxCompressedLengthPostTeardown failed");
        }
        catch (IllegalStateException ie){
            assertTrue(true);
        }
    }

    @Test
    public void testChunkedCompressionWithByteArray(){
        try{
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE);
            byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
            String book2 = new String(src, StandardCharsets.UTF_8);
            byte[] dest = new byte[intQatSession.maxCompressedLength(src.length)];
            byte[] unCompressed = new byte[src.length];

            int compressedSize = intQatSession.compress(src,0,src.length, dest,0);
            int decompressedSize = intQatSession.decompress(dest,0,compressedSize,unCompressed,0);

            assertTrue(compressedSize > 0);
            assertEquals(decompressedSize,src.length);

            assertTrue(book2.compareTo(new String(unCompressed, Charset.defaultCharset())) == 0);

        }
        catch (QATException | IOException e){
            fail(e.getMessage());
        }
    }

    @Test
    public void testChunkedCompressionWithByteBuff(){
        try{
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE);
            byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
            String book2 = new String(src, StandardCharsets.UTF_8);
            byte[] unCompressed = new byte[src.length];

            ByteBuffer srcBuffer = ByteBuffer.allocateDirect(src.length);
            ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(intQatSession.maxCompressedLength(src.length));
            ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(src.length);

            srcBuffer.put(src);
            srcBuffer.flip();

            int compressedSize = intQatSession.compress(srcBuffer,compressedBuffer);
            compressedBuffer.flip();
            int decompressedSize = intQatSession.decompress(compressedBuffer,decompressedBuffer);
            decompressedBuffer.flip();
            decompressedBuffer.get(unCompressed,0,decompressedSize);
            assertTrue(compressedSize > 0);
            assertEquals(decompressedSize,src.length);

            assertTrue(book2.compareTo(new String(unCompressed, Charset.defaultCharset())) == 0);

        }
        catch (QATException | IOException e){
            fail(e.getMessage());
        }
    }

    @Test
    public void testChunkedCompressionWithWrappedByteBuff(){
        try{
            intQatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE);
            byte[] src = Files.readAllBytes(Path.of("src/main/resources/book2"));
            String book2 = new String(src, StandardCharsets.UTF_8);
            byte[] unCompressed = new byte[src.length];

            ByteBuffer srcBuffer = ByteBuffer.allocate(src.length);
            ByteBuffer compressedBuffer = ByteBuffer.allocate(intQatSession.maxCompressedLength(src.length));
            ByteBuffer decompressedBuffer = ByteBuffer.allocate(src.length);

            srcBuffer.put(src);
            srcBuffer.flip();

            int compressedSize = intQatSession.compress(srcBuffer,compressedBuffer);
            compressedBuffer.flip();
            int decompressedSize = intQatSession.decompress(compressedBuffer,decompressedBuffer);

            decompressedBuffer.flip();
            decompressedBuffer.get(unCompressed,0,decompressedSize);
            assertTrue(compressedSize > 0);
            assertEquals(decompressedSize,src.length);

            assertTrue(book2.compareTo(new String(unCompressed, Charset.defaultCharset())) == 0);

        }
        catch (QATException | IOException e){
            fail(e.getMessage());
        }
    }
}
