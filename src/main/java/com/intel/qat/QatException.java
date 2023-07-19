/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

/**
 * A class that represents QAT related exceptions.
 */
public class QatException extends RuntimeException {
  /**
   * Constructs a new QatException with the specified message.
   *
   * @param message the error message.
   */
  public QatException(String message) {
    super(message);
  }
}
