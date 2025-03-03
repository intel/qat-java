/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Class for loading system library - libqat-java.so */
class NativeLoader {
  private static String libName = "libqat-java";
  private static boolean isLoaded = false;
  private static String extension;

  @SuppressWarnings({"deprecation", "removal"})
  static boolean isLoaded() {
    if (isLoaded) return true;
    try {
      SecurityManager sm = System.getSecurityManager();
      if (sm == null) {
        System.loadLibrary(libName);
      } else {
        java.security.PrivilegedAction<Void> pa =
            () -> {
              System.loadLibrary(libName);
              return null;
            };
        java.security.AccessController.doPrivileged(pa);
      }
      isLoaded = true;
    } catch (UnsatisfiedLinkError e) {
      isLoaded = false;
    }
    return isLoaded;
  }

  static synchronized String getLibName() {
    return "/com/intel/qat/"
        + getOSName()
        + "/"
        + System.getProperty("os.arch")
        + "/"
        + libName
        + extension;
  }

  static String getOSName() {
    String os = System.getProperty("os.name");
    String ret;
    if (os.contains("Linux")) {
      ret = "linux";
      extension = ".so";
    } else throw new UnsupportedOperationException("Operating System is not supported");
    return ret;
  }

  @SuppressWarnings({"deprecation", "removal"})
  static synchronized void loadLibrary() {
    if (isLoaded()) return;
    String libName = getLibName();
    File tempNativeLib = null;
    File tempNativeLibLock = null;
    try (InputStream in = NativeLoader.class.getResourceAsStream(libName)) {
      if (in == null) {
        throw new UnsupportedOperationException(
            "Unsupported OS/arch, cannot find " + libName + ". Please try building from source.");
      }
      // To avoid race condition with other concurrently running Java processes
      // using qat-java create the .lck file first.

      tempNativeLibLock = File.createTempFile("libqat-java", extension + ".lck");
      tempNativeLib = new File(tempNativeLibLock.getAbsolutePath().replaceFirst(".lck$", ""));
      try (FileOutputStream out = new FileOutputStream(tempNativeLib)) {
        byte[] buf = new byte[4096];
        int bytesRead;
        while (true) {
          bytesRead = in.read(buf);
          if (bytesRead == -1) break;
          out.write(buf, 0, bytesRead);
        }
      }
      boolean isSymbolicLink = Files.isSymbolicLink(tempNativeLib.toPath());
      if (isSymbolicLink) {
        throw new IOException(
            "Failed to load native qat-java library."
                + tempNativeLib.toPath()
                + " is a symbolic link.");
      }
      File finalTempNativeLib = tempNativeLib;
      SecurityManager sm = System.getSecurityManager();
      if (sm == null) {
        System.load(finalTempNativeLib.getAbsolutePath());
      } else {
        java.security.PrivilegedAction<Void> pa =
            () -> {
              System.load(finalTempNativeLib.getAbsolutePath());
              return null;
            };
        java.security.AccessController.doPrivileged(pa);
      }
      isLoaded = true;
    } catch (IOException e) {
      throw new ExceptionInInitializerError(
          "Failed to load native qat-java library.\n" + e.getMessage());
    } catch (RuntimeException e) {
      throw e;
    } finally {
      if (tempNativeLib != null) tempNativeLib.deleteOnExit();
      if (tempNativeLibLock != null) tempNativeLibLock.deleteOnExit();
    }
  }
}
