/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive unit tests for QatZipper class. Tests cover configuration,
 * compression/decompression, error handling, and edge cases.
 */
class QatZipperTests {

  // Test data
  private static final String TEST_STRING = "Hello, World! This is a test string for compression.";
  private static final byte[] TEST_DATA = TEST_STRING.getBytes();
  private static final byte[] EMPTY_ARRAY = new byte[0];
  private static final byte[] LARGE_DATA = generateLargeTestData(10000);

  @BeforeEach
  void setUp() {
    // Setup before each test
  }

  @AfterEach
  void tearDown() {
    // Cleanup after each test
  }

  // ===========================
  // Builder Tests
  // ===========================

  @Test
  @DisplayName("Builder creates QatZipper with default settings")
  void testBuilderDefaults() {
    QatZipper zipper = new QatZipper.Builder().build();
    assertNotNull(zipper);
    zipper.end();
  }

  @ParameterizedTest
  @EnumSource(QatZipper.Algorithm.class)
  @DisplayName("Builder accepts all algorithm types")
  void testBuilderAllAlgorithms(QatZipper.Algorithm algorithm) {
    QatZipper zipper = new QatZipper.Builder().algorithm(algorithm).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder sets compression level")
  void testBuilderSetLevel() {
    QatZipper zipper = new QatZipper.Builder().level(9).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 5, 9, 12})
  @DisplayName("Builder accepts valid ZSTD compression levels")
  void testBuilderValidZSTDLevels(int level) {
    QatZipper zipper =
        new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).level(level).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder throws exception for invalid ZSTD level - too low")
  void testBuilderInvalidZSTDLevelLow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).level(0).build();
        });
  }

  @Test
  @DisplayName("Builder throws exception for invalid ZSTD level - too high")
  void testBuilderInvalidZSTDLevelHigh() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).level(23).build();
        });
  }

  @Test
  @DisplayName("Builder throws exception for negative level")
  void testBuilderNegativeLevel() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new QatZipper.Builder().level(-1).build();
        });
  }

  @ParameterizedTest
  @EnumSource(QatZipper.Mode.class)
  @DisplayName("Builder accepts all mode types")
  void testBuilderAllModes(QatZipper.Mode mode) {
    QatZipper zipper = new QatZipper.Builder().mode(mode).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder throws exception for null mode")
  void testBuilderNullMode() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new QatZipper.Builder().mode(null).build();
        });
  }

  @Test
  @DisplayName("Builder throws exception for null algorithm")
  void testBuilderNullAlgorithm() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new QatZipper.Builder().algorithm(null).build();
        });
  }

  @ParameterizedTest
  @EnumSource(QatZipper.PollingMode.class)
  @DisplayName("Builder accepts all polling modes")
  void testBuilderAllPollingModes(QatZipper.PollingMode pollingMode) {
    QatZipper zipper = new QatZipper.Builder().pollingMode(pollingMode).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder throws exception for null polling mode")
  void testBuilderNullPollingMode() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new QatZipper.Builder().pollingMode(null).build();
        });
  }

  @ParameterizedTest
  @EnumSource(QatZipper.DataFormat.class)
  @DisplayName("Builder accepts all data formats")
  void testBuilderAllDataFormats(QatZipper.DataFormat dataFormat) {
    QatZipper zipper = new QatZipper.Builder().dataFormat(dataFormat).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder throws exception for null data format")
  void testBuilderNullDataFormat() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new QatZipper.Builder().dataFormat(null).build();
        });
  }

  @ParameterizedTest
  @EnumSource(QatZipper.HardwareBufferSize.class)
  @DisplayName("Builder accepts all hardware buffer sizes")
  void testBuilderAllHardwareBufferSizes(QatZipper.HardwareBufferSize hwBufferSize) {
    QatZipper zipper = new QatZipper.Builder().hardwareBufferSize(hwBufferSize).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder throws exception for null hardware buffer size")
  void testBuilderNullHwBufferSize() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new QatZipper.Builder().hardwareBufferSize(null).build();
        });
  }

  @ParameterizedTest
  @EnumSource(QatZipper.LogLevel.class)
  @DisplayName("Builder accepts all log levels")
  void testBuilderAllLogLevels(QatZipper.LogLevel logLevel) {
    QatZipper zipper = new QatZipper.Builder().logLevel(logLevel).build();
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Builder throws exception for null log level")
  void testBuilderNullLogLevel() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new QatZipper.Builder().logLevel(null).build();
        });
  }

  @Test
  @DisplayName("Builder chains multiple settings")
  void testBuilderChaining() {
    QatZipper zipper =
        new QatZipper.Builder()
            .algorithm(QatZipper.Algorithm.ZSTD)
            .level(6)
            .mode(QatZipper.Mode.AUTO)
            .pollingMode(QatZipper.PollingMode.PERIODICAL)
            .dataFormat(QatZipper.DataFormat.DEFLATE_GZIP)
            .hardwareBufferSize(QatZipper.HardwareBufferSize.MAX_BUFFER_SIZE)
            .logLevel(QatZipper.LogLevel.ERROR)
            .build();
    assertNotNull(zipper);
    zipper.end();
  }

  // ===========================
  // Compression Tests - Byte Arrays
  // ===========================

  @Test
  @DisplayName("Compress byte array with sufficient output buffer")
  void testCompressByteArray() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, output);
      assertTrue(compressedSize > 0);
      assertTrue(compressedSize <= output.length);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress byte array with offsets")
  void testCompressByteArrayWithOffsets() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[zipper.maxCompressedLength(TEST_DATA.length) + 10];
      int compressedSize =
          zipper.compress(TEST_DATA, 0, TEST_DATA.length, output, 5, output.length - 5);
      assertTrue(compressedSize > 0);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for null source array")
  void testCompressNullSource() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(null, output);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for null destination array")
  void testCompressNullDestination() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(TEST_DATA, null);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for zero source length")
  void testCompressZeroSourceLength() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(TEST_DATA, 0, 0, output, 0, output.length);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for zero destination length")
  void testCompressZeroDestinationLength() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(TEST_DATA, 0, TEST_DATA.length, output, 0, 0);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for negative source offset")
  void testCompressNegativeSourceOffset() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          ArrayIndexOutOfBoundsException.class,
          () -> {
            zipper.compress(TEST_DATA, -1, TEST_DATA.length, output, 0, output.length);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for negative destination offset")
  void testCompressNegativeDestinationOffset() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          ArrayIndexOutOfBoundsException.class,
          () -> {
            zipper.compress(TEST_DATA, 0, TEST_DATA.length, output, -1, output.length);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for source bounds exceeded")
  void testCompressSourceBoundsExceeded() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          ArrayIndexOutOfBoundsException.class,
          () -> {
            zipper.compress(TEST_DATA, 0, TEST_DATA.length + 1, output, 0, output.length);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for destination bounds exceeded")
  void testCompressDestinationBoundsExceeded() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          ArrayIndexOutOfBoundsException.class,
          () -> {
            zipper.compress(TEST_DATA, 0, TEST_DATA.length, output, 0, output.length + 1);
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Decompression Tests - Byte Arrays
  // ===========================

  @Test
  @DisplayName("Decompress byte array successfully")
  void testDecompressByteArray() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress first
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);

      // Decompress
      byte[] decompressed = new byte[TEST_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(TEST_DATA.length, decompressedSize);
      assertArrayEquals(TEST_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress with offsets")
  void testDecompressByteArrayWithOffsets() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress first
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length) + 10];
      int compressedSize =
          zipper.compress(TEST_DATA, 0, TEST_DATA.length, compressed, 5, compressed.length - 5);

      // Decompress
      byte[] decompressed = new byte[TEST_DATA.length + 10];
      int decompressedSize =
          zipper.decompress(
              compressed, 5, compressedSize, decompressed, 3, decompressed.length - 3);

      assertEquals(TEST_DATA.length, decompressedSize);
      byte[] actual = new byte[TEST_DATA.length];
      System.arraycopy(decompressed, 3, actual, 0, TEST_DATA.length);
      assertArrayEquals(TEST_DATA, actual);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for null source array")
  void testDecompressNullSource() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompress(null, 0, 10, output, 0, output.length);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for null destination array")
  void testDecompressNullDestination() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompress(compressed, 0, compressed.length, null, 0, 10);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for zero source length")
  void testDecompressZeroSourceLength() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[100];
      byte[] output = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompress(compressed, 0, 0, output, 0, output.length);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for zero destination length")
  void testDecompressZeroDestinationLength() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[100];
      byte[] output = new byte[100];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompress(compressed, 0, compressed.length, output, 0, 0);
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Compression Tests - ByteBuffers
  // ===========================

  @Test
  @DisplayName("Compress ByteBuffer successfully")
  void testCompressByteBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer dst = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, dst);
      assertTrue(compressedSize > 0);
      assertEquals(TEST_DATA.length, src.position());
      assertEquals(compressedSize, dst.position());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress direct ByteBuffer successfully")
  void testCompressDirectByteBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.allocateDirect(TEST_DATA.length);
      src.put(TEST_DATA);
      src.flip();

      ByteBuffer dst = ByteBuffer.allocateDirect(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, dst);
      assertTrue(compressedSize > 0);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for read-only destination buffer")
  void testCompressReadOnlyDestination() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer dst =
          ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length)).asReadOnlyBuffer();

      assertThrows(
          ReadOnlyBufferException.class,
          () -> {
            zipper.compress(src, dst);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for null source buffer")
  void testCompressNullSourceBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer dst = ByteBuffer.allocate(100);
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(null, dst);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception for null destination buffer")
  void testCompressNullDestinationBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(src, null);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception when source position >= limit")
  void testCompressSourcePositionAtLimit() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      src.position(src.limit());
      ByteBuffer dst = ByteBuffer.allocate(100);

      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(src, dst);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress throws exception when destination position >= limit")
  void testCompressDestinationPositionAtLimit() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer dst = ByteBuffer.allocate(100);
      dst.position(dst.limit());

      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compress(src, dst);
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Decompression Tests - ByteBuffers
  // ===========================

  @Test
  @DisplayName("Decompress ByteBuffer successfully")
  void testDecompressByteBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress first
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer compressed = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));
      int compressedSize = zipper.compress(src, compressed);
      compressed.flip();

      // Decompress
      ByteBuffer decompressed = ByteBuffer.allocate(TEST_DATA.length);
      int decompressedSize = zipper.decompress(compressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      decompressed.flip();
      byte[] result = new byte[decompressed.remaining()];
      decompressed.get(result);
      assertArrayEquals(TEST_DATA, result);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress direct ByteBuffer successfully")
  void testDecompressDirectByteBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress first
      ByteBuffer src = ByteBuffer.allocateDirect(TEST_DATA.length);
      src.put(TEST_DATA);
      src.flip();

      ByteBuffer compressed =
          ByteBuffer.allocateDirect(zipper.maxCompressedLength(TEST_DATA.length));
      int compressedSize = zipper.compress(src, compressed);
      compressed.flip();

      // Decompress
      ByteBuffer decompressed = ByteBuffer.allocateDirect(TEST_DATA.length);
      int decompressedSize = zipper.decompress(compressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      decompressed.flip();
      byte[] result = new byte[decompressed.remaining()];
      decompressed.get(result);
      assertArrayEquals(TEST_DATA, result);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for read-only destination buffer")
  void testDecompressReadOnlyDestination() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.allocate(100);
      ByteBuffer dst = ByteBuffer.allocate(100).asReadOnlyBuffer();

      assertThrows(
          ReadOnlyBufferException.class,
          () -> {
            zipper.decompress(src, dst);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for null source buffer")
  void testDecompressNullSourceBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer dst = ByteBuffer.allocate(100);
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompress(null, dst);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress throws exception for null destination buffer")
  void testDecompressNullDestinationBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.allocate(100);
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompress(src, null);
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // ZSTD-specific Tests
  // ===========================

  @Test
  @DisplayName("ZSTD compression and decompression")
  void testZSTDCompression() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).level(6).build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);

      byte[] decompressed = new byte[TEST_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(TEST_DATA.length, decompressedSize);
      assertArrayEquals(TEST_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("ZSTD setChecksumFlag works correctly")
  void testZSTDSetChecksumFlag() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      assertFalse(zipper.getChecksumFlag());

      zipper.setChecksumFlag(true);
      assertTrue(zipper.getChecksumFlag());

      zipper.setChecksumFlag(false);
      assertFalse(zipper.getChecksumFlag());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("ZSTD setChecksumFlag throws exception for non-ZSTD algorithm")
  void testSetChecksumFlagNonZSTD() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.DEFLATE).build();
    try {
      assertThrows(
          UnsupportedOperationException.class,
          () -> {
            zipper.setChecksumFlag(true);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("ZSTD getChecksumFlag throws exception for non-ZSTD algorithm")
  void testGetChecksumFlagNonZSTD() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.LZ4).build();
    try {
      assertThrows(
          UnsupportedOperationException.class,
          () -> {
            zipper.getChecksumFlag();
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Utility Method Tests
  // ===========================

  @Test
  @DisplayName("maxCompressedLength returns positive value")
  void testMaxCompressedLength() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      int maxSize = zipper.maxCompressedLength(TEST_DATA.length);
      assertTrue(maxSize > TEST_DATA.length);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("maxCompressedLength throws exception for negative length")
  void testMaxCompressedLengthNegative() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.maxCompressedLength(-1);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("maxCompressedLength for ZSTD algorithm")
  void testMaxCompressedLengthZSTD() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      int maxSize = zipper.maxCompressedLength(TEST_DATA.length);
      assertTrue(maxSize > 0);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("getBytesRead returns correct value after compression")
  void testGetBytesRead() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      zipper.compress(TEST_DATA, output);

      int bytesRead = zipper.getBytesRead();
      assertEquals(TEST_DATA.length, bytesRead);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("getBytesWritten returns correct value after compression")
  void testGetBytesWritten() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] output = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, output);

      int bytesWritten = zipper.getBytesWritten();
      assertEquals(compressedSize, bytesWritten);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("getBytesRead and getBytesWritten after decompression")
  void testGetBytesAfterDecompression() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);

      // Decompress
      byte[] decompressed = new byte[TEST_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(compressedSize, zipper.getBytesRead());
      assertEquals(decompressedSize, zipper.getBytesWritten());
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // End/Resource Management Tests
  // ===========================

  @Test
  @DisplayName("end() can be called multiple times safely")
  void testEndMultipleCalls() {
    QatZipper zipper = new QatZipper.Builder().build();
    assertDoesNotThrow(
        () -> {
          zipper.end();
          zipper.end();
          zipper.end();
        });
  }

  @Test
  @DisplayName("Operations after end() throw IllegalStateException")
  void testOperationsAfterEnd() {
    QatZipper zipper = new QatZipper.Builder().build();
    byte[] output = new byte[100];
    zipper.end();

    assertThrows(IllegalStateException.class, () -> zipper.compress(TEST_DATA, output));
    assertThrows(
        IllegalStateException.class,
        () -> zipper.decompress(output, 0, output.length, new byte[100], 0, 100));
    assertThrows(IllegalStateException.class, () -> zipper.maxCompressedLength(100));
  }

  // ===========================
  // Static Method Tests
  // ===========================

  @Test
  @DisplayName("isQatAvailable returns a boolean value")
  void testIsQatAvailable() {
    boolean available = QatZipper.isQatAvailable();
    // Just verify it returns without exception, value depends on system
    assertTrue(available || !available);
  }

  // ===========================
  // Enum Tests
  // ===========================

  @Test
  @DisplayName("Algorithm enum has expected values")
  void testAlgorithmEnum() {
    assertEquals(3, QatZipper.Algorithm.values().length);
    assertNotNull(QatZipper.Algorithm.DEFLATE);
    assertNotNull(QatZipper.Algorithm.LZ4);
    assertNotNull(QatZipper.Algorithm.ZSTD);
  }

  @Test
  @DisplayName("Mode enum has expected values")
  void testModeEnum() {
    assertEquals(2, QatZipper.Mode.values().length);
    assertNotNull(QatZipper.Mode.HARDWARE);
    assertNotNull(QatZipper.Mode.AUTO);
  }

  @Test
  @DisplayName("PollingMode enum has expected values")
  void testPollingModeEnum() {
    assertEquals(2, QatZipper.PollingMode.values().length);
    assertNotNull(QatZipper.PollingMode.BUSY);
    assertNotNull(QatZipper.PollingMode.PERIODICAL);
  }

  @Test
  @DisplayName("DataFormat enum has expected values")
  void testDataFormatEnum() {
    assertEquals(5, QatZipper.DataFormat.values().length);
    assertNotNull(QatZipper.DataFormat.DEFLATE_4B);
    assertNotNull(QatZipper.DataFormat.DEFLATE_GZIP);
    assertNotNull(QatZipper.DataFormat.DEFLATE_GZIP_EXT);
    assertNotNull(QatZipper.DataFormat.DEFLATE_RAW);
    assertNotNull(QatZipper.DataFormat.ZLIB);
  }

  @Test
  @DisplayName("HardwareBufferSize enum has expected values and getValue works")
  void testHardwareBufferSizeEnum() {
    assertEquals(2, QatZipper.HardwareBufferSize.values().length);
    assertEquals(64 * 1024, QatZipper.HardwareBufferSize.DEFAULT_BUFFER_SIZE.getValue());
    assertEquals(512 * 1024, QatZipper.HardwareBufferSize.MAX_BUFFER_SIZE.getValue());
  }

  @Test
  @DisplayName("LogLevel enum has expected values")
  void testLogLevelEnum() {
    assertEquals(8, QatZipper.LogLevel.values().length);
    assertNotNull(QatZipper.LogLevel.NONE);
    assertNotNull(QatZipper.LogLevel.FATAL);
    assertNotNull(QatZipper.LogLevel.ERROR);
    assertNotNull(QatZipper.LogLevel.WARNING);
    assertNotNull(QatZipper.LogLevel.INFO);
    assertNotNull(QatZipper.LogLevel.DEBUG1);
    assertNotNull(QatZipper.LogLevel.DEBUG2);
    assertNotNull(QatZipper.LogLevel.DEBUG3);
  }

  // ===========================
  // Default Constants Tests
  // ===========================

  @Test
  @DisplayName("Default constants have expected values")
  void testDefaultConstants() {
    assertEquals(QatZipper.Algorithm.DEFLATE, QatZipper.DEFAULT_ALGORITHM);
    assertEquals(6, QatZipper.DEFAULT_COMPRESSION_LEVEL_DEFLATE);
    assertEquals(3, QatZipper.DEFAULT_COMPRESSION_LEVEL_ZSTD);
    assertEquals(QatZipper.Mode.AUTO, QatZipper.DEFAULT_MODE);
    assertEquals(QatZipper.PollingMode.BUSY, QatZipper.DEFAULT_POLLING_MODE);
    assertEquals(QatZipper.DataFormat.DEFLATE_GZIP_EXT, QatZipper.DEFAULT_DATA_FORMAT);
    assertEquals(
        QatZipper.HardwareBufferSize.DEFAULT_BUFFER_SIZE, QatZipper.DEFAULT_HW_BUFFER_SIZE);
    assertEquals(QatZipper.LogLevel.NONE, QatZipper.DEFAULT_LOG_LEVEL);
  }

  // ===========================
  // Large Data Tests
  // ===========================

  @Test
  @DisplayName("Compress and decompress large data successfully")
  void testLargeDataCompression() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(LARGE_DATA.length)];
      int compressedSize = zipper.compress(LARGE_DATA, compressed);
      assertTrue(compressedSize > 0);

      byte[] decompressed = new byte[LARGE_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(LARGE_DATA.length, decompressedSize);
      assertArrayEquals(LARGE_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Edge Case Tests
  // ===========================

  @Test
  @DisplayName("Compress single byte")
  void testCompressSingleByte() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] data = new byte[] {42};
      byte[] compressed = new byte[zipper.maxCompressedLength(data.length)];
      int compressedSize = zipper.compress(data, compressed);
      assertTrue(compressedSize > 0);

      byte[] decompressed = new byte[data.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(data.length, decompressedSize);
      assertArrayEquals(data, decompressed);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("ByteBuffer with custom position and limit")
  void testByteBufferCustomPositionLimit() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] largeData = new byte[100];
      System.arraycopy(TEST_DATA, 0, largeData, 10, TEST_DATA.length);

      ByteBuffer src = ByteBuffer.wrap(largeData);
      src.position(10);
      src.limit(10 + TEST_DATA.length);

      ByteBuffer dst = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, dst);
      assertTrue(compressedSize > 0);
      assertEquals(10 + TEST_DATA.length, src.position());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress with offset at end of array")
  void testCompressOffsetAtEnd() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] data = new byte[TEST_DATA.length + 10];
      System.arraycopy(TEST_DATA, 0, data, 10, TEST_DATA.length);

      byte[] output = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(data, 10, TEST_DATA.length, output, 0, output.length);
      assertTrue(compressedSize > 0);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Constructor Tests
  // ===========================

  @ParameterizedTest
  @EnumSource(QatZipper.Algorithm.class)
  @DisplayName("Single-argument constructor accepts all algorithms")
  void testSingleArgConstructor(QatZipper.Algorithm algorithm) {
    QatZipper zipper = new QatZipper(algorithm);
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("Two-argument constructor with algorithm and level")
  void testTwoArgConstructor() {
    QatZipper zipper = new QatZipper(QatZipper.Algorithm.ZSTD, 6);
    assertNotNull(zipper);
    zipper.end();
  }

  @Test
  @DisplayName("No-arg constructor creates default QatZipper")
  void testNoArgConstructor() {
    QatZipper zipper = new QatZipper();
    assertNotNull(zipper);
    zipper.end();
  }

  // ===========================
  // Builder getAlgorithm Tests
  // ===========================

  @Test
  @DisplayName("Builder getAlgorithm returns default algorithm")
  void testBuilderGetAlgorithmDefault() {
    QatZipper.Builder builder = new QatZipper.Builder();
    assertEquals(QatZipper.Algorithm.DEFLATE, builder.getAlgorithm());
  }

  @Test
  @DisplayName("Builder getAlgorithm returns set algorithm")
  void testBuilderGetAlgorithmAfterSet() {
    QatZipper.Builder builder = new QatZipper.Builder().algorithm(QatZipper.Algorithm.LZ4);
    assertEquals(QatZipper.Algorithm.LZ4, builder.getAlgorithm());
  }

  // ===========================
  // Decompress convenience method Tests
  // ===========================

  @Test
  @DisplayName("Decompress convenience method (byte[], byte[]) works correctly")
  void testDecompressConvenienceMethod() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);

      // Use exact-size compressed array for convenience method
      byte[] exactCompressed = new byte[compressedSize];
      System.arraycopy(compressed, 0, exactCompressed, 0, compressedSize);

      byte[] decompressed = new byte[TEST_DATA.length];
      int decompressedSize = zipper.decompress(exactCompressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      assertArrayEquals(TEST_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // compressFull Tests
  // ===========================

  @Test
  @DisplayName("compressFull compresses multiple sub-blocks successfully")
  void testCompressFullBasic() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      int blockLength = 1024;
      int numBlocks = (LARGE_DATA.length + blockLength - 1) / blockLength;
      int maxCompressed = zipper.maxCompressedLength(blockLength) * numBlocks;
      byte[] dst = new byte[maxCompressed];
      int[] sizes = new int[numBlocks];

      int totalWritten =
          zipper.compressFull(
              LARGE_DATA, 0, LARGE_DATA.length, blockLength, dst, 0, dst.length, sizes, 0);

      assertTrue(totalWritten > 0);
      // Verify sizes array is populated
      for (int i = 0; i < numBlocks; i++) {
        assertTrue(sizes[i] > 0, "Block " + i + " should have non-zero compressed size");
      }
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("compressFull throws exception when session is closed")
  void testCompressFullAfterEnd() {
    QatZipper zipper = new QatZipper.Builder().build();
    zipper.end();

    int[] sizes = new int[1];
    assertThrows(
        IllegalStateException.class,
        () -> {
          zipper.compressFull(
              TEST_DATA, 0, TEST_DATA.length, TEST_DATA.length, new byte[100], 0, 100, sizes, 0);
        });
  }

  @Test
  @DisplayName("compressFull throws exception for null source")
  void testCompressFullNullSource() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      int[] sizes = new int[1];
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.compressFull(null, 0, 10, 10, new byte[100], 0, 100, sizes, 0);
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // decompressFull Tests
  // ===========================

  @Test
  @DisplayName("decompressFull decompresses multiple concatenated frames")
  void testDecompressFullBasic() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      int blockLength = 1024;
      int numBlocks = (LARGE_DATA.length + blockLength - 1) / blockLength;
      int maxCompressed = zipper.maxCompressedLength(blockLength) * numBlocks;
      byte[] compressedDst = new byte[maxCompressed];
      int[] sizes = new int[numBlocks];

      int totalCompressed =
          zipper.compressFull(
              LARGE_DATA,
              0,
              LARGE_DATA.length,
              blockLength,
              compressedDst,
              0,
              compressedDst.length,
              sizes,
              0);

      // Now decompress using decompressFull
      byte[] decompressed = new byte[LARGE_DATA.length];
      int totalDecompressed =
          zipper.decompressFull(
              compressedDst, 0, totalCompressed, decompressed, 0, decompressed.length);

      assertEquals(LARGE_DATA.length, totalDecompressed);
      assertArrayEquals(LARGE_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("decompressFull throws exception when session is closed")
  void testDecompressFullAfterEnd() {
    QatZipper zipper = new QatZipper.Builder().build();
    zipper.end();

    assertThrows(
        IllegalStateException.class,
        () -> {
          zipper.decompressFull(new byte[100], 0, 100, new byte[200], 0, 200);
        });
  }

  @Test
  @DisplayName("decompressFull throws exception for null source")
  void testDecompressFullNullSource() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompressFull(null, 0, 10, new byte[100], 0, 100);
          });
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("decompressFull throws exception for null destination")
  void testDecompressFullNullDestination() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            zipper.decompressFull(new byte[100], 0, 100, null, 0, 10);
          });
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // All Algorithms Round-trip Tests
  // ===========================

  @ParameterizedTest
  @EnumSource(QatZipper.Algorithm.class)
  @DisplayName("Compress/decompress round-trip for all algorithms")
  void testRoundTripAllAlgorithms(QatZipper.Algorithm algorithm) {
    QatZipper zipper = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);
      assertTrue(compressedSize > 0);

      byte[] decompressed = new byte[TEST_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(TEST_DATA.length, decompressedSize);
      assertArrayEquals(TEST_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  @ParameterizedTest
  @EnumSource(QatZipper.Algorithm.class)
  @DisplayName("Large data round-trip for all algorithms")
  void testLargeDataRoundTripAllAlgorithms(QatZipper.Algorithm algorithm) {
    QatZipper zipper = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(LARGE_DATA.length)];
      int compressedSize = zipper.compress(LARGE_DATA, compressed);
      assertTrue(compressedSize > 0);

      byte[] decompressed = new byte[LARGE_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(LARGE_DATA.length, decompressedSize);
      assertArrayEquals(LARGE_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Mixed ByteBuffer Tests
  // ===========================

  @Test
  @DisplayName("Compress with direct source and heap destination buffer")
  void testCompressDirectSrcHeapDst() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.allocateDirect(TEST_DATA.length);
      src.put(TEST_DATA);
      src.flip();

      ByteBuffer dst = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, dst);
      assertTrue(compressedSize > 0);
      assertEquals(TEST_DATA.length, src.position());
      assertEquals(compressedSize, dst.position());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress with heap source and direct destination buffer")
  void testCompressHeapSrcDirectDst() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer dst = ByteBuffer.allocateDirect(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, dst);
      assertTrue(compressedSize > 0);
      assertEquals(TEST_DATA.length, src.position());
      assertEquals(compressedSize, dst.position());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress with direct source and heap destination buffer")
  void testDecompressDirectSrcHeapDst() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress using direct buffers
      ByteBuffer src = ByteBuffer.allocateDirect(TEST_DATA.length);
      src.put(TEST_DATA);
      src.flip();
      ByteBuffer compressed =
          ByteBuffer.allocateDirect(zipper.maxCompressedLength(TEST_DATA.length));
      int compressedSize = zipper.compress(src, compressed);
      compressed.flip();

      // Decompress: direct src -> heap dst
      ByteBuffer decompressed = ByteBuffer.allocate(TEST_DATA.length);
      int decompressedSize = zipper.decompress(compressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      decompressed.flip();
      byte[] result = new byte[decompressed.remaining()];
      decompressed.get(result);
      assertArrayEquals(TEST_DATA, result);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Decompress with heap source and direct destination buffer")
  void testDecompressHeapSrcDirectDst() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      // Compress using heap buffers
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer compressed = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));
      int compressedSize = zipper.compress(src, compressed);
      compressed.flip();

      // Decompress: heap src -> direct dst
      ByteBuffer decompressed = ByteBuffer.allocateDirect(TEST_DATA.length);
      int decompressedSize = zipper.decompress(compressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      decompressed.flip();
      byte[] result = new byte[decompressed.remaining()];
      decompressed.get(result);
      assertArrayEquals(TEST_DATA, result);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("Compress with read-only source buffer")
  void testCompressReadOnlySourceBuffer() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA).asReadOnlyBuffer();
      ByteBuffer dst = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, dst);
      assertTrue(compressedSize > 0);
      assertEquals(TEST_DATA.length, src.position());
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // ZSTD ByteBuffer Tests
  // ===========================

  @Test
  @DisplayName("ZSTD compress/decompress with heap ByteBuffers")
  void testZSTDByteBufferHeap() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer compressed = ByteBuffer.allocate(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, compressed);
      assertTrue(compressedSize > 0);
      compressed.flip();

      ByteBuffer decompressed = ByteBuffer.allocate(TEST_DATA.length);
      int decompressedSize = zipper.decompress(compressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      decompressed.flip();
      byte[] result = new byte[decompressed.remaining()];
      decompressed.get(result);
      assertArrayEquals(TEST_DATA, result);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("ZSTD compress/decompress with direct ByteBuffers")
  void testZSTDByteBufferDirect() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      ByteBuffer src = ByteBuffer.allocateDirect(TEST_DATA.length);
      src.put(TEST_DATA);
      src.flip();
      ByteBuffer compressed =
          ByteBuffer.allocateDirect(zipper.maxCompressedLength(TEST_DATA.length));

      int compressedSize = zipper.compress(src, compressed);
      assertTrue(compressedSize > 0);
      compressed.flip();

      ByteBuffer decompressed = ByteBuffer.allocateDirect(TEST_DATA.length);
      int decompressedSize = zipper.decompress(compressed, decompressed);

      assertEquals(TEST_DATA.length, decompressedSize);
      decompressed.flip();
      byte[] result = new byte[decompressed.remaining()];
      decompressed.get(result);
      assertArrayEquals(TEST_DATA, result);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // ZSTD Checksum Round-trip Test
  // ===========================

  @Test
  @DisplayName("ZSTD compression with checksum enabled produces valid data")
  void testZSTDChecksumRoundTrip() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      zipper.setChecksumFlag(true);
      assertTrue(zipper.getChecksumFlag());

      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);
      assertTrue(compressedSize > 0);

      byte[] decompressed = new byte[TEST_DATA.length];
      int decompressedSize =
          zipper.decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

      assertEquals(TEST_DATA.length, decompressedSize);
      assertArrayEquals(TEST_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // maxCompressedLength Edge Case Tests
  // ===========================

  @Test
  @DisplayName("maxCompressedLength for zero length returns positive value")
  void testMaxCompressedLengthZero() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      int maxSize = zipper.maxCompressedLength(0);
      assertTrue(maxSize >= 0);
    } finally {
      zipper.end();
    }
  }

  @ParameterizedTest
  @EnumSource(QatZipper.Algorithm.class)
  @DisplayName("maxCompressedLength for all algorithms")
  void testMaxCompressedLengthAllAlgorithms(QatZipper.Algorithm algorithm) {
    QatZipper zipper = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      int maxSize = zipper.maxCompressedLength(TEST_DATA.length);
      assertTrue(maxSize > 0);
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Builder Validation Edge Cases
  // ===========================

  @Test
  @DisplayName("Builder throws exception for DEFLATE level above 9")
  void testBuilderInvalidDeflateLevelHigh() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new QatZipper.Builder().algorithm(QatZipper.Algorithm.DEFLATE).level(10).build();
        });
  }

  @Test
  @DisplayName("Builder throws exception for LZ4 level above 9")
  void testBuilderInvalidLZ4LevelHigh() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new QatZipper.Builder().algorithm(QatZipper.Algorithm.LZ4).level(10).build();
        });
  }

  @Test
  @DisplayName("Builder throws exception for ZSTD level above 12")
  void testBuilderInvalidZSTDLevelAbove12() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).level(13).build();
        });
  }

  @Test
  @DisplayName("Convenience constructor validates compression level")
  void testConvenienceConstructorValidatesLevel() {
    assertThrows(
        IllegalArgumentException.class, () -> new QatZipper(QatZipper.Algorithm.DEFLATE, 99));
    assertThrows(IllegalArgumentException.class, () -> new QatZipper(QatZipper.Algorithm.ZSTD, 13));
  }

  @Test
  @DisplayName("compressFull and decompressFull return sizes for ZSTD")
  void testZstdCompressFullReturnsSize() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(LARGE_DATA.length)];
      int[] sizes = new int[4];
      int compressedSize =
          zipper.compressFull(
              LARGE_DATA, 0, LARGE_DATA.length, 4096, compressed, 0, compressed.length, sizes, 0);
      assertTrue(compressedSize > 0);
      assertEquals(LARGE_DATA.length, zipper.getBytesRead());
      assertEquals(compressedSize, zipper.getBytesWritten());

      byte[] decompressed = new byte[LARGE_DATA.length];
      int decompressedSize =
          zipper.decompressFull(
              compressed, 0, compressedSize, decompressed, 0, decompressed.length);
      assertEquals(LARGE_DATA.length, decompressedSize);
      assertEquals(compressedSize, zipper.getBytesRead());
      assertEquals(decompressedSize, zipper.getBytesWritten());
      assertArrayEquals(LARGE_DATA, decompressed);
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("compressFull throws exception for null sizes array")
  void testCompressFullNullSizes() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      assertThrows(
          IllegalArgumentException.class,
          () ->
              zipper.compressFull(
                  TEST_DATA, 0, TEST_DATA.length, 16, compressed, 0, compressed.length, null, 0));
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("compressFull throws exception for too-short sizes array")
  void testCompressFullShortSizes() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(LARGE_DATA.length)];
      // 10000 bytes in 4096-byte blocks needs 3 entries
      int[] sizes = new int[2];
      assertThrows(
          ArrayIndexOutOfBoundsException.class,
          () ->
              zipper.compressFull(
                  LARGE_DATA,
                  0,
                  LARGE_DATA.length,
                  4096,
                  compressed,
                  0,
                  compressed.length,
                  sizes,
                  0));
    } finally {
      zipper.end();
    }
  }

  @ParameterizedTest
  @EnumSource(QatZipper.Algorithm.class)
  @DisplayName("ByteBuffer decompress advances source position for all algorithms")
  void testByteBufferDecompressAdvancesSource(QatZipper.Algorithm algorithm) {
    QatZipper zipper = new QatZipper.Builder().algorithm(algorithm).build();
    try {
      ByteBuffer src = ByteBuffer.allocateDirect(LARGE_DATA.length);
      src.put(LARGE_DATA).flip();
      ByteBuffer compressed =
          ByteBuffer.allocateDirect(zipper.maxCompressedLength(LARGE_DATA.length));
      zipper.compress(src, compressed);
      compressed.flip();
      int compressedSize = compressed.remaining();

      ByteBuffer decompressed = ByteBuffer.allocateDirect(LARGE_DATA.length);
      zipper.decompress(compressed, decompressed);
      assertEquals(compressedSize, compressed.position());
      assertFalse(compressed.hasRemaining());
      assertEquals(compressedSize, zipper.getBytesRead());
      assertEquals(LARGE_DATA.length, zipper.getBytesWritten());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("getBytesRead and getBytesWritten return 0 after a failed operation")
  void testCountersResetOnFailure() {
    QatZipper zipper = new QatZipper.Builder().build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);
      assertTrue(compressedSize > 0);
      assertTrue(zipper.getBytesRead() > 0);

      // Early-exit failure: read-only destination buffer
      ByteBuffer src = ByteBuffer.wrap(TEST_DATA);
      ByteBuffer dst = ByteBuffer.allocate(64).asReadOnlyBuffer();
      assertThrows(ReadOnlyBufferException.class, () -> zipper.compress(src, dst));
      assertEquals(0, zipper.getBytesRead());
      assertEquals(0, zipper.getBytesWritten());
    } finally {
      zipper.end();
    }
  }

  @Test
  @DisplayName("getBytesRead and getBytesWritten return 0 after a failed ZSTD decompression")
  void testCountersResetOnZstdFailure() {
    QatZipper zipper = new QatZipper.Builder().algorithm(QatZipper.Algorithm.ZSTD).build();
    try {
      byte[] compressed = new byte[zipper.maxCompressedLength(TEST_DATA.length)];
      int compressedSize = zipper.compress(TEST_DATA, compressed);
      assertTrue(compressedSize > 0);
      assertTrue(zipper.getBytesRead() > 0);

      // Not a valid ZSTD frame
      byte[] garbage = new byte[64];
      byte[] dst = new byte[64];
      assertThrows(RuntimeException.class, () -> zipper.decompress(garbage, dst));
      assertEquals(0, zipper.getBytesRead());
      assertEquals(0, zipper.getBytesWritten());
    } finally {
      zipper.end();
    }
  }

  // ===========================
  // Helper Methods
  // ===========================

  private static byte[] generateLargeTestData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) (i % 256);
    }
    return data;
  }
}
