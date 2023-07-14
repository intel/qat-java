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

void throw_exception(JNIEnv *env, jlong err_code, const char *msg);

#ifdef __cplusplus
}
#endif
#endif
