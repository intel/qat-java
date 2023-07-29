#!/bin/bash
mvn clean install

dataset=(dickens mozilla mr nci ooffice osdb reymont samba sao webster xml x-ray)
url=https://sun.aei.polsl.pl/~sdeor/corpus

mkdir samples

for data in ${dataset[@]}; do
  wget ${url}/${data}.bz2 -O samples/${data}.bz2
  bzip2 -d samples/${data}.bz2
  
  numactl -C 2 -N 0 java -jar target/benchmarks.jar com.intel.qat.jmh.BenchmarkDriver samples/${data}

done

rm -rf samples
