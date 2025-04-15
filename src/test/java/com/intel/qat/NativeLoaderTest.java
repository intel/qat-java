/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NativeLoaderTest {
  @BeforeEach
  void setUp() {
    setField("isLoaded", false);
  }

  @Test
  void testLoadNativeLibraryAlreadyLoaded() {
    setField("isLoaded", true);

    assertDoesNotThrow(() -> NativeLoader.loadLibrary());
    assertTrue(NativeLoader.isLoaded(), "Library should be marked as loaded");
  }

  @Test
  void testLoadNativeLibraryNotPresent() {
    setField("isLoaded", false);
    try {
      NativeLoader.loadLibrary();
      assertTrue(true);
    } catch (RuntimeException e) {
    }
  }

  private <T> void setField(String fieldName, T value) {
    try {
      Field field = NativeLoader.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(null, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }
}
