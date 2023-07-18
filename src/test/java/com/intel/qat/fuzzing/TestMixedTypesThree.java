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

public class TestMixedTypesThree {
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
      ByteBuffer readonlyBuf = srcBuf.asReadOnlyBuffer();
      srcBuf.flip();

      ByteBuffer comBuf = ByteBuffer.allocate(comSize);
      qatSession.compress(readonlyBuf, comBuf);
      comBuf.flip();

      ByteBuffer decBuf = ByteBuffer.allocate(src.length);
      qatSession.decompress(comBuf, decBuf);

      qatSession.endSession();

      assert srcBuf.compareTo(decBuf) == 0 : "The source and decompressed buffers do not match.";
    } catch (QatException e) {
      throw e;
    }
  }
}
