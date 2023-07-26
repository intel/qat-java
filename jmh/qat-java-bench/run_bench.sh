#!/bin/bash
mvn clean install

dataset=(mozilla) # dickens mozilla mr nci ooffice osdb reymont samba sao webster xml x-ray)
url=https://sun.aei.polsl.pl//~sdeor/corpus

for data in ${dataset[@]}; do
  wget ${url}/${data}.bz2 -O samples/${data}.bz2
  bzip2 -d samples/${data}.bz2
  
  file_size=$(ls -l samples/${data} | awk '{print $5}')

  java -jar target/benchmarks.jar BenchmarkWithFile.QatCompressor \
  -p pinMemSize=65536 -p fileName=samples/${data}

  sleep 10

  java -jar target/benchmarks.jar BenchmarkWithFile.QatDecompressor \
  -p pinMemSize=65536 -p fileName=samples/${data}.qat.gz -p srcFileSize=${file_size}

<<COMMENT
  sleep 10

  java -jar target/benchmarks.jar BenchmarkWithFile.JavaZipDeflater \
  -p fileName=samples/${data}

  sleep 10

  java -jar target/benchmarks.jar BenchmarkWithFile.JavaZipInflater \
  -p fileName=samples/${data}.javazip.gz -p srcFileSize=${file_size}

  sleep 10
COMMENT
done

