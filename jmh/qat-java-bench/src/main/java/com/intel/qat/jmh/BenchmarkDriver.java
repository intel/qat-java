/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.jmh;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkDriver {
  private static String classPattern(String s) {
    // Matches the class name, optionally with a '.' on either side of the name
    return "(?<![^.])" + s + "(?![^.])";
  }

  private static String classPattern(Class<?> c) {
    return "(?<![^.])" + c.getSimpleName() + "(?![^.])";
  }

  public static void main(String[] args) throws Exception {
    String fileName = args[0];
    String[] jmhArgs = Arrays.copyOfRange(args, 1, args.length);
    CommandLineOptions cli = new CommandLineOptions(jmhArgs);

    // Determine which benchmarks to run
    ChainedOptionsBuilder builder = new OptionsBuilder();
    List<String> argIncludes = cli.getIncludes();
    if (argIncludes.isEmpty()) {
      // No benchmarks specified. Enable all benchmarks by default:
      builder =
          builder
              .parent(cli)
              .include(classPattern(QatJavaBench.class))
              .include(classPattern(JavaZipBench.class))
              .include(classPattern(QatZstdBench.class))
              .include(classPattern(ZstdSoftwareBench.class));
    } else {
      // Re-parse the arguments, removing any benchmark names
      String[] newJmhArgs = new String[jmhArgs.length - argIncludes.size()];
      int i = 0;
      for (String arg : jmhArgs) {
        if (!cli.getIncludes().contains(arg)) newJmhArgs[i++] = arg;
      }
      assert i == newJmhArgs.length;
      CommandLineOptions newCli = new CommandLineOptions(newJmhArgs);

      // Re-include any requested benchmarks, but now using `classPattern()`:
      builder = builder.parent(newCli);
      for (String include : cli.getIncludes()) builder = builder.include(classPattern(include));
    }

    // Finish building the options, and run the benchmarks!
    Options opts =
        builder
            .forks(1)
            .param("fileName", fileName)
            // .jvmArgs("-Xms4g", "-Xmx4g")
            .build();

    Collection<RunResult> results = new Runner(opts).run();
    System.out.println("-------------------------");
    System.out.println("SUMMARY");
    System.out.println("-------------------------");

    // Calculate the throughput in MB/sec for each result
    long fileSize = new File(fileName).length();
    for (RunResult rr : results) {
      Result<?> r = rr.getAggregatedResult().getPrimaryResult();
      double speed = r.getScore() * fileSize / (1024 * 1024);
      System.out.printf("%-50s%.2f MB/sec\n", rr.getParams().getBenchmark(), speed);
    }
  }
}
