/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
#include "util.h"

#include <limits.h>
#include <stdio.h>

static char *get_error_msg(int err_code) {
  switch (err_code) {
    case 0:
      return "QZ_OK";
    case 1:
      return "QZ_DUPLICATE";
    case 2:
      return "QZ_FORCE_SW";
    case -1:
      return "QZ_PARAMS";
    case -2:
      return "QZ_FAIL";
    case -3:
      return "QZ_BUF_ERROR";
    case -4:
      return "QZ_DATA_ERROR";
    case -5:
      return "QZ_TIMEOUT";
    case -100:
      return "QZ_INTEG";
    case 11:
      return "QZ_NO_HW";
    case 12:
      return "QZ_NO_MDRV";
    case 13:
      return "QZ_NO_INST_ATTACH";
    case 14:
      return "QZ_LOW_MEM";
    case 15:
      return "QZ_LOW_DEST_MEM";
    case 16:
      return "QZ_UNSUPPORTED_FMT";
    case 100:
      return "QZ_NONE";
    case -101:
      return "QZ_NOSW_NO_HW";
    case -102:
      return "QZ_NOSW_NO_MDRV";
    case -103:
      return "QZ_NOSW_NO_INST_ATTACH";
    case -104:
      return "QZ_NOSW_LOW_MEM";
    case -105:
      return "QZ_NO_SW_AVAIL";
    case -116:
      return "QZ_NOSW_UNSUPPORTED_FMT";
    case -117:
      return "QZ_POST_PROCESS_ERROR";
    case -118:
      return "QZ_METADATA_OVERFLOW";
    case -119:
      return "QZ_OUT_OF_RANGE";
    case -200:
      return "QZ_NOT_SUPPORTED";
    case INT_MIN:
      return "Session not setup";
  }
  return "A_NON_DOCUMENTED_ERROR_CODE_RETURNED";
}

void throw_exception(JNIEnv *env, jlong err_code, const char *err_msg) {
  char buff[256];
  jclass Exception = (*env)->FindClass(env, "com/intel/qat/QatException");
  snprintf(buff, sizeof(buff), "%s: %s", get_error_msg(err_code), err_msg);
  (*env)->ThrowNew(env, Exception, buff);
}
