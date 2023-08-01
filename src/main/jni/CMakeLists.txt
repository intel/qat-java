# ******************************************************************************
# Copyright (C) 2023 Intel Corporation
#
# SPDX-License-Identifier: BSD
# ******************************************************************************

cmake_minimum_required(VERSION 3.16.3)
project(qat-java)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Werror -std=c11 -Wpedantic -shared -fPIC -fstack-protector-strong -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -lqatzip")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -pie")

# Build options
option(SANITIZE_MEMORY "Enables memory sanitizing" OFF)

# Print user's settings
message(STATUS "Memory sanitizing build: ${SANITIZE_MEMORY}")

if (SANITIZE_MEMORY)
	   set(CMAKE_C_COMPILER "/usr/bin/clang")
	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=fuzzer-no-link,address")
else()
	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -D_FORTIFY_SOURCE=2")

endif ()

# include Java packages
FIND_PACKAGE(JNI REQUIRED)

INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH})
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH2})

# Add the source files
file(GLOB SOURCES "*.c")

# Generate the shared library from the sources
add_library(qat-java SHARED ${SOURCES})