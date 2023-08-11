## Java* Native Interface binding for Intel® QuickAssist Technology
Qat-Java library provides accelerated compression and decompression using Intel® QuickAssist Technology (QAT) [QATzip](https://github.com/intel/QATzip) library. For more information about Intel® QAT, refer to the [QAT Programmer's Guide](https://www.intel.com/content/www/us/en/content-details/743912/intel-quickassist-technology-intel-qat-software-for-linux-programmers-guide-hardware-version-2-0.html). Additionally, the online [QAT Hardware User Guide](https://intel.github.io/quickassist/index.html) is a valuable resource that provides guidance on setting up and optimizing Intel® QAT.

Qat-Java currently supports DEFLATE and LZ4 compression algorithms.

### Dependencies
To use Intel® QAT for compression and decompression, Qat-Java requires the following dependencies to be met.

- ***Install QAT driver***. Download the Intel® QAT driver for Linux from [here](https://www.intel.com/content/www/us/en/download/765501/intel-quickassist-technology-driver-for-linux-hw-version-2-0.html) and then follow these [instructions](https://intel.github.io/quickassist/GSG/2.X/index.html).

- ***Install QATZip***. The instructions for installing QATZip library are available [here](https://github.com/intel/QATzip#installation-instructions).

*Important!* In cases where a QAT hardware is not available, Qat-Java can use a software-only execution. The instructions for installing and configuring the dependencies for a software-only execution are documented [here](SOFTWARE_ONLY_CONFIG.md).  

## Building Qat-Java
The following are the prerequisites needed for building the Qat-Java library:

1. Intel® QAT library - explained in the previous section
2. Java 11 or above
3. Build tools - **gcc**, **CMake** , **Maven** and **clang** (for fuzz testing)

> Build instructions were tested using gcc-8.5.0, cmake-3.10.0, mvn-3.5.4 and clang-16.0.0.

To build qat-java, run the below command:
```
mvn clean package
```

You can, for example, run the `ByteArrayExample` example using the below command:
```
java -cp .:./target/classes/ com.intel.qat.examples.ByteArrayExample
```

Or using this command:
```
java --module-path target/classes  -m com.intel.qat/com.intel.qat.examples.ByteArrayExample
```

Other Maven targets include:

- `compile` - builds sources
- `test` - builds and runs tests
- `site` - generates Surefire report into ```target/site```
- `javadoc:javadoc` - builds javadocs into ```target/site/apidocs```
- `package` - builds jar file into ```target``` directory

### Testing
This library supports both junit testing and Fuzz testing.

To run all the unit tests, execute the below command.
```
mvn clean test
```

To include hardware specific tests, execute the below command:
```
mvn clean test -Dhardware.available=true
```

You can also install the [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer/blob/main/CONTRIBUTING.md) tool and run Fuzz tests. 
```
mvn clean test -Dfuzzing=true
```

## CONTRIBUTING ##
Thanks for your interest! Please see the [CONTRIBUTING.md](CONTRIBUTING.md) document for information on how to contribute.
## Contacts ##
For more information on this library, contact Nishchal, Praveen (praveen.nishchal@intel.com) or Mammo, Mulugeta (mulugeta.mammo@intel.com), or  Denloye, Olasoji (olasoji.denloye@intel.com).

&nbsp;

><b id="f1">*</b> Java is a registered trademark of Oracle and/or its affiliates.
