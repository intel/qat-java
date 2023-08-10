/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.examples;

import com.intel.qat.QatException;
import com.intel.qat.QatZipper;

public class ByteArrayExample {

  public static void main(String[] args) {
    try {
      String inputStr = "The quick brown fox jumps over the lazy dog.";
      byte[] input = inputStr.getBytes();

      QatZipper qzip = new QatZipper();

      // Create a buffer with enough size for compression
      byte[] compressedData = new byte[qzip.maxCompressedLength(input.length)];

      // Compress the bytes
      int compressedSize = qzip.compress(input, compressedData);

      // Decompress the bytes into a String
      byte[] decompressedData = new byte[input.length];
      int decompressedSize = qzip.decompress(compressedData, decompressedData);

      // Release resources
      qzip.end();

      // Convert the bytes into a String
      String outputStr = new String(decompressedData, 0, decompressedSize);
      System.out.println("Decompressed data: " + outputStr);
    } catch (QatException e) {
      e.printStackTrace();
    }
  }
}
