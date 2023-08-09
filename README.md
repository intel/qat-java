
# Java* Native Interface binding for Intel® Quick Assist Technology

This library provides accelerated compression and decompression using 
Intel® QuickAssist Technology (QAT) [QATzip](https://github.com/intel/QATzip) library. For more information about Intel® QAT, refer to the [QAT Programmer's Guide](https://www.intel.com/content/www/us/en/download/765501/intel-quickassist-technology-driver-for-linux-hw-version-2-0.html). Additionally, the online [QAT Hardware User Guide](https://intel.github.io/quickassist/index.html) 
is a valuable resource that provides guidance on setting up and optimizing Intel® QAT.

### Install Dependencies (Hardware path, preferred)
This is the first of two approaches that requires Intel® QAT hardware presence on the system. It requires installing QAT driver and configuration described in next section:

- ***Install QAT driver***. All packages are available under [Artifactory](https://www.intel.com/content/www/us/en/download/765501/intel-quickassist-technology-driver-for-linux-hw-version-2-0.html). In one of the packages, follow the steps in the README file to setup on a machine with QAT hardware. Note that only Intel&reg; 4XXX (QAT Gen 4) and newer chipset specific drivers are compatible with this plugin. Also, the kernel version must not be newer than 5.18 or compilation will fail.

- ***Install QATzip***. Follow the instructions [here](https://github.com/intel/QATzip). The number of huge pages may also need to be modified to meet the top-level application's requirements. See the details in [performance-test-with-qatzip](https://github.com/intel-innersource/applications.qat.shims.qatzip.qatzip#performance-test-with-qatzip) for more information.

### Configure Devices (Hardware path)

please follow instruction [outlined](https://github.com/intel/QATzip#installation-instructions) to configure
system.

### Install Dependencies (Software path)

This is the second of two approaches that adds all necessary components to the system. It leverages the Linux distribution's built-in package manager. Please ensure the machine is using kernel version 6.0 or newer so that compatible library and firmware versions are applied. Do not combine the steps in this section with those described in method #2.

- Install QATzip and QATlib on RHEL 8, RHEL 9, CentOS Stream 8, or CentOS Stream 9.

```
sudo dnf install qatzip qatzip-devel qatzip-libs qatlib qatlib-devel qatlib-service qatlib-tests
```

- Install QATzip and QATlib on SLES 15 or openSUSE Leap 15.

```
sudo zypper install libqatzip3 qatzip qatzip-devel qatzip qatlib qatlib-devel
```

### Configure Devices (Software Path)
This is the second of two approaches that enables QAT compression/decompression on the system without Intel QAT hardware. Refer to the previous section for details on installing all dependencies. Do not combine the steps in this section with those described in hardware path method.

- Enable virtualization technology for directed I/O (VT-d) option in the BIOS menu.

- Ensure the machine is using kernel version 6.0 or newer, and enable the Intel IOMMU driver with scalable mode support with `intel_iommu=on,sm_on` included in the kernel boot parameters.

- Add currently logged in non-root user to the *qat* group and repeat as required for other non-root users.

```
sudo usermod -a -G qat `whoami`
```

- Increase the amount of locked memory available (e.g., 500 MB) for currently logged in non-root user and repeat as required for other non-root users. With QAT Gen 4, a minimum of 16 MB is needed for each VF plus whatever is required for the application.

```
echo `whoami` - memlock 500000  | sudo tee -a /etc/security/limits.conf > /dev/null
```

- Create the settings file used by all QAT devices.

```
sudo touch /etc/sysconfig/qat
```

- Enable the compression/decompression service and specify the engine distribution. This is accomplished by using the `POLICY` and `ServicesEnabled` fields. The values used will depend on the requirements of the applications using QAT. For example, the settings shown below only enable compression/decompression while allowing each application process to access at most 2 VFs. With QAT Gen 4, each device is connected to 16 VFs, so this example will have a limit of 32 application processes able to use QAT if there are 4 devices on the system. As an aside, `POLICY=0` means each QAT device's VFs are available to exactly one application process.

```
POLICY=2
ServicesEnabled=dc
```

- Reboot the machine to pick up the new settings. This is only required after installing, re-installing, or updating dependencies. In all other situations, this step can be skipped.

```
sudo reboot
```

- Restart the QAT service and check that all devices are setup properly.

```
sudo systemctl restart qat
sudo systemctl status qat
```
## Build & Run ##

### PREREQUISITES TO BUILD ###
The following are the prerequisites for building this Java library:

1. Intel® QAT library - explained in the previous section
2. Java 11 or above
3. Build tools - **gcc**, **CMake** , **Maven** and **clang (required for fuzz testing)**

### PREREQUISITES TO RUN ###
This library assumes the availability of Intel® QAT hardware (https://intel.github.io/quickassist/index.html).

To build qat-java, run the below command:
```
    $ mvn clean package
```

Other Maven targets include:

- `compile` - builds sources
- `test` - builds and runs tests
- `site` - generates Surefire report into ```target/site```
- `javadoc:javadoc` - builds javadocs into ```target/site/apidocs```
- `package` - builds jar file into ```target``` directory

### LIBRARY TESTING ###
This library supports both junit testing and Fuzz testing.

To run all the unit tests, execute the following command. To include hardware specific test, add ``-Dhardware.available=true``. 
```
mvn clean test
```

You can also install the [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer/blob/main/CONTRIBUTING.md) tool and run Fuzz tests. 
```
mvn clean test -Dfuzzing=true
```

### USING THIS LIBRARY IN EXISTING JAVA APPLICATIONS ###
To use this library in your Java application, build the qat-java jar and include
its location in your Java classpath.  For example:
   ```
   $ mvn package
   $ javac -cp .:<path>/qat-java/target/qat-java-<version>.jar <source>
   $ java -cp .:<path>/qat-java/target/qat-java-<version>.jar <class>
   ```

Alternatively, include qat-java's `target/classes` directory in your Java classpath and the
`target/cppbuild` directory in your `java.library.path`.  For example:
    ```
    $ mvn compile
    $ javac -cp .:<path>/qat-java/target/classes <source>
    $ java -cp .:<path>/qat-java/target/classes -Djava.library.path=<path>/qat-java/target/cbuild <class>
    ```
## CONTRIBUTING ##
Thanks for your interest! Please see the [CONTRIBUTING.md](CONTRIBUTING.md) document for information on how to contribute.
## Contacts ##
For more information on this library, contact Nishchal, Praveen (praveen.nishchal@intel.com) or Mammo, Mulugeta (mulugeta.mammo@intel.com), or  Denloye, Olasoji (olasoji.denloye@intel.com).

&nbsp;

><b id="f1">*</b> Java is a registered trademark of Oracle and/or its affiliates.
