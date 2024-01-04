## JMH
A set of JMH benchmarks.
```
Name              | Algorithm                | Parameters
---------------------------------------------------------
QatJavaBench      | DEFLATE, gzip-ext format | file, level
JavaUtilZipBench  | DEFLATE, zlib format     | file, level
Lz4JavaBench      | LZ4                      | file
ZstdJniBench      | Zstandard                | file, zstdLevel, zstdChunkSize, threads
QatZstdBench      | Zstandard                | file, zstdLevel, zstdChunkSize, threads
```
<!-- TODO Update name to ZstdJniBench (or fix name in README), update '-t' arg to 'threads' -->

## Build
To build the benchmark, execute the below command:
```
mvn clean install
```

## Run
To run the benchmark, use the below command:

```
java -jar target/benchmarks.jar [benchmark-class] -p file=/path/to/a/text-corpus -p level=<level> <jmh-params>
```
<!-- ```
TODO DELETE java -jar target/benchmarks.jar /path/to/a/text-corpus [options] [benchmarks ...]
``` -->
<!-- TODO DELETE
Options include:
- `-t#`: set the number of threads
- `-p zstdChunkSize=#`: set the chunk size
- `-p zstdLevel=#`: set the zstd compression level

By default, all benchmarks will be run; to run individual benchmarks instead, specify one or more of the following:
- `QatJavaBench` (QAT, Deflate/LZ4)
- `JavaZipBench` (CPU, Deflate/LZ4)
- `QatZstdBench` (QAT, zstd)
- `ZstdSoftwareBench` (CPU, zstd) -->

For example:

```
java -jar target/benchmarks.jar QatJavaBench -p file=silesia/dickens -p level=6 -wi 1 -i 2 -t 1
```
<!-- ``` TODO DELETE
java -jar target/benchmarks.jar ~/Downloads/silesia.concat -t64 -p zstdChunkSize=16384 QatZstdBench ZstdSoftwareBench
``` -->

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
