## JMH
A set of JMH benchmarks.

Parameters for all benchmarks:

* `file`: path to the input file to be compressed.
* `level`: compression level to use (min: 1, max: 12). Higher levels lead to smaller output.
  * Note: zstd software compression levels correspond unequally with the levels of most other benchmarks; therefore they use a separate parameter.

| Name                                         | Algorithm                | Params |
| -------------------------------------------- | ------------------------ | ------ |
| `QatJavaBench`, `QatJavaStreamBench`         | DEFLATE, gzip-ext format |        |
| `JavaUtilZipBench`, `JavaUtilZipStreamBench` | DEFLATE, zlib format     |        |
| `Lz4JavaBench`                               | LZ4                      |        |
| `QatZstdBench`                               | Zstandard                | `zstdChunkSize`: break the input into chunks of the specified size |
| `ZstdSoftwareBench`                          | Zstandard                | `zstdChunkSize`: break the input into chunks of the specified size<br>`zstdLevel`: sets the zstd software compression level; this is always used in place of `level` |

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

Example:
```
java -jar target/benchmarks.jar QatJavaBench -p file=silesia/dickens -p level=6 -f 1 -wi 1 -i 2 -t 1
```

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
