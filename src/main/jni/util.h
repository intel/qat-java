/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include <jni.h>
#ifndef util_h
#define util_h
#ifdef __cplusplus
extern "C" {
#endif

void throw_exception(JNIEnv *env, const char *arg, jlong status);

#ifdef __cplusplus
}
#endif
#endif