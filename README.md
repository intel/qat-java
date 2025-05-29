## Java* Native Interface Binding for Intel® QuickAssist Technology

**Qat-Java** is a library that accelerates data compression using Intel® [QuickAssist Technology (QAT)](https://www.intel.com/content/www/us/en/architecture-and-technology/intel-quick-assist-technology-overview.html).  
For more details on Intel® QAT and installation instructions, refer to the [QAT Documentation](https://intel.github.io/quickassist/index.html).

Qat-Java currently supports the following compression algorithms:
- **DEFLATE**
- **LZ4**

## Prerequisites

This release was validated with the following tools and libraries:

- [QATlib v24.09.0](https://github.com/intel/qatlib)
- [QATzip v1.3.0](https://github.com/intel/QATzip/releases) and its dependencies
- GCC 8.5 or newer
- JDK 17 or newer
- Clang (for fuzz testing)

## Build

To build Qat-Java, run:

```
mvn clean package
```

### Additional Maven Targets

In addition to `mvn clean package`, the following Maven goals are available:

- `clean` — Cleans the build directory.  
- `compile` — Compiles the source code.  
- `test` — Compiles and runs all unit tests.  
- `package` — Packages the compiled classes into JAR files (in the `target/` directory).  
- `javadoc:javadoc` — Generates Javadoc API documentation.  
- `spotless:check` — Checks that the source code is properly formatted.  
- `spotless:apply` — Automatically fixes code formatting issues.  
- `site` — Generates Surefire test reports in `target/site/`.

## Testing

To run all unit tests:

```
mvn test
```

## Fuzz Testing

To enable fuzz testing, install the [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer/blob/main/CONTRIBUTING.md) tool and run:

```
mvn test -Dfuzzing=true
```

## Examples

To run an example from the `com.intel.qat.examples` package, use the following command:

```
java -cp .:./target/classes/ com.intel.qat.examples.<ExampleClass>
```

## Authors

- **Mulugeta Mammo** — [mulugeta.mammo@intel.com](mailto:mulugeta.mammo@intel.com)  
- **Olasoji Denloye** — [olasoji.denloye@intel.com](mailto:olasoji.denloye@intel.com)  
- **Praveen Nishchal** — [praveen.nishchal@intel.com](mailto:praveen.nishchal@intel.com)

**Zstandard compression contributions** by:
- Jacob Greenfield  
- Matthew West  
- Tommy Parisi

## Contributing

Thank you for your interest in contributing!  
Please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) document for details on how to get involved.

## Contact

For questions or more information about this library, contact:  
- [mulugeta.mammo@intel.com](mailto:mulugeta.mammo@intel.com)  
- [olasoji.denloye@intel.com](mailto:olasoji.denloye@intel.com)

<sub><b id="f1">*</b> Java is a registered trademark of Oracle and/or its affiliates.</sub>
