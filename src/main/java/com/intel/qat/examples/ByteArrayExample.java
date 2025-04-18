/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.examples;

import com.intel.qat.QatZipper;

public class ByteArrayExample {

  public static void main(String[] args) {
    try {
      String inputStr = "The quick brown fox jumps over the lazy dog.";
      byte[] input = inputStr.getBytes("UTF-8");

      QatZipper qzip = new QatZipper.Builder().build();

      // Create a buffer with enough size for compression
      byte[] compressedData = new byte[qzip.maxCompressedLength(input.length)];

      // Compress the bytes
      int clen = qzip.compress(input, compressedData);

      // Decompress the bytes into a String
      byte[] decompressedData = new byte[input.length];
      int decompressedLength =
          qzip.decompress(compressedData, 0, clen, decompressedData, 0, decompressedData.length);

      // Release resources
      qzip.end();

      // Convert the bytes into a String
      String outputStr = new String(decompressedData, 0, decompressedLength, "UTF-8");
      System.out.println("Decompressed data: " + outputStr);
    } catch (java.io.UnsupportedEncodingException | RuntimeException e) {
      e.printStackTrace();
    }
  }
}
