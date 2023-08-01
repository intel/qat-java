/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QatException;

public class TestDriver {
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    try {
      TestByteArray.fuzzerTestOneInput(data);
      TestByteArrayWithParams.fuzzerTestOneInput(data);
      TestByteBuffer.fuzzerTestOneInput(data);
      TestDirectByteBuffer.fuzzerTestOneInput(data);
      TestMixedTypesOne.fuzzerTestOneInput(data);
      TestMixedTypesTwo.fuzzerTestOneInput(data);
      TestMixedTypesThree.fuzzerTestOneInput(data);
      TestWithCompressionLengthAndRetry.fuzzerTestOneInput(data);
    } catch (QatException e) {
      throw e;
    }
  }
}
