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
java -jar target/benchmarks.jar com.intel.qat.jmh.BenchmarkDriver /path/to/a/text-corpus
```

You may get a text corpus for benchmarking from the [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia). 
