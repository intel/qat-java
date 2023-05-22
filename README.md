
DESCRIPTION
-----------
This library is JNI application using QATZip APIs https://github.com/intel/QATzip

INSTALLATION
------------
Follow instructions on https://github.com/intel/QATzip/blob/master/README.md for QAT installation

BUILD
-----
- mvn clean
- mvn package
- jar file can be found in target and shared object can be found in target/cbuild

HOW TO USE
----------
1. Shared object (so) file - This should be place in the directory pointed by java.library.path system property
2. jar can be installed using mvn:install command in local maven repository. For example

  'mvn install:install-file -Dfile=<path-to-file> -DgroupId=<group-id> -DartifactId=<artifact-id> -Dversion=<version> -Dpackaging=jar -DgeneratePom=true'
  
  
  
  
