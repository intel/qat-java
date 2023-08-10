## Performance Test
The source files in this directory use JMH to benchmark the performance of Qat-Java and `java.util.zip`. The benchmark downloads and uses files from the [Silesia compression corpus](https://sun.aei.polsl.pl//~sdeor/index.php?page=silesia) to measure compression and decompression speeds as well as compression ratio.

### Build and Run
To build and run the benchmark, execute the below command:
```
./run_bench.sh
```

