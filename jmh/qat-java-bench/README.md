## Performance Test
This JMH benchmark may be used to performance test the Qat-Java library.

## Build
To build the benchmark, execute the below command:
```
mvn clean install
```

## Run
To run the benchmark, use the below command:

```
java -jar target/benchmarks.jar /path/to/a/text-corpus [options] [benchmarks ...]
```

Options include:
- `-t#`: set the number of threads
- `-p zstdChunkSize=#`: set the chunk size
- `-p zstdLevel=#`: set the zstd compression level

By default, all benchmarks will be run; to run individual benchmarks instead, specify one or more of the following:
- `QatJavaBench` (QAT, Deflate/LZ4)
- `JavaZipBench` (CPU, Deflate/LZ4)
- `QatZstdBench` (QAT, zstd)
- `ZstdSoftwareBench` (CPU, zstd)

For example:


```
java -jar target/benchmarks.jar ~/Downloads/silesia.concat -t64 -p zstdChunkSize=16384 QatZstdBench ZstdSoftwareBench
```

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
