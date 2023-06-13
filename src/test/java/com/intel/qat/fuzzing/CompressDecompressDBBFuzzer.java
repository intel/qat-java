package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QATException;
import com.intel.qat.QATSession;

import java.nio.ByteBuffer;

public class CompressDecompressDBBFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        try {
            byte[] srcData = data.consumeRemainingAsBytes();
            int n = srcData.length;
            if (n <= 0) {
                return;
            }
            ByteBuffer srcBB = ByteBuffer.allocateDirect(n);
            srcBB.put(srcData, 0, n);
            srcBB.flip();

            QATSession qatSession = new QATSession();
            int compressedSize = qatSession.maxCompressedLength(n);

            assert compressedSize > 0;

            ByteBuffer compressedBB = ByteBuffer.allocateDirect(compressedSize);
            qatSession.compress(srcBB, compressedBB);
            ByteBuffer decompressedBB = ByteBuffer.allocateDirect(n);
            decompressedBB.flip();
            qatSession.decompress(compressedBB, decompressedBB);

            assert srcBB.compareTo(decompressedBB) == 0 : "Source and decompressed buffer are not equal";
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException ignored) {
        } catch (QATException ignored) {
            final String expectedErrorMessage = "The source length is too large";
            if (!ignored.getMessage().equalsIgnoreCase(expectedErrorMessage)) {
                throw ignored;
            }
        }
    }
}
