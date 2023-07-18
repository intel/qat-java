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

      int comLevel = new Random().nextInt(9) + 1;
      int retryCount = new Random().nextInt(20);

      byte[] src = data.consumeRemainingAsBytes();
      QatSession qatSession =
          new QatSession(QatSession.CompressionAlgorithm.DEFLATE, comLevel, QatSession.Mode.AUTO, retryCount);

      byte[] dst = new byte[qatSession.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int comSize = qatSession.compress(src, 0, src.length, dst, 0, dst.length);
      qatSession.decompress(dst, 0, comSize, dec, 0, dec.length);

      qatSession.endSession();

      assert Arrays.equals(src, dec) : "The source and decompressed arrays do not match.";
    } catch (Exception e) {
      throw e;
    }
  }
}
