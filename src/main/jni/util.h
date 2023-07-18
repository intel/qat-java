/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#ifndef UTIL_H_
#define UTIL_H_

#include <jni.h>

#define QZ_HW_INIT_ERROR "An error occured while initializing QAT hardware."
#define QZ_SETUP_SESSION_ERROR "An error occured while setting up session."
#define QZ_MEMFREE_ERROR "An error occured while freeing up pinned memory."
#define QZ_BUFFER_ERROR "An error occured while reading the buffer."
#define QZ_COMPRESS_ERROR "An error occured while compression."
#define QZ_DECOMPRESS_ERROR "An error occured while decompression."
#define QZ_TEARDOWN_ERROR "An error occured while tearing down session."

#ifdef __cplusplus
extern "C" {
#endif

void throw_exception(JNIEnv *env, jlong err_code, const char *msg);

#ifdef __cplusplus
}
#endif

#endif
