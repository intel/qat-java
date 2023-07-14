/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("com.intel.qat")
public class QATTestSuite {
  private static String flag = System.getProperty("hardware.available");
  public static final boolean FORCE_HARDWARE =
      (flag != null && flag.equals("true"));
}
