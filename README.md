## Java* Native Interface binding for Intel® QuickAssist Technology
Qat-Java is a library that accelerates data compression using Intel® [QuickAssist Technology](https://www.intel.com/content/www/us/en/architecture-and-technology/intel-quick-assist-technology-overview.html). For more information about Intel® QAT and installation instructions, refer to the [QAT Documentation](https://intel.github.io/quickassist/index.html).

Qat-Java currently supports DEFLATE, LZ4, and ZStandard compression algorithms.

## Prerequisites
This release was validated on the following:

* QATlib, version [24.09.0](https://github.com/intel/qatlib).
* QATzip, version [1.3.0](https://github.com/intel/QATzip/releases) and its dependencies.
* ZSTD library, version [1.5.4](https://github.com/facebook/zstd).
* GCC, version 8.5+.
* JDK 17+.
* clang (for fuzz testing).

## Build
To build qat-java, run the below command:
```
mvn clean package
```

Other Maven targets include:

- `clean` &mdash; cleans.
- `compile` &mdash; builds sources.
- `test` &mdash; builds and runs tests.
- `package` &mdash; builds and writes jar files into ```target``` directory.
- `javadoc:javadoc` &mdash; generates javadocs. 
- `spotless:check` &mdash; check if source code is formatted well.
- `spotless:apply` &mdash; fixes source code format issues.
- `site` &mdash; generates Surefire reports into ```target/site```.

> The build instructions were tested using gcc-8.5.0, cmake-3.10.0, mvn-3.5.4 and clang-16.0.0.

## Testing
To run all the unit tests, execute the below command.
```
mvn test
```

You can also install the [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer/blob/main/CONTRIBUTING.md) tool and run Fuzz tests. 
```
mvn test -Dfuzzing=true
```

## Examples
You can run the examples in the `com.intel.qat.examples`, use the below command:
```
java -cp .:./target/classes/:path/to/zstd-jni-1.5.6-1.jar com.intel.qat.examples.<example-class>
```

## Authors
* Mulugeta Mammo (mulugeta.mammo@intel.com)
* Olasoji Denloye (olasoji.denloye@intel.com)
* Praveen Nishchal (praveen.nishchal@intel.com)

with contributions on ZStandard compression by:
* Jacob Greenfield, Matthew West, and Tommy Parisi

## Contributing
Thanks for your interest! Please see the [CONTRIBUTING.md](CONTRIBUTING.md) document for information on how to contribute.

## Contacts ##
For more information on this library, contact Mammo, Mulugeta (mulugeta.mammo@intel.com) or  Denloye, Olasoji (olasoji.denloye@intel.com).

&nbsp;

> <b id="f1">*</b> Java is a registered trademark of Oracle and/or its affiliates.
