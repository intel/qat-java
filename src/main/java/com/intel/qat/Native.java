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
class Native {
    private static boolean loaded = false;
    private static String extension = "";

    //https://github.com/lz4/lz4-java/pull/204/files - privilege section
    static boolean isLoaded() {
        if (loaded) return true;
        try {
            System.loadLibrary("qat-java");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            // Could not load native library from "java.library.path"
            // Next, try loading from the jar
            loaded = false;
        }
        return loaded;
    }

    static String getLibName() {
        return "/com/intel/qat/" + getOSName() + "/" + getOSArch() + "/" + "libqat-java" + extension;
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

    static String getOSArch() {
        return System.getProperty("os.arch");
    }

    static synchronized void loadLibrary() {
        if (isLoaded()) return;
        String libName = getLibName();
        File tempNativeLib = null;
        File tempNativeLibLock = null;
        try (InputStream in = Native.class.getResourceAsStream(libName)) {
            if (in == null) {
                throw new UnsupportedOperationException("Unsupported OS/arch, cannot find " + libName + ". Please try building from source.");
            }
            // To avoid race condition with other concurrently running Java processes using qpl-java create the .lck file first.
            tempNativeLibLock = File.createTempFile("libqpl-java", extension + ".lck");
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
                throw new IOException("Failed to load native qat-java library");
            }
            System.load(tempNativeLib.getAbsolutePath());
            loaded = true;
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load native qat-java library");
        } finally {
            if (tempNativeLib != null)
                tempNativeLib.deleteOnExit();
            if (tempNativeLibLock != null)
                tempNativeLibLock.deleteOnExit();
        }
    }
}
