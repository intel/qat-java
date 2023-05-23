/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

/**
 * Thrown to indicate that the qat operation failed to execute successfully.
 */
public class QATException extends RuntimeException {

    /**
     * Constructs a new QATException with the specified message.
     *
     * @param message error message
     */
    public QATException(String message) {
        super(message);
    }
}