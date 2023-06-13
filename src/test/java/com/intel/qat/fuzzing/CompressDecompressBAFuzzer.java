/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QATSession;
import com.intel.qat.QATException;
import java.util.Arrays;
public class CompressDecompressBAFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        try {
            QATSession qatSession = new QATSession();
            byte[] src = data.consumeRemainingAsBytes();
            int size = src.length;
            int compressLength = qatSession.maxCompressedLength(size);
            byte[] dst = new byte[compressLength];
            byte[] decompArr = new byte[src.length];

            int compressedSize = qatSession.compress(src,0,src.length,dst,0);
            int decompressedSize = qatSession.decompress(dst,0, compressedSize, decompArr,0);

            assert Arrays.equals(src, decompArr): "Source and decompressed array are not equal";
        }
        catch (ArrayIndexOutOfBoundsException | IllegalArgumentException ignored) {
        } catch (QATException ignored) {
            final String expectedErrorMessage = "The source length is too large";
            if (!ignored.getMessage().equalsIgnoreCase(expectedErrorMessage)) {
                throw ignored;
            }
        }
    }
}
