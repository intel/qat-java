/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    //TODO what kind of test we need ?
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
/*
    @Test
    void testSimpleCompressionDecompression(){
        QATSession qatSession = null;
        try{
            qatSession = new QATSession();
            System.out.println("compressed buffer is " +qatSession.compressedBuffer.limit());

        String uncompressed = "lorem opsum lorem opsum opsum lorem";
        byte[] source = uncompressed.getBytes();
        byte[] uncomp = new byte[source.length];
        byte[] dest = new byte[qatSession.maxCompressedLength(source.length)];

            System.out.println("source length " + source.length);

        int compressedSize = qatSession.compressByteArray(source,0, source.length, dest,0);
        assertNotNull(dest);

        int decompressedSize = qatSession.decompressByteArray(dest,0, dest.length, uncomp, 0);
        assertNotNull(uncomp);
        assertEquals(uncomp.toString().equals(uncompressed), true);
        }
        catch (QATException ie){
            fail(ie.getMessage());
        }
    }
    */

    @Test
    public void testTeardown(){
        System.out.println("EXECUTING testTeardown..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
            assertNotNull(qatSession.unCompressedBuffer);
            assertNotNull(qatSession.compressedBuffer);
            System.out.println("Java test: Bytebuffer source is non-empty"+ qatSession.unCompressedBuffer.toString());
            System.out.println("Java test: Bytebuffer source is direct byte buffer "+ qatSession.unCompressedBuffer.isDirect());
            System.out.println("Java test: Bytebuffer dest is non-empty"+ qatSession.compressedBuffer.toString());
            System.out.println("Java test: Bytebuffer dest is direct byte buffer "+ qatSession.compressedBuffer.isDirect());

            assertTrue(qatSession.unCompressedBuffer.isDirect());
            assertTrue(qatSession.unCompressedBuffer.isDirect());

            qatSession.teardown();

            System.out.println("Java test: teardown succeeded");
            System.out.println("Java test: After Bytebuffer source is non-empty"+ qatSession.unCompressedBuffer.toString());
            System.out.println("Java test: Bytebuffer source is direct byte buffer "+ qatSession.unCompressedBuffer.isDirect());
            assertTrue(true);
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        catch (Exception e){
            System.out.println(e.getMessage());
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
            System.out.println("Java test: Bytebuffer source is non-empty"+ qatSession.unCompressedBuffer.toString());
            System.out.println("Java test: Bytebuffer source is direct byte buffer "+ qatSession.unCompressedBuffer.isDirect());
            System.out.println("Java test: Bytebuffer dest is non-empty"+ qatSession.compressedBuffer.toString());
            System.out.println("Java test: Bytebuffer dest is direct byte buffer "+ qatSession.compressedBuffer.isDirect());

            assertTrue(qatSession.unCompressedBuffer.isDirect());
            assertTrue(qatSession.unCompressedBuffer.isDirect());

            qatSession.teardown();
            System.out.println("Java test: teared down session");
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        catch (Exception e){
            fail(e.getMessage());
        }
        System.out.println("Java test: teardown succeeded");
        System.out.println("Java test: After Bytebuffer source is non-empty"+ qatSession.unCompressedBuffer.toString());
        assertTrue(true);
    }


/*
    
    @Test
    public void testDefaultConstructor(){
        System.out.println("EXECUTING testDefaultConstructor..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession();
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        qatSession.teardown();
        System.out.println("EXECUTING testDefaultConstructor successful..");
        assertTrue(true);

    }

    @Test
    public void testParametrizedConstructor(){
        System.out.println("EXECUTING testParametrizedConstructor..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE, 0, QATUtils.CompressionAlgo.DEFLATE, 6);
            qatSession.teardown();
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
        System.out.println("EXECUTING testParametrizedConstructor succesfull..");
        assertTrue(true);
    }
    @Test
    public void testHardwareSetupTearDown(){
        System.out.println("EXECUTING testHardwareSetupTearDown..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE,0, QATUtils.CompressionAlgo.DEFLATE,6);
        }
        catch (QATException qe){
            fail(qe.getMessage());
        }
        try {
            qatSession.teardown();
        } catch (QATException qe) {
            fail("QAT session teardown failed");
        }
        System.out.println("EXECUTING testHardwareSetupTearDown successful..");
        assertTrue(true);

    }

    @Test
    public void testNativeMemory(){
        System.out.println("EXECUTING testNativeMemory..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE,0, QATUtils.CompressionAlgo.DEFLATE,6);
        }
        catch (QATException qe){
            fail(qe.getMessage());
        }
        try {
            qatSession.teardown();
        } catch (QATException qe) {
            fail("QAT session teardown failed");
        }
        System.out.println("EXECUTING testNativeMemory successful..");
        assertTrue(true);

    }

    @Test
    public void testCompressionDecompression(){
        System.out.println("EXECUTING testCompressionDecompression..");
        for(int i = 0; i < numberOfThreads; i++){
            final int thread = i;

            threads[i] = new Thread(){
                @Override
                public void run(){
                    try{
                        doCompressDecompress(thread);
                    }
                    catch(QATException ie){
                        System.out.println("doCompressDecompress fails " + ie.getMessage());
                    }
                }
            };
        }
        System.out.println("EXECUTING testCompressionDecompression successful..");
        assertTrue(true);
    }

    @Test
    public void testSetupDuplicate(){
        System.out.println("EXECUTING testSetupDuplicate..");
        QATSession qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE,0, QATUtils.CompressionAlgo.DEFLATE,6);
        try {
            qatSession.setup();
        }
        catch (QATException qe){
            qatSession.teardown();
            System.out.println("EXECUTING testSetupDuplicate successful..");
            assertTrue(true);
        }
        fail("Session duplicate test got failed");
    }

    @Test
    public void testInvalidCompressionLevel(){
        System.out.println("EXECUTING testInvalidCompressionLevel..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE, 0, QATUtils.CompressionAlgo.DEFLATE, 10);
            fail("Invalid compression level test failed!");
        }
        catch (IllegalArgumentException ie){
            System.out.println("illegal argument exception");
            System.out.println("EXECUTING testInvalidCompressionLevel successful..");
            assertTrue(true);
        }
        catch (Exception e){
            System.out.println("general exception");
            assertTrue(true);
        }



    }

    @Test
    public void testInvalidRetryCount(){
        System.out.println("EXECUTING testInvalidRetryCount..");
        try {
            QATSession qatSession = new QATSession(QATUtils.ExecutionPaths.HARDWARE, 100, QATUtils.CompressionAlgo.DEFLATE, 6);
            fail("Invalid retry count test failed!");
        }
        catch (IllegalArgumentException ie){
            System.out.println("EXECUTING testInvalidRetryCount successful..");
            assertTrue(true);
        }

    }
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
