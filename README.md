# Java* Native Interface Binding for Intel® QuickAssist Technology

**Qat-Java** accelerates data compression in Java applications using Intel® [QuickAssist Technology (QAT)](https://www.intel.com/content/www/us/en/architecture-and-technology/intel-quick-assist-technology-overview.html) hardware.

## Supported Algorithms

| Algorithm | Notes |
|-----------|-------|
| DEFLATE   | QAT offload |
| LZ4       | QAT offload |
| Zstandard | QAT offload (only for compression) |

## Prerequisites

### Hardware

- Intel® platform with QAT (e.g., 4th/5th Gen Xeon® Scalable)
- QAT devices configured and accessible (see [QAT Documentation](https://intel.github.io/quickassist/index.html))

### Software

| Dependency | Version |
|------------|---------|
| [QATlib](https://github.com/intel/qatlib) | 24.09.0 |
| [QATzip](https://github.com/intel/QATzip/releases) | 1.3.0 |
| [Zstandard](https://github.com/facebook/zstd) | 1.5.4 |
| [zstd-jni](https://github.com/luben/zstd-jni) | 1.5.6-1+ (if not building from source) |
| Zlib | 1.2.7+ |
| GCC | 8.5+ |
| JDK | 17+ |
| Clang | Required for fuzz testing only |

## Quick Start

```bash
# Build
mvn clean package

# Run an example
java -cp .:./target/classes/:path/to/zstd-jni-1.5.6-1.jar \
  com.intel.qat.examples.QatDeflateExample
```

## Build

```bash
mvn clean package
```

### Maven Goals

| Goal | Description |
|------|-------------|
| `clean` | Remove build artifacts |
| `compile` | Compile source code |
| `test` | Run unit tests |
| `package` | Build JAR files into `target/` |
| `javadoc:javadoc` | Generate API documentation |
| `spotless:check` | Verify code formatting |
| `spotless:apply` | Auto-fix code formatting |
| `site` | Generate Surefire reports in `target/site/` |

## Testing

Run all unit tests:

```bash
mvn test
```

### Fuzz Testing

Requires [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer):

```bash
mvn test -Dfuzzing=true
```

## Examples

Run any class from the `com.intel.qat.examples` package:

```bash
# Linux
java -cp .:./target/classes/:path/to/zstd-jni-1.5.6-1.jar \
  com.intel.qat.examples.<ExampleClass>

# Windows
java -cp .;.\target\classes\;path\to\zstd-jni-1.5.6-1.jar ^
  com.intel.qat.examples.<ExampleClass>
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Authors

- Mulugeta Mammo — [mulugeta.mammo@intel.com](mailto:mulugeta.mammo@intel.com)
- Olasoji Denloye — [olasoji.denloye@intel.com](mailto:olasoji.denloye@intel.com)
- Praveen Nishchal — [praveen.nishchal@intel.com](mailto:praveen.nishchal@intel.com)

Zstandard compression contributions by Jacob Greenfield, Matthew West, and Tommy Parisi.

## License

[BSD License](LICENSE)

---

<sub><b>*</b> Java is a registered trademark of Oracle and/or its affiliates.</sub>
