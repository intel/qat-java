/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.io.File;
import java.util.Collection;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.util.Optional;

public class BenchmarkDriver {
  public static void main(String[] args) {
    try {
      CommandLineOptions cmdOpts = new CommandLineOptions(args);

      Optional<Collection<String>> col = cmdOpts.getParameter("file");
      if (!col.hasValue()) {
        throw new IllegalArgumentException("A file parameter is required.");
      }
      String file = col.get().iterator().next();

      // Run benchmark
      Collection<RunResult> results = new Runner(cmdOpts).run();

      // Print score in MB/sec (if benchmark mode is throughput)
      System.out.println();
      long fileSize = new File(file).length();
      for (RunResult rr : results) {
        Result r = rr.getPrimaryResult();
        if (r.getScoreUnit().equals("ops/s")) {
          double speed = r.getScore() * fileSize / (1024 * 1024);
          System.out.printf("%-54s%.2f MB/sec%n", rr.getParams().getBenchmark(), speed);
        }
      }
    } catch (RunnerException e) {
      System.err.printf("%s: %s", "Error", e.getMessage());
    } catch (CommandLineOptionException e) {
      System.err.printf("%s: %s", "Error parsing command line", e.getMessage());
    }
  }
}
