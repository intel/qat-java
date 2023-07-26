/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#ifndef UTIL_H_
#define UTIL_H_

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

void throw_exception(JNIEnv *env, jlong err_code, const char *msg);
unsigned int next_power_of_2(unsigned int);

#ifdef __cplusplus
}
#endif

#endif
