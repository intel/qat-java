/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

public class QATTest {
    private int numberOfThreads;
    private File filePath, filesList[];
    private Thread[] threads;

    private int filesPerThread,remainingFiles;

    public void QATTest(){
        this.numberOfThreads = 11;
        filePath = new File("../resources");
        filesList = filePath.listFiles();
        threads = new Thread[numberOfThreads];
        int filesPerThread = filesList.length / numberOfThreads;
        int remainingFiles = filesList.length % numberOfThreads;
    }

    /*@Test
    void testPinnedMemFailure(){
        System.out.println("EXECUTING testPinnedMemFailure..");
        int limit = 100_000_000;
        QATSession[] qatSession = new QATSession[limit];
        try {
            for (int i = 0; i < limit; i++) {
                qatSession[i] = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 6, QATSession.Mode.HARDWARE, 0);
                if(qatSession[i].compressedBuffer == null && qatSession[i].unCompressedBuffer == null) {
                    System.out.println("limit exhausted checked buffers");
                    assertTrue(true);
                }
            }
        }
        catch (Exception e){
            assertTrue(true);
        }
        fail("pinned mem did not exhaust");
    }*/
    @Test
    void testWrappedBuffers(){
        System.out.println("EXECUTING testWrappedBuffers..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes();
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[2 * source.length];

            ByteBuffer srcBuffer = ByteBuffer.wrap(source);
            ByteBuffer destBuffer = ByteBuffer.wrap(dest);
            ByteBuffer uncompBuffer = ByteBuffer.wrap(uncomp);


            System.out.println("---------- Java test: source buffer position "+ srcBuffer.position() +" and remaining "+ srcBuffer.remaining());
            System.out.println("---------- Java test: compression started");

            int compressedSize = qatSession.compress(srcBuffer,destBuffer);

            if(compressedSize < 0)
                fail("testWrappedBuffers compression fails");
            assertNotNull(destBuffer);
            System.out.println("---------- Java test: compression of size " + compressedSize);
            System.out.println("---------- Java test: compressedBuffer " + destBuffer.position());
            //destBuffer.limit(compressedSize);
            destBuffer.flip();
            int decompressedSize = qatSession.decompress(destBuffer,uncompBuffer);
            assertNotNull(uncompBuffer);

            if(decompressedSize < 0)
                fail("testWrappedBuffers decompression fails");

            String str = new String(uncomp, StandardCharsets.UTF_8);
            System.out.println(" result string "+ str);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (Exception e){
            e.getStackTrace();
            fail(e.getMessage());
        }
        assertTrue(true);
    }
    @Test
    void testIndirectBuffers(){
        System.out.println("EXECUTING testIndirectBuffers..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes();
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[2 * source.length];

            ByteBuffer srcBuffer = ByteBuffer.allocate(source.length);
            ByteBuffer destBuffer = ByteBuffer.allocate(dest.length);
            ByteBuffer uncompBuffer = ByteBuffer.allocate(uncomp.length);
            srcBuffer.put(source,0,source.length);

            System.out.println("---------- Java test: source buffer position "+ srcBuffer.position() +" and remaining "+ srcBuffer.remaining());
            System.out.println("---------- Java test: compression started");
            srcBuffer.flip();
            int compressedSize = qatSession.compress(srcBuffer,destBuffer);

            if(compressedSize < 0)
                fail("testWrappedBuffers compression fails");
            assertNotNull(destBuffer);
            System.out.println("---------- Java test: compression of size " + compressedSize);
            System.out.println("---------- Java test: compressedBuffer " + destBuffer.position());
            //destBuffer.limit(compressedSize);
            destBuffer.flip();
            int decompressedSize = qatSession.decompress(destBuffer,uncompBuffer);
            assertNotNull(uncompBuffer);

            if(decompressedSize < 0)
                fail("testWrappedBuffers decompression fails");

            String str = new String(uncomp, StandardCharsets.UTF_8);
            System.out.println(" result string "+ str);
            assertTrue(str.compareTo(uncompressed) == 0);
        }
        catch (Exception e){
            e.getStackTrace();
            fail(e.getMessage());
        }
        assertTrue(true);
    }
    @Test
    void testCompDecompDefaultMode(){
        System.out.println("EXECUTING testCompDecompDefaultMode..");
        QATSession qatSession = null;
        try{
            qatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes();
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[2 * source.length];

            int compressedSize = qatSession.compress(source,0, source.length, dest,0);
            assertNotNull(dest);

            int decompressedSize = qatSession.decompress(dest,0, compressedSize, uncomp, 0);
            assertNotNull(uncomp);
            String str = new String(uncomp, StandardCharsets.UTF_8);
            System.out.println("resulting string "+ str);
            assertTrue(str.compareTo(uncompressed) == 0);
            qatSession.teardown();
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testCompDecompHardwareMode(){
        System.out.println("EXECUTING testCompDecompHardwareMode..");
        QATSession qatSession = null;
        try{
            qatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,6, QATSession.Mode.HARDWARE,0);

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes();
            byte[] uncomp = new byte[source.length];
            byte[] dest = new byte[2 * source.length];

            int compressedSize = qatSession.compress(source,0, source.length, dest,0);
            assertNotNull(dest);

            int decompressedSize = qatSession.decompress(dest,0, compressedSize, uncomp, 0);
            assertNotNull(uncomp);
            String str = new String(uncomp, StandardCharsets.UTF_8);

            assertEquals(str.equals(uncompressed), true);
            qatSession.teardown();
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testCompDecompDefaultModeByteBuff(){
        System.out.println("EXECUTING testCompDecompDefaultModeByteBuff..");
        QATSession qatSession = null;
        try{
            qatSession = new QATSession();

            String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes();
            byte[] uncomp = new byte[source.length];

            ByteBuffer srcBuff = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(qatSession.maxCompressedLength(source.length));
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);

            srcBuff.put(source,0,source.length);
            srcBuff.flip();

            int compressedSize = qatSession.compress(srcBuff,destBuff);
            assertNotNull(destBuff);
            destBuff.flip();
            int decompressedSize = qatSession.decompress(destBuff,unCompBuff);
            assertNotNull(uncomp);
            unCompBuff.flip();
            unCompBuff.get(uncomp,0,decompressedSize);
            String str = new String(uncomp);
            System.out.println("uncompressed "+ uncompressed + " and final result " + str);
            assertTrue(str.compareTo(uncompressed) == 0);
            qatSession.teardown();
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }

    @Test
    void testCompDecompDefaultModeReadOnlyByteBuff(){
        System.out.println("EXECUTING testCompDecompDefaultModeReadOnlyByteBuff..");
        QATSession qatSession = null;
        try{
            qatSession = new QATSession();

                String uncompressed = "lorem opsum lorem opsum opsum lorem";
            byte[] source = uncompressed.getBytes();
            byte[] uncomp = new byte[source.length];

            ByteBuffer srcBuffRW = ByteBuffer.allocateDirect(source.length);
            ByteBuffer destBuff = ByteBuffer.allocateDirect(2*source.length);
            ByteBuffer unCompBuff = ByteBuffer.allocateDirect(source.length);
            srcBuffRW.put(source,0,source.length);
            srcBuffRW.flip();

            ByteBuffer srcBuffRO = srcBuffRW.asReadOnlyBuffer();
            int compressedSize = qatSession.compress(srcBuffRO,destBuff);
            assertNotNull(destBuff);
            destBuff.flip();
            int decompressedSize = qatSession.decompress(destBuff,unCompBuff);
            assertNotNull(uncomp);
            unCompBuff.flip();
            unCompBuff.get(uncomp,0,decompressedSize);
            String str = new String(uncomp, StandardCharsets.UTF_8);

            assertEquals(str.equals(uncompressed), true);
            qatSession.teardown();
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }
    @Test
    public void testTeardown(){
        System.out.println("EXECUTING testTeardown..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            assertNotNull(qatSession.unCompressedBuffer);
            assertNotNull(qatSession.compressedBuffer);

            assertTrue(qatSession.unCompressedBuffer.isDirect());
            assertTrue(qatSession.unCompressedBuffer.isDirect());

            qatSession.teardown();

            assertTrue(true);
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        catch (Exception e){
            fail(e.getMessage());
        }
    }

    @Test
    public void testNativeByteBuffer(){
        System.out.println("EXECUTING testNativeByteBuffer..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            assertNotNull(qatSession.unCompressedBuffer);
            assertNotNull(qatSession.compressedBuffer);
            assertTrue(qatSession.unCompressedBuffer.isDirect());
            assertTrue(qatSession.unCompressedBuffer.isDirect());

            qatSession.teardown();
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        catch (Exception e){
            fail(e.getMessage());
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
    public void testParametrizedConstructorDefaultMode(){
        System.out.println("EXECUTING testParametrizedConstructorDefaultMode..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4,0);
            qatSession.teardown();
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
        System.out.println("EXECUTING testParametrizedConstructorDefaultMode successfully..");
        assertTrue(true);
    }

    @Test
    public void testParametrizedConstructorHardwareMode(){
        System.out.println("EXECUTING testParametrizedConstructorHardwareMode..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATSession.CompressionAlgorithm.LZ4,0, QATSession.Mode.HARDWARE);
            qatSession.teardown();
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
        System.out.println("EXECUTING testParametrizedConstructorHardwareMode successfully..");
        assertTrue(true);
    }

    @Test
    public void testIllegalStateException(){
        System.out.println("EXECUTING testIllegalStateException..");
        QATSession qatSession = null;
        String uncompressed = "lorem opsum lorem opsum opsum lorem";
        byte[] source = uncompressed.getBytes();
        byte[] uncomp = new byte[source.length];
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
        System.out.println("EXECUTING testIllegalStateException successfully..");
    }

    @Test
    public void testInvalidCompressionLevel(){
        System.out.println("EXECUTING testInvalidCompressionLevel..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 10, QATSession.Mode.AUTO,6);
            fail("testInvalidCompressionLevel failed");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }
        System.out.println("EXECUTING testInvalidCompressionLevel successfully..");
    }

    @Test
    public void testInvalidRetryCount(){
       System.out.println("EXECUTING testInvalidRetryCount..");
       QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE, 10, QATSession.Mode.AUTO,-1);
            fail("testInvalidRetryCount failed");
        }
        catch (IllegalArgumentException ie){
            assertTrue(true);
        }

    }
    /*
    private void doCompressDecompress(int thread) throws QATException{
        List<File> inFiles = new ArrayList<>();

        for(int i = filesPerThread * thread; i < filesPerThread * (thread + 1);i++){
            inFiles.add(filesList[i]);
        }

        if(thread == numberOfThreads - 1 && remainingFiles > 0){
            for(int j = filesList.length - remainingFiles; j < filesList.length; j++)
                inFiles.add(filesList[j]);
        }
        QATSession qatSession = null;
        try{
            try {
                qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE, 0, QATUtils.CompressionAlgo.DEFLATE, 6);
            }
            catch (QATException qe) {
                fail(qe.getMessage());
            }

            for(File file: inFiles){

                byte[] srcArray = new byte[0];
                try {
                    srcArray = Files.readAllBytes(file.toPath());
                } catch (IOException e) {
                    throw new QATException(e.getMessage());
                }

                // get max compressed Length
                int maxCompressedLength =
                        qatSession.maxCompressedLength(srcArray.length);

                byte[] destArray = new byte[maxCompressedLength];
                //source and destination byte buffer
                int compressedLength = qatSession.compressByteArray(srcArray,0,srcArray.length, destArray,0);

                if (compressedLength < 0) {
                    System.out.println("unsuccessful compression.. exiting");
                }

                int uncompressedLength2 = qatSession.decompressByteArray(destArray,0, compressedLength, srcArray,0);

                assertNotEquals(uncompressedLength2,0);
                assertEquals(uncompressedLength2, srcArray.length);

                byte[] arrtoWrite = new byte[uncompressedLength2];
                arrtoWrite = Arrays.copyOf(srcArray, uncompressedLength2);

                try (FileOutputStream fos = new FileOutputStream("../resources/res" + file.getName().replaceFirst("[.][^.]+$", "") +"-output.txt")) {
                    fos.write(arrtoWrite);
                }
                catch (Exception e){
                    fail("uncompressed data failed to write");
                }
            }

            qatSession.teardown();
        }
        catch(QATException e) {
            System.out.println("Runtime Exception message: " + e.getMessage());
        }

    }
*/
}
