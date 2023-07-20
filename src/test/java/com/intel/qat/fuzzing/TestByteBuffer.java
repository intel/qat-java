/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;
import com.intel.qat.QatZip;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TestByteBuffer {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      byte[] src = data.consumeRemainingAsBytes();

      ByteBuffer srcBuf = ByteBuffer.allocate(src.length);
      srcBuf.put(src, 0, src.length);
      srcBuf.flip();

      QatZip qatSession = new QatZip();
      int compressedSize = qatSession.maxCompressedLength(src.length);

      assert compressedSize > 0;

      ByteBuffer comBuf = ByteBuffer.allocate(compressedSize);
      qatSession.compress(srcBuf, comBuf);
      comBuf.flip();

      ByteBuffer decBuf = ByteBuffer.allocate(src.length);
      qatSession.decompress(comBuf, decBuf);

      qatSession.end();

      assert srcBuf.compareTo(decBuf) == 0 : "The source and decompressed buffers do not match.";
    } catch (QatException e) {
      throw e;
    }
  }
}
