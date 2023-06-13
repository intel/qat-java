package com.intel.qat.fuzzing;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QATException;
import com.intel.qat.QATSession;

import java.util.Arrays;

public class CompressDifferentCLAndRCFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        try {
            int compressionLevel = data.consumeInt();
            int retryCount = data.consumeInt();
            byte[] src = data.consumeRemainingAsBytes();
            int size = src.length;
            QATSession qatSession = new QATSession(QATSession.CompressionAlgorithm.DEFLATE,compressionLevel, QATSession.Mode.AUTO,retryCount);
            int compressLength = qatSession.maxCompressedLength(size);
            byte[] dst = new byte[compressLength];
            byte[] decompArr = new byte[src.length];

            int compressedSize = qatSession.compress(src,0,src.length,dst,0);
            int decompressedSize = qatSession.decompress(dst,0, compressedSize, decompArr,0);

            assert Arrays.equals(src, decompArr): "Source and decompressed array are not equal";
        }
        catch (ArrayIndexOutOfBoundsException | IllegalArgumentException|QATException ignored) {
        } catch (Exception ignored) {
            final String expectedErrorMessage = "The source length is too large";
            if (!ignored.getMessage().equalsIgnoreCase(expectedErrorMessage)) {
                throw ignored;
            }
        }
    }
}
