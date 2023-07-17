/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;
import com.intel.qat.QatSession;
import java.util.Arrays;
import java.util.Random;

public class TestByteArrayWithParams {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      QatSession qatSession = new QatSession();
      byte[] src = data.consumeRemainingAsBytes();
      int srcOffset = new Random().nextInt(src.length);
      int compressLength = qatSession.maxCompressedLength(src.length);
      byte[] dst = new byte[compressLength];
      byte[] decomp = new byte[src.length];

      int compressedSize = qatSession.compress(
          src, srcOffset, src.length - srcOffset, dst, 0, dst.length);
      int decompressedSize = qatSession.decompress(
          dst, 0, compressedSize, decomp, 0, decomp.length);

      qatSession.endSession();

      assert Arrays.equals(Arrays.copyOfRange(src, srcOffset, src.length),
          Arrays.copyOfRange(decomp, 0, decompressedSize))
          : "Source and decompressed array are not equal";
    } catch (QatException ignored) {
      final String expectedErrorMessage = "The source length is too large";
      if (!ignored.getMessage().equalsIgnoreCase(expectedErrorMessage)) {
        throw ignored;
      }
    }
  }
}
