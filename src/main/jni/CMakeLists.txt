# ==========================================================================
# Copyright (C) 2023 Intel Corporation
#
# SPDX-License-Identifier: MIT
# ==========================================================================
# use clang-format for both C and Java code
#install clang
# use clang with cmdline option enable address sanitizer

cmake_minimum_required(VERSION 3.16.3) # check minimum required for qat
project(qat-java)
set(CMAKE_BUILD_TYPE Release)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -shared -std=c11 -fsanitize=address -Wall -Wextra -Wpedantic -Werror -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -lqatzip -lnuma -lpthread")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -lqatzip -lnuma -lpthread -pie")

# include Java packages
FIND_PACKAGE(Java REQUIRED)
FIND_PACKAGE(JNI REQUIRED)

LINK_DIRECTORIES(/usr/lib64) #where qat library files are kept
LINK_DIRECTORIES(/usr/local/lib)
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH})
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH2})

include_directories(include)

# Add the source files
file(GLOB SOURCES "*.c")

# Generate the shared library from the sources
add_library(qat-java SHARED ${SOURCES})

# Add dependency for qat library
target_link_libraries(qat-java qatzip)

# treat all warnings as errors , enable warnings
# do not hardocde any path
# check what SDL recommends and include accordingly