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
java -jar target/benchmarks.jar <benchmark-class> -p file=/path/to/a/text-corpus -p level=<level> <jmh-params>
```

For example:
```
java -jar target/benchmarks.jar QatJava -p file=silesia/dickens -p level=6 -bm thrpt -wi 1 -i 2 -t 1
```

You may get a text corpus for benchmarking from [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
