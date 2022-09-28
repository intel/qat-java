DESCRIPTION
-----------
This library is JNI application using QATZip APIs https://github.com/intel-innersource/applications.qat.shims.qatzip.qatzip

INSTALLATION
------------
Follow instructions on https://github.com/intel-innersource/applications.qat.shims.qatzip.qatzip#installation-instructions for QAT installation

BUILD
-----
- Provide sufficient privelege to src/run.sh by chmod +x run.sh
- Execute ./run.sh
- jar file can be found in target/jar and shared object can be found in target/sharedobject

HOW TO USE
----------
1. Shared object (so) file - This should be place in the directory pointed by java.library.path system property
2. jar can be installed using mvn:install command in local maven repository. For example

  'mvn install:install-file -Dfile=<path-to-file> -DgroupId=<group-id> -DartifactId=<artifact-id> -Dversion=<version> -Dpackaging=jar -DgeneratePom=true'
  
API Guide
---------
-> setup() - Establishes QAT session. Both compress and decompress API needs to set up qat session first.
  
-> teardown() - tears down QAT session. This should be called after all compress and/or decompress operations have been completed.
  
-> maxCompressedLength(int srcLen) - provides maximum compressed length for a given uncompressed source length(srcLen).
  
-> nativeSrcDestByteBuff(int srcSize, int destSize) - provides natively allocated (PINNED or COMMON mem based on availability) source (uncompressed) and destination (compressed) bytebuffer. All you need is to provide source and destination buffer size.
  
-> freeNativesrcDestByteBuff() - frees up source and destination bytebuffer.
  
-> compressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest) - compresses uncompressed ByteBuffer(src) for a given source offset(srcOffset) and source length (srcLen) and stores that into destination ByteBuffer(dest). We need to call setup() API before calling compress and call teardown() when we are done with all compress operations.
  
-> decompressByteBuff(ByteBuffer src,int srcOffset, int srcLen, ByteBuffer dest) - decompresses compressed ByteBuffer(src) for a given source offset(srcOffset) and source length (srcLen) and stores that into destination ByteBuffer(dest). We need to call setup() API before calling decompress and call teardown() when we are done with all decompress operations.

  
  
  
