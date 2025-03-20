## JMH
A set of JMH benchmarks.
```
Name                | Algorithm
--------------------------------------------
QatJavaBench        | DEFLATE, LZ4
QatJavaZstdBench    | ZSTD
QatJavaStreamBench  | DEFLATE, LZ4, ZSTD
```

## Build
To build the benchmark, execute the below command:
```
mvn clean package
```

## Run
To run the benchmark, use the below command:

```
java -jar target/benchmarks.jar QatJavaBench -p file=/path/to/a/text-corpus -p algorithm=<"DEFLATE"|"LZ4"> -p level=<level> -p chunkSize=<size> <jmh-params>
java -jar target/benchmarks.jar QatJavaZstdBench -p file=/path/to/a/text-corpus -p level=<level> -p chunkSize=<size> <jmh-params>
java -jar target/benchmarks.jar QatJavaStreamBench -p file=/path/to/a/text-corpus -p algorithm=<"DEFLATE"|"LZ4"|"ZSTD"> -p level=<level> -p chunkSize=<size> <jmh-params>
```

Examples:
```
java -jar target/benchmarks.jar QatJavaBench -p file=silesia/dickens -p algorithm="DEFLATE" -p level=6 -p chunkSize=65536 -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar QatJavaZstdBench -p file=silesia/dickens -p level=6 -p chunkSize=65536  -f 1 -wi 1 -i 2 -t 1
java -jar target/benchmarks.jar QatJavaStreamBench -p file=silesia/dickens -p algorithm="LZ4" -p level=6 -p chunkSize=65536 -f 1 -wi 1 -i 2 -t 1
```

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
