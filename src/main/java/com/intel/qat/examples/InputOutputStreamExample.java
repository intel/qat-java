/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.examples;

import static com.intel.qat.QatZipper.Algorithm;

import com.intel.qat.QatCompressorOutputStream;
import com.intel.qat.QatDecompressorInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class InputOutputStreamExample {

  public static void main(String[] args) {
    try {
      byte[] dataToCompress = "The quick brown fox jumps over the lazy dog.".getBytes("UTF-8");

      ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
      QatCompressorOutputStream qatOutputStream =
          new QatCompressorOutputStream(compressedOutput, Algorithm.DEFLATE);
      qatOutputStream.write(dataToCompress);
      qatOutputStream.close();

      byte[] compressedData = compressedOutput.toByteArray();

      ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedData);
      QatDecompressorInputStream qatInputStream =
          new QatDecompressorInputStream(compressedInput, Algorithm.DEFLATE);
      ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();

      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = qatInputStream.read(buffer)) != -1) {
        decompressedOutput.write(buffer, 0, bytesRead);
      }
      qatInputStream.close();

      byte[] decompressedData = decompressedOutput.toByteArray();
      System.out.println("Decompressed data: " + new String(decompressedData, "UTF-8"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
