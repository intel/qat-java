#!/bin/bash
mvn clean install
SOURCE_FILE_SIZE=$(ls -l samples/dickens | awk '{print $5}')

<<COMMENT
java -jar target/benchmarks.jar BenchmarkWithFile.QatCompressor \
-p pinMemSize=65536 -p fileName=samples/dickens

java -jar target/benchmarks.jar BenchmarkWithFile.QatDecompressor \
-p pinMemSize=65536 -p fileName=samples/dickens.qat.gz -p srcFileSize=${SOURCE_FILE_SIZE}
COMMENT
java -jar target/benchmarks.jar BenchmarkWithFile.JavaZipDeflater \
-p fileName=samples/dickens

java -jar target/benchmarks.jar BenchmarkWithFile.JavaZipInflater \
-p fileName=samples/dickens.javazip.gz -p srcFileSize=${SOURCE_FILE_SIZE}
