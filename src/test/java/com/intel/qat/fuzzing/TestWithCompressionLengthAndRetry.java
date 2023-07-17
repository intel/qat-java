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

public class TestWithCompressionLengthAndRetry {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      int compressionLevel = new Random().nextInt(9) + 1;
      int retryCount = new Random().nextInt(20);

      byte[] src = data.consumeRemainingAsBytes();
      QatSession qatSession =
          new QatSession(QatSession.CompressionAlgorithm.DEFLATE,
              compressionLevel, QatSession.Mode.AUTO, retryCount);

      int compressLength = qatSession.maxCompressedLength(src.length);
      byte[] dst = new byte[compressLength];
      byte[] decomp = new byte[src.length];

      int compressedSize =
          qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      int decompressedSize = qatSession.decompress(
          dst, 0, compressedSize, decomp, 0, decomp.length);

      qatSession.endSession();

      assert Arrays.equals(src, decomp)
          : "Source and decompressed array are not equal";
    } catch (Exception ignored) {
      final String expectedErrorMessage = "The source length is too large";
      if (!ignored.getMessage().equalsIgnoreCase(expectedErrorMessage)) {
        throw ignored;
      }
    }
  }
}
