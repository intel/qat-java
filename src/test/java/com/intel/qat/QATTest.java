package com.intel.qat;

import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

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
    public void testHardwareSetupTearDown(){
        QATSession qatSession = new QATSession(1000,QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, false,0);
        try {
            qatSession.setup();
        }
        catch (QATException qe){
            fail(qe.getMessage());
            return;
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
        QATSession qatSession = new QATSession(1000,QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, false,0);
        try {
            qatSession.setup();
        }
        catch (QATException qe){
            fail(qe.getMessage());
            return;
        }
        assertEquals(qatSession.unCompressedBuffer.isDirect(), true);
        assertEquals(qatSession.compressedBuffer.isDirect(), true);

        try {
            qatSession.teardown();
        } catch (QATException qe) {
            fail(qe.getMessage());
            return;
        }
        assertTrue(true);

    }

    @Test
    public void testCompressionDecompression(){
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
        QATSession qatSession = new QATSession(1000,QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, true,10);
        try {
            qatSession.setup();
            qatSession.setup();
        }
        catch (QATException qe){
            assertEquals(1, QATUtils.getErrorMessage(Integer.parseInt(qe.getMessage())));
        }
        fail("Session duplicate test got failed");
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
        QATSession qatSession = new QATSession(1000,QATUtils.ExecutionPaths.QAT_HARDWARE_ONLY, true,10);
        try{
            // init session with QAT hardware
            try {
                qatSession.setup();
            }
            catch (QATException qe){
                System.out.println("setup session failed");
                return;
            }

            for(File file: inFiles){
                qatSession.unCompressedBuffer.clear();
                qatSession.compressedBuffer.clear();

                byte[] srcArray = new byte[0];
                try {
                    srcArray = Files.readAllBytes(file.toPath());
                } catch (IOException e) {
                    throw new QATException(e.getMessage());
                }

                // get max compressed Length
                int maxCompressedLength =
                        qatSession.maxCompressedLength(srcArray.length);

                //source and destination byte buffer

                if(!qatSession.unCompressedBuffer.isDirect() || !qatSession.compressedBuffer.isDirect()){
                    System.out.println("ERROR: src or dest byte buffer is not direct\n");
                    return;
                }
                qatSession.unCompressedBuffer.put(srcArray);
                qatSession.compressedBuffer.flip();

                int compressedLength = qatSession.compressByteBuff(qatSession.unCompressedBuffer,0,
                        qatSession.unCompressedBuffer.limit(),
                        qatSession.compressedBuffer);

                if (compressedLength < 0) {
                    System.out.println("unsuccessful compression.. exiting");
                }

                qatSession.compressedBuffer.flip();
                qatSession.unCompressedBuffer.clear();
                int uncompressedLength2 = qatSession.decompressByteBuff(qatSession.compressedBuffer,0,
                        compressedLength, qatSession.unCompressedBuffer);

                assertNotEquals(uncompressedLength2,0);
                assertEquals(uncompressedLength2, srcArray.length);


                byte[] arrtoWrite = new byte[uncompressedLength2];
                qatSession.unCompressedBuffer.get(arrtoWrite,0,uncompressedLength2);
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
