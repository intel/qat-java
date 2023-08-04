/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

/**
 * Signals that a QAT error has occurred.
 */
public class QatException extends RuntimeException {
  /**
   * Constructs a QatException with the specified detail message.
   *
   * @param message the string containing a detail message
   */
  public QatException(String message) {
    super(message);
  }
}
