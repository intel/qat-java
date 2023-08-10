## Software-only Configuration
When a system has no QAT hardware available, Qat-Java can still be made to run in a software-only mode. This mode is configured by leveraging the Linux distribution's built-in package manager. Please ensure the machine is using kernel version 6.0 or newer so that compatible library and firmware versions are applied.

- Install QATzip and QATlib on RHEL 8, RHEL 9, CentOS Stream 8, or CentOS Stream 9.

  ```
  sudo dnf install qatzip qatzip-devel qatzip-libs qatlib qatlib-devel qatlib-service qatlib-tests
  ```

- Install QATzip and QATlib on SLES 15 or openSUSE Leap 15.

  ```
  sudo zypper install libqatzip3 qatzip qatzip-devel qatzip qatlib qatlib-devel
  ```

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

