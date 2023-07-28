/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;
import com.intel.qat.QatZipper;
import java.nio.ByteBuffer;

public class TestMixedTypesTwo {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      byte[] src = data.consumeRemainingAsBytes();

      ByteBuffer srcBuf = ByteBuffer.allocateDirect(src.length);
      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      QatZipper zipper = new QatZipper();
      int compressedSize = zipper.maxCompressedLength(src.length);

      assert compressedSize > 0;

      ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
      zipper.compress(srcBuf, comBuf);
      comBuf.flip();

      ByteBuffer decBuf = ByteBuffer.allocate(src.length);
      zipper.decompress(comBuf, decBuf);

      zipper.end();

      assert srcBuf.compareTo(decBuf)
          == 0 : "The source and decompressed buffers do not match.";
    } catch (QatException e) {
      throw e;
    }
  }
}
