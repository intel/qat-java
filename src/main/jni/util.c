/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
#include "util.h"

#include <qatzip.h>

/**
 * Retrieves the QAT error message string for a given error code.
 *
 * @param err_code The QAT error code
 * @return A constant string describing the error code, or "QZ_UNKNOWN" for
 * unrecognized codes
 */
const char *get_err_str(int err_code) {
  // clang-format off
  switch (err_code) {
    case QZ_OK:                    return "QZ_OK: The operation was successful";
    case QZ_DUPLICATE:             return "QZ_DUPLICATE: Duplicate operation detected";
    case QZ_FORCE_SW:              return "QZ_FORCE_SW: Forced to use software implementation";
    case QZ_PARAMS:                return "QZ_PARAMS: Invalid or incorrect parameters provided";
    case QZ_FAIL:                  return "QZ_FAIL: General operation failure";
    case QZ_BUF_ERROR:             return "QZ_BUF_ERROR: Buffer-related error occurred";
    case QZ_DATA_ERROR:            return "QZ_DATA_ERROR: Input data is corrupted or invalid";
    case QZ_TIMEOUT:               return "QZ_TIMEOUT: Operation timed out";
    case QZ_INTEG:                 return "QZ_INTEG: Integrity check failed";
    case QZ_NO_HW:                 return "QZ_NO_HW: No hardware acceleration available";
    case QZ_NO_MDRV:               return "QZ_NO_MDRV: Missing or incompatible driver";
    case QZ_NO_INST_ATTACH:        return "QZ_NO_INST_ATTACH: Failed to attach to instance";
    case QZ_LOW_MEM:               return "QZ_LOW_MEM: Insufficient memory available";
    case QZ_LOW_DEST_MEM:          return "QZ_LOW_DEST_MEM: Insufficient destination memory";
    case QZ_UNSUPPORTED_FMT:       return "QZ_UNSUPPORTED_FMT: Unsupported format detected";
    case QZ_NONE:                  return "QZ_NONE: No error condition specified";
    case QZ_NOSW_NO_HW:            return "QZ_NOSW_NO_HW: No software fallback and hardware unavailable";
    case QZ_NOSW_NO_MDRV:          return "QZ_NOSW_NO_MDRV: No software fallback and missing driver";
    case QZ_NOSW_NO_INST_ATTACH:   return "QZ_NOSW_NO_INST_ATTACH: No software fallback and instance attachment failed";
    case QZ_NOSW_LOW_MEM:          return "QZ_NOSW_LOW_MEM: No software fallback and insufficient memory";
    case QZ_NO_SW_AVAIL:           return "QZ_NO_SW_AVAIL: No software implementation available";
    case QZ_NOSW_UNSUPPORTED_FMT:  return "QZ_NOSW_UNSUPPORTED_FMT: No software fallback and unsupported format";
    case QZ_POST_PROCESS_ERROR:    return "QZ_POST_PROCESS_ERROR: Error during post-processing";
    case QZ_METADATA_OVERFLOW:     return "QZ_METADATA_OVERFLOW: Metadata exceeds allocated space";
    case QZ_OUT_OF_RANGE:          return "QZ_OUT_OF_RANGE: Value outside acceptable range";
    case QZ_NOT_SUPPORTED:         return "QZ_NOT_SUPPORTED: Operation or feature not supported";
    default:
        return "QZ_UNKNOWN: Unknown error code";
  }
  // clang-format on
}
