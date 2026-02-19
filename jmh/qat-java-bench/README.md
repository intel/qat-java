## JMH
A set of JMH benchmarks.
```
Name                | Algorithm
--------------------------------------------
QatJavaBench        | DEFLATE, LZ4, ZSTD
QatJavaStreamBench  | Uses a streaming API
DeflaterBench       | Uses java.util.zip.Deflater/Inflater
DeflaterStreamBench | Uses a streaming API
ZstdBench           | ZSTD (using zstd-jni)
ZstdStreamBench     | Uses a streaming API
```

## Build
To build the benchmark, execute the below command:
```
mvn clean package
```

## Run
To run the benchmark, use the below command:

```
java -jar target/benchmarks.jar QatJavaBench -p inputFilePath=/path/to/a/text-corpus -p algorithmName=<"DEFLATE"|"LZ4"|"ZSTD"> -p compressionLevel=<compressionLevel> -p blockSizeBytes=<size> <jmh-params>
java -jar target/benchmarks.jar QatJavaStreamBench -p inputFilePath=/path/to/a/text-corpus -p algorithmName=<"DEFLATE"|"LZ4"|"ZSTD"> -p compressionLevel=<compressionLevel> -p blockSizeBytes=<size> <jmh-params>
java -jar target/benchmarks.jar <Deflater|DeflaterStream|Zstd|ZstdStream>Bench -p inputFilePath=/path/to/a/text-corpus -p compressionLevel=<compressionLevel> -p blockSizeBytes=<size> <jmh-params>
```

Examples:
```
java -jar target/benchmarks.jar QatJavaBench -p inputFilePath=silesia/dickens -p algorithmName="DEFLATE" -p compressionLevel=6 -p blockSizeBytes=65536 -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar QatJavaStreamBench -p inputFilePath=silesia/dickens -p algorithmName="LZ4" -p compressionLevel=6 -p blockSizeBytes=65536 -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar DeflaterBench -p inputFilePath=silesia/dickens -p compressionLevel=6 -p blockSizeBytes=65536 -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar DeflaterStreamBench -p inputFilePath=silesia/dickens -p compressionLevel=6 -p blockSizeBytes=65536 -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar ZstdBench -p inputFilePath=silesia/dickens -p compressionLevel=6 -p blockSizeBytes=65536 -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar ZstdStreamBench -p inputFilePath=silesia/dickens -p compressionLevel=6 -p blockSizeBytes=65536 -f 1 -wi 1 -i 2 -t 1
```

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
