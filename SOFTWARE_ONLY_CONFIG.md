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
