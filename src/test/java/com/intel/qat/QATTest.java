/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;
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

    @Test
    public void testDefaultConstructor(){
        System.out.println("EXECUTING testDefaultConstructor..");
        try {
            QATSession qatSession = new QATSession();
        }
        catch (IllegalArgumentException | QATException ie){
            fail(ie.getMessage());
        }
        assertTrue(true);
    }

    @Test
    public void testParametrizedConstructor(){
        System.out.println("EXECUTING testParametrizedConstructor..");
        try {
            QATSession qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, 0, QATUtils.CompressionAlgo.DEFLATE, 6);
        }
        catch (IllegalArgumentException| QATException ie){
            fail(ie.getMessage());
        }
        assertTrue(true);
    }
    public void testHardwareSetupTearDown(){
        System.out.println("EXECUTING testHardwareSetupTearDown..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY,0, QATUtils.CompressionAlgo.DEFLATE,6);
        }
        catch (QATException qe){
            fail(qe.getMessage());
        }
        try {
            qatSession.teardown();
        } catch (QATException qe) {
            fail("QAT session teardown failed");
            return;
        }
        assertTrue(true);

    }

    @Test
    public void testNativeMemory(){
        System.out.println("EXECUTING testNativeMemory..");
        QATSession qatSession = null;
        try {
            qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY,0, QATUtils.CompressionAlgo.DEFLATE,6);
        }
        catch (QATException qe){
            fail(qe.getMessage());
        }
        try {
            qatSession.teardown();
        } catch (QATException qe) {
            fail("QAT session teardown failed");
            return;
        }
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
    }

    @Test
    public void testSetupDuplicate(){
        System.out.println("EXECUTING testSetupDuplicate..");
        QATSession qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY,0, QATUtils.CompressionAlgo.DEFLATE,6);
        try {
            qatSession.setup();
        }
        catch (QATException qe){
            assertTrue(true);
        }
        fail("Session duplicate test got failed");
    }

    @Test
    public void testInvalidCompressionLevel(){
        System.out.println("EXECUTING testInvalidCompressionLevel..");
        try {
            QATSession qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, 0, QATUtils.CompressionAlgo.DEFLATE, 10);
            fail("Invalid compression level test failed!");
        }
        catch (IllegalArgumentException ie){
            System.out.println("illegal argument exception");
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
            QATSession qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, 100, QATUtils.CompressionAlgo.DEFLATE, 6);
            fail("Invalid retry count test failed!");
        }
        catch (IllegalArgumentException ie){
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
                qatSession = new QATSession(1000, QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, 0, QATUtils.CompressionAlgo.DEFLATE, 6);
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
                int compressedLength = qatSession.compressByteArray(srcArray,0,srcArray.length, destArray);

                if (compressedLength < 0) {
                    System.out.println("unsuccessful compression.. exiting");
                }

                int uncompressedLength2 = qatSession.decompressByteArray(destArray,0, compressedLength, srcArray);

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

}
