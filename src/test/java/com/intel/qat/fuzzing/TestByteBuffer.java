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
import java.util.Arrays;

public class TestByteBuffer {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      if (data.remainingBytes() == 0)
        return;

      byte[] srcData = data.consumeRemainingAsBytes();
      int n = srcData.length;

      ByteBuffer srcBB = ByteBuffer.allocate(n);
      srcBB.put(srcData, 0, n);
      srcBB.flip();

      QatSession qatSession = new QatSession();
      int compressedSize = qatSession.maxCompressedLength(n);

      assert compressedSize > 0;

      ByteBuffer compressedBB = ByteBuffer.allocate(compressedSize);
      qatSession.compress(srcBB, compressedBB);
      compressedBB.flip();

      ByteBuffer decompressedBB = ByteBuffer.allocate(n);
      qatSession.decompress(compressedBB, decompressedBB);

      qatSession.endSession();

      assert srcBB.compareTo(decompressedBB)
          == 0 : "Source and decompressed buffer are not equal";
    } catch (QatException ignored) {
      final String expectedErrorMessage = "The source length is too large";
      if (!ignored.getMessage().equalsIgnoreCase(expectedErrorMessage)) {
        throw ignored;
      }
    }
  }
}
