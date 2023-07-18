/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;
import com.intel.qat.QatSession;
import java.nio.ByteBuffer;

public class TestMixedTypesOne {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      byte[] src = data.consumeRemainingAsBytes();

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      QatSession qatSession = new QatSession();
      int comSize = qatSession.maxCompressedLength(src.length);

      assert comSize > 0;

      ByteBuffer comBuf = ByteBuffer.allocateDirect(comSize);
      qatSession.compress(srcBuf, comBuf);
      comBuf.flip();

      ByteBuffer decBuf = ByteBuffer.allocateDirect(src.length);
      qatSession.decompress(comBuf, decBuf);

      qatSession.endSession();

      assert srcBuf.compareTo(decBuf)
          == 0 : "Source and decompressed buffer do not match.";
    } catch (QatException e) {
      throw e;
    }
  }
}
