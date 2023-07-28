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

public class TestByteArrayWithParams {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      QatZipper zipper = new QatZipper();

      byte[] src = data.consumeRemainingAsBytes();
      byte[] dst = new byte[zipper.maxCompressedLength(src.length)];
      byte[] dec = new byte[src.length];

      int srcOffset = new Random().nextInt(src.length);
      int compressedSize = zipper.compress(
          src, srcOffset, src.length - srcOffset, dst, 0, dst.length);
      int decompressedSize =
          zipper.decompress(dst, 0, compressedSize, dec, 0, dec.length);

      zipper.end();

      assert Arrays.equals(Arrays.copyOfRange(src, srcOffset, src.length),
          Arrays.copyOfRange(dec, 0, decompressedSize))
          : "The source and decompressed arrays do not match.";
    } catch (QatException e) {
      throw e;
    }
  }
}
