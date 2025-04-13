/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
#include <qatzip.h>
#include "util.h"

/**
 * Retrieves the QAT error message string for a given error code.
 *
 * @param err_code The QAT error code
 * @return A constant string describing the error code, or "QZ_UNKNOWN" for unrecognized codes
 */
const char *get_err_str(int err_code)
{
    switch (err_code) {
        case QZ_OK:                    return "QZ_OK";
        case QZ_DUPLICATE:             return "QZ_DUPLICATE";
        case QZ_FORCE_SW:              return "QZ_FORCE_SW";
        case QZ_PARAMS:                return "QZ_PARAMS";
        case QZ_FAIL:                  return "QZ_FAIL";
        case QZ_BUF_ERROR:             return "QZ_BUF_ERROR";
        case QZ_DATA_ERROR:            return "QZ_DATA_ERROR";
        case QZ_TIMEOUT:               return "QZ_TIMEOUT";
        case QZ_INTEG:                 return "QZ_INTEG";
        case QZ_NO_HW:                 return "QZ_NO_HW";
        case QZ_NO_MDRV:               return "QZ_NO_MDRV";
        case QZ_NO_INST_ATTACH:        return "QZ_NO_INST_ATTACH";
        case QZ_LOW_MEM:               return "QZ_LOW_MEM";
        case QZ_LOW_DEST_MEM:          return "QZ_LOW_DEST_MEM";
        case QZ_UNSUPPORTED_FMT:       return "QZ_UNSUPPORTED_FMT";
        case QZ_NONE:                  return "QZ_NONE";
        case QZ_NOSW_NO_HW:            return "QZ_NOSW_NO_HW";
        case QZ_NOSW_NO_MDRV:          return "QZ_NOSW_NO_MDRV";
        case QZ_NOSW_NO_INST_ATTACH:   return "QZ_NOSW_NO_INST_ATTACH";
        case QZ_NOSW_LOW_MEM:          return "QZ_NOSW_LOW_MEM";
        case QZ_NO_SW_AVAIL:           return "QZ_NO_SW_AVAIL";
        case QZ_NOSW_UNSUPPORTED_FMT:  return "QZ_NOSW_UNSUPPORTED_FMT";
        case QZ_POST_PROCESS_ERROR:    return "QZ_POST_PROCESS_ERROR";
        case QZ_METADATA_OVERFLOW:     return "QZ_METADATA_OVERFLOW";
        case QZ_OUT_OF_RANGE:          return "QZ_OUT_OF_RANGE";
        case QZ_NOT_SUPPORTED:         return "QZ_NOT_SUPPORTED";
        default:
            return "QZ_UNKNOWN";
    }
}
