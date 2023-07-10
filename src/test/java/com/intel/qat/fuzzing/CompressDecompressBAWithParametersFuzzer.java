package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QATException;
import com.intel.qat.QATSession;

import java.util.Arrays;

public class CompressDecompressBAWithParametersFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        try {
            QATSession qatSession = new QATSession();
            int srcOffset = data.consumeInt();
            int srcLength = data.consumeInt();
            byte[] src = data.consumeRemainingAsBytes();
            int size = src.length;
            int compressLength = qatSession.maxCompressedLength(size);
            byte[] dst = new byte[compressLength];
            byte[] decompArr = new byte[src.length];

            int compressedSize = qatSession.compress(src,srcOffset,src.length,dst,0,dst.length);
            int decompressedSize = qatSession.decompress(dst,0, compressedSize, decompArr,0,decompArr.length);

            assert Arrays.equals(src, Arrays.copyOfRange(decompArr,0,decompressedSize)): "Source and decompressed array are not equal";
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
