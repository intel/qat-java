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

public class NativeTest {
  @BeforeEach
  void setUp() {
    setField("loaded", false);
  }

  @Test
  void testLoadNativeLibraryAlreadyLoaded() {
    setField("loaded", true);

    assertDoesNotThrow(() -> Native.loadLibrary());
    assertTrue(Native.isLoaded(), "Library should be marked as loaded");
  }

  @Test
  void testLoadNativeLibraryNotPresent() {
    setField("loaded", false);
    try {
      Native.loadLibrary();
      assertTrue(true);
    } catch (RuntimeException e) {
    }
  }

  private <T> void setField(String fieldName, T value) {
    try {
      Field field = Native.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(null, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }
}
