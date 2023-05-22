/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include <jni.h>

#ifndef _Included_com_intel_qat_InternalJNI
#define _Included_com_intel_qat_InternalJNI
#ifdef __cplusplus
extern "C" {
#endif

void throw_exception(JNIEnv *env, const char *arg, jlong status);

#ifdef __cplusplus
}
#endif
#endif