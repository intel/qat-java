## JMH
A set of JMH benchmarks.
```
Name              | Algorithm
--------------------------------------------
QatJavaBench      | DEFLATE, gzip-ext format
JavaUtilZipBench  | DEFLATE, zlib format
```

## Build
To build the benchmark, execute the below command:
```
mvn clean package
```

## Run
To run the benchmark, use the below command:

```
java -jar target/benchmarks.jar [benchmark-class] -p file=/path/to/a/text-corpus -p level=<level> -p chunkSize=<size> <jmh-params>
```

Example:
```
java -jar target/benchmarks.jar QatJavaBench -p file=silesia/dickens -p level=6 -p chunkSize=65536 -f 1 -wi 1 -i 2 -t 1
```

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
