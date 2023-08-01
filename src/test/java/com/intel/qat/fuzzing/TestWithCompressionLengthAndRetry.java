/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;
import com.intel.qat.QatZipper;
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
      QatZipper zipper = new QatZipper(QatZipper.Algorithm.DEFLATE, comLevel,
          QatZipper.Mode.AUTO, retryCount);

      byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int compressedSize =
          zipper.compress(src, 0, src.length, dst, 0, dst.length);
      zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);

      zipper.end();

      assert Arrays.equals(src, dec)
          : "The source and decompressed arrays do not match.";
    } catch (Exception e) {
      throw e;
    }
  }
}
