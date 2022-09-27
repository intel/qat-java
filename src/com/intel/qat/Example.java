package com.intel.qat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Example {
  public static void main(String[] args)
      throws IOException { 
    if(args.length != 3)
        throw new IllegalArgumentException();
    
    File filePath = new File(args[0]);
    File filesList[] = filePath.listFiles();

    System.out.println("--------------------------------");
    int numberOfThreads = Integer.parseInt(args[2]);
    Thread[] threads = new Thread[numberOfThreads];

    final int filesPerThread = filesList.length / numberOfThreads;
    final int remainingFiles = filesList.length % numberOfThreads;
   
    for(int i = 0; i < numberOfThreads; i++){
      final int thread = i;

      threads[i] = new Thread(){
        @Override
        public void run(){
          try{
            doCompressDecompress(filesList, numberOfThreads, thread, filesPerThread, remainingFiles, args[1]);
          }
          catch(IOException ie){
            System.out.println("doCompressDecompress fails " + ie.getMessage());
          }
        }
      };
    }

    for(Thread t: threads){
      t.start();
    }
    for(Thread th: threads){
        try {
            th.join();
        }
        catch (InterruptedException ie){System.out.println(ie.getMessage());}
    }
    System.out.println("Please find so file in target/sharedobject and jar file in target/jar");
  }

  private static  void doCompressDecompress(File[] filesList, int numberOfThreads, int thread, int filesPerThread, int remainingFiles, String destination) throws IOException{
    List<File> inFiles = new ArrayList<>();

    for(int i = filesPerThread * thread; i < filesPerThread * (thread + 1);i++){
        inFiles.add(filesList[i]);
    }

    if(thread == numberOfThreads - 1 && remainingFiles > 0){
        for(int j = filesList.length - remainingFiles; j < filesList.length; j++)
            inFiles.add(filesList[j]);
    }
    CompressorDecompressor compressorDecompressor = new CompressorDecompressor();
    try{
    // init session with QAT hardware
    compressorDecompressor.setup();
    ByteBuffer srcBuff = (ByteBuffer)compressorDecompressor.nativeByteBuff(1000);
    ByteBuffer destBuff = (ByteBuffer)compressorDecompressor.nativeByteBuff(1000);

    for(File file: inFiles){
      
      srcBuff.clear();
      destBuff.clear();

      byte[] srcArray = Files.readAllBytes(file.toPath());

      // get max compressed Length
      int maxCompressedLength =
        compressorDecompressor.maxCompressedLength(srcArray.length);

      //source and destination byte buffer

      if(!srcBuff.isDirect() || !destBuff.isDirect()){
        System.out.println("ERROR: src or dest byte buffer is not direct\n");
        return;
      }
      srcBuff.put(srcArray);
      srcBuff.flip();
      
	int compressedLength = compressorDecompressor.compressByteBuff(srcBuff,0, srcBuff.limit(), destBuff);

      if (compressedLength < 0) {
        System.out.println("unsuccessful compression.. exiting");
      }

      destBuff.flip();
      srcBuff.clear();
      int uncompressedLength2 = compressorDecompressor.decompressByteBuff(destBuff,0, compressedLength, srcBuff);
      
      if (uncompressedLength2 <= 0 || uncompressedLength2 != srcArray.length) {
        System.out.println("unsuccessful decompression.. exiting");
        return;
      }
        

      byte[] arrtoWrite = new byte[uncompressedLength2];
      srcBuff.get(arrtoWrite,0,uncompressedLength2);
      try (FileOutputStream fos = new FileOutputStream(destination + file.getName().replaceFirst("[.][^.]+$", "") +"-output.txt")) {
        fos.write(arrtoWrite);
      }      
      
    }
    compressorDecompressor.freeNativeByteBuff(srcBuff);
    compressorDecompressor.freeNativeByteBuff(destBuff);
    compressorDecompressor.teardown();
   }
   catch(RuntimeException re){
     System.out.println("Runtime Exception message: "+ re.getMessage());
   }
   catch(IOException io){
    System.out.println("File IO Exception message: "+ io.getMessage());
   }
   catch(Exception e){
    System.out.println("Exception message: "+ e.getMessage());
   }

  }
}
