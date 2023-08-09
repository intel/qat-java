/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.examples;

import com.intel.qat.QatCompressOutputStream;
import com.intel.qat.QatDecompressInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class InputOutputStreamExample {
  private static final int BUFFER_SIZE = 1024;

  public static void main(String[] args) {
    try {
      byte[] dataToCompress = "The quick brown fox jumps over the lazy dog.".getBytes();

      ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
      QatCompressOutputStream qatOutputStream =
          new QatCompressOutputStream(compressedOutput, BUFFER_SIZE);
      qatOutputStream.write(dataToCompress);
      qatOutputStream.close();

      byte[] compressedData = compressedOutput.toByteArray();

      ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedData);
      QatDecompressInputStream qatInputStream =
          new QatDecompressInputStream(compressedInput, BUFFER_SIZE);
      ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();

      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = qatInputStream.read(buffer)) != -1) {
        decompressedOutput.write(buffer, 0, bytesRead);
      }
      qatInputStream.close();

      byte[] decompressedData = decompressedOutput.toByteArray();
      System.out.println("Decompressed data: " + new String(decompressedData));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
