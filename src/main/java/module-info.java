/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

/**
 * Defines APIs for data compression using Intel&reg; QuickAssit Technology.
 *
 * <p>The implementation uses the <a href="https://github.com/intel/QATzip">QATZip</a> library
 * through JNI bindings.
 */
module com.intel.qat {
  requires com.github.luben.zstd_jni;

  exports com.intel.qat;
}
