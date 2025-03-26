# ******************************************************************************
# Copyright (C) 2023 Intel Corporation
#
# SPDX-License-Identifier: BSD
# ******************************************************************************

cmake_minimum_required(VERSION 3.10.0)
project(qat-java)

set(SOURCE_FILES 
    com_intel_qat_InternalJNI.c
    util.c
)
set(SHARED_LIBRARY_NAME qat-java)

FIND_PACKAGE(JNI REQUIRED)
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH})
INCLUDE_DIRECTORIES(${JAVA_INCLUDE_PATH2})

# Pull submodule
execute_process(
  COMMAND /bin/sh -c "git submodule update --init --recursive && cd ${CMAKE_SOURCE_DIR}/external/qat-zstd-plugin  && git fetch --tags && git checkout tags/v0.2.0"
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  RESULT_VARIABLE GIT_RESULT
  OUTPUT_VARIABLE GIT_OUTPUT
  ERROR_VARIABLE GIT_ERROR
)
if(NOT GIT_RESULT EQUAL 0)
  message(FATAL_ERROR "Failed to update git submodules: ${GIT_ERROR}")
endif()

# Add custom target
add_custom_target(build_submodule ALL
    COMMAND ${CMAKE_MAKE_PROGRAM} -C ${CMAKE_SOURCE_DIR}/external/qat-zstd-plugin
    COMMENT "Building submodule qat-zstd-plugin via its Makefile"
    VERBATIM
)

# Add the source files to the project
add_library(${SHARED_LIBRARY_NAME} SHARED ${SOURCE_FILES})

# Build the submodule before the shared library
add_dependencies(${SHARED_LIBRARY_NAME} build_submodule)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -O2 -Wall -Wextra -Wpedantic -Werror -fstack-protector-strong")

message("CMAKE_C_FLAGS: ${CMAKE_C_FLAGS}")

# Add a SANITIZE_MEMORY option
option(SANITIZE_MEMORY "Enables memory sanitizing" OFF)

# Print user's settings
message(STATUS "Memory sanitizing build: ${SANITIZE_MEMORY}")

if (SANITIZE_MEMORY)
	   set(CMAKE_C_COMPILER "/usr/bin/clang")
	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=fuzzer-no-link,address")
else()
	   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_FORTIFY_SOURCE=2")
endif()

target_link_libraries(${SHARED_LIBRARY_NAME} PRIVATE 
    qatzip
    ${CMAKE_CURRENT_SOURCE_DIR}/external/qat-zstd-plugin/src/libqatseqprod.a
)

