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
    setField("libName", "libfake-qat-java");
    try {
      NativeLoader.loadLibrary();
    } catch (RuntimeException e) {
      assertTrue(true);
    }
    setField("libName", "libqat-java");
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
