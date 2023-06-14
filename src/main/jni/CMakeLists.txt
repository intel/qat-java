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
#set(CMAKE_BUILD_TYPE Release)
set (CMAKE_C_COMPILER "/usr/bin/clang")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=fuzzer-no-link,address -shared -std=c11 -Wall -Wextra -Wpedantic -Werror -fPIC -fstack-protector-strong -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -lqatzip -lnuma -lpthread")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -lqatzip -lnuma -lpthread -pie")

# Build options
option(SANITIZE_MEMORY "Enables memory sanitizing" OFF)

# Print user's settings
message(STATUS "Memory sanitizing build: ${SANITIZE_MEMORY}")

#if (SANITIZE_MEMORY)
#       message("------------- SANITIZE MEMORY -------------");
#	   set (CMAKE_C_COMPILER "/usr/bin/clang")
#	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=fuzzer-no-link,address")
#else()
#	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -D_FORTIFY_SOURCE=2")

#endif ()

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