#!/bin/bash
mvn clean install
java -jar target/benchmarks.jar BenchmarkWithFile -p pinMemSize=65536 -p fileName=samples/dickens
