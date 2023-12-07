# ******************************************************************************
# Copyright (C) 2023 Intel Corporation
#
# SPDX-License-Identifier: BSD
# ******************************************************************************

cmake_minimum_required(VERSION 3.10.0)
project(qat-java)

# Set the source files and shared library name
file(GLOB SOURCE_FILES "*.c")
set(SHARED_LIBRARY_NAME qat-java)

# Include Java packages and directories
FIND_PACKAGE(JNI REQUIRED)
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH})
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH2})

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -Wall -Wextra -Wpedantic -Werror -fstack-protector-strong")

# Add a SANITIZE_MEMORY option
option(SANITIZE_MEMORY "Enables memory sanitizing" OFF)

message(STATUS "Memory sanitizing build: ${SANITIZE_MEMORY}")

if (SANITIZE_MEMORY)
	   set(CMAKE_C_COMPILER clang)
	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=fuzzer-no-link,address")
else()
	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_FORTIFY_SOURCE=2")
endif()

add_library(${SHARED_LIBRARY_NAME} SHARED ${SOURCE_FILES})
target_link_libraries(${SHARED_LIBRARY_NAME} -lqatzip -lqatseqprod)
