#!/bin/bash
mvn clean install

dataset=(dickens mozilla mr nci ooffice osdb reymont samba sao webster xml x-ray)
url=https://sun.aei.polsl.pl//~sdeor/corpus

mkdir samples

for data in ${dataset[@]}; do
  wget ${url}/${data}.bz2 -O samples/${data}.bz2
  bzip2 -d samples/${data}.bz2
  
  file_size=$(ls -l samples/${data} | awk '{print $5}')

  java -jar target/benchmarks.jar BenchmarkWithFile.QatCompressor \
  -p fileName=samples/${data}

  echo "compressed size ${data}.qat.gz: " $(ls -l samples/${data}.qat.gz | awk '{print $5}')
   
  sleep 10

  java -jar target/benchmarks.jar BenchmarkWithFile.QatDecompressor \
  -p fileName=samples/${data}.qat.gz -p srcFileSize=${file_size}

  sleep 10
<<COMMENT
  java -jar target/benchmarks.jar BenchmarkWithFile.JavaZipDeflater \
  -p fileName=samples/${data}

  sleep 10

  java -jar target/benchmarks.jar BenchmarkWithFile.JavaZipInflater \
  -p fileName=samples/${data}.javazip.gz -p srcFileSize=${file_size}

  sleep 10
COMMENT
done

rm -rf samples
