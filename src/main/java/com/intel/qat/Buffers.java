/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.nio.ByteBuffer;

public class Buffers {
    ByteBuffer unCompressedBuffer;
    ByteBuffer compressedBuffer;

    long qzSession; // use long
}
