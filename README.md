## Java* Native Interface binding for Intel® Quick Assist Technology
Qat-Java library provides accelerated compression and decompression using Intel® QuickAssist Technology (QAT) [QATzip](https://github.com/intel/QATzip) library. For more information about Intel® QAT, refer to the [QAT Programmer's Guide](https://www.intel.com/content/www/us/en/download/765501/intel-quickassist-technology-driver-for-linux-hw-version-2-0.html). Additionally, the online [QAT Hardware User Guide](https://intel.github.io/quickassist/index.html) is a valuable resource that provides guidance on setting up and optimizing Intel® QAT.

### Dependencies
To use Intel® QAT for compression and decompression, Qat-Java requires the following dependencies to be met.

- ***Install QAT driver***. All packages are available under [Artifactory](https://www.intel.com/content/www/us/en/download/765501/intel-quickassist-technology-driver-for-linux-hw-version-2-0.html). In one of the packages, follow the steps in the README file to setup on a machine with QAT hardware. Note that only Intel&reg; 4XXX (QAT Gen 4) and newer chipset specific drivers are compatible with this plugin. Also, the kernel version must not be newer than 5.18 or compilation will fail.

- ***Install QATzip***. Follow the instructions [here](https://github.com/intel/QATzip#installation-instructions). The number of huge pages may also need to be modified to meet the top-level application's requirements. See the details in [performance-test-with-qatzip](https://github.com/intel-innersource/applications.qat.shims.qatzip.qatzip#performance-test-with-qatzip) for more information.

*Important!* In cases where a QAT hardware is not available, Qat-Java can use a software-only execution. The instructions for installing and configuring the dependencies for a software-only execution are documented [here](SOFTWARE_ONLY_CONFIG.md).  

## Building Qat-Java
The following are the prerequisites needed for building the Qat-Java library:

1. Intel® QAT library - explained in the previous section
2. Java 11 or above
3. Build tools - **gcc**, **CMake** , **Maven** and **clang** (for fuzz testing)

To build qat-java, run the below command:
```
mvn clean package
```

You can, for example, run the ByteArrayExample using the below command:
```
java --module-path target/qat-java-1.0.0.jar -m com.intel.qat/com.intel.qat.examples.ByteArrayExample
```

Or this command:
```
java -cp .:./target/qat-java-1.0.0.jar com.intel.qat.examples.ByteArrayExample
```

Other Maven targets include:

- `compile` - builds sources
- `test` - builds and runs tests
- `site` - generates Surefire report into ```target/site```
- `javadoc:javadoc` - builds javadocs into ```target/site/apidocs```
- `package` - builds jar file into ```target``` directory

### Testing
This library supports both junit testing and Fuzz testing.

To run all the unit tests, execute the following command. To include hardware specific test, add ``-Dhardware.available=true``. 
```
mvn clean test
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
