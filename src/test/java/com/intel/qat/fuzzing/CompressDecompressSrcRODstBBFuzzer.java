package com.intel.qat.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.intel.qat.QATException;
import com.intel.qat.QATSession;

import java.nio.ByteBuffer;

public class CompressDecompressSrcRODstBBFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        try {
            byte[] srcData = data.consumeRemainingAsBytes();
            int n = srcData.length;
            if (n <= 0) {
                return;
            }
            ByteBuffer srcBB = ByteBuffer.allocate(n);
            srcBB.put(srcData, 0, n);
            srcBB.flip();

            QATSession qatSession = new QATSession();
            int compressedSize = qatSession.maxCompressedLength(n);

            assert compressedSize > 0;
            ByteBuffer srcBBRO = srcBB.asReadOnlyBuffer();
            ByteBuffer compressedBB = ByteBuffer.allocate(compressedSize);
            qatSession.compress(srcBBRO, compressedBB);
            ByteBuffer decompressedBB = ByteBuffer.allocate(n);
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
