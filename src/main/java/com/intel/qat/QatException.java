/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

/**
 * Thrown to indicate that the qat operation failed to execute successfully.
 */
public class QatException extends RuntimeException {
  /**
   * Constructs a new QatException with the specified message.
   *
   * @param message error message
   */
  public QatException(String message) {
    super(message);
  }
}