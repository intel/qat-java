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

      QatZipper zipper = new QatZipper();

      // Create a buffer with enough size for compression
      byte[] compressedData = new byte[zipper.maxCompressedLength(input.length)];

      // Compress the bytes
      int compressedSize = zipper.compress(input, compressedData);

      // Decompress the bytes into a String
      byte[] decompressedData = new byte[input.length];
      int decompressedSize = zipper.decompress(compressedData, decompressedData);

      // Release resources
      zipper.end();

      // Convert the bytes into a String
      String outputStr = new String(decompressedData, 0, decompressedSize);
      System.out.println("Decompressed data: " + outputStr);
    } catch (QatException e) {

    }
  }
}
