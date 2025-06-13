# ******************************************************************************
# Copyright (C) 2023 Intel Corporation
#
# SPDX-License-Identifier: BSD
# ******************************************************************************

cmake_minimum_required(VERSION 3.10)

project(qat-java LANGUAGES C)

# Set default build type early
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# Define shared library name
set(SHARED_LIBRARY_NAME qat-java)

# Source files
set(SOURCE_FILES
    com_intel_qat_InternalJNI.c
    util.c
)

# Set C standard and compile flags
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Define common compile flags
set(COMMON_FLAGS "")
list(APPEND COMMON_FLAGS
    -O2
    -Wall
    -Wextra
    -Wpedantic
    -Werror
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
)

# Sanitizer option
option(SANITIZE_MEMORY "Enable memory sanitizing" OFF)
if(SANITIZE_MEMORY)
    if(CMAKE_C_COMPILER_ID MATCHES "Clang")
        list(APPEND COMMON_FLAGS -fsanitize=fuzzer-no-link,address)
    else()
        message(WARNING "Memory sanitizing requires Clang compiler")
    endif()
endif()

# Find JNI
find_package(JNI REQUIRED)

# Create shared library
add_library(${SHARED_LIBRARY_NAME} SHARED ${SOURCE_FILES})

# Include directories
target_include_directories(${SHARED_LIBRARY_NAME} PRIVATE
    ${JNI_INCLUDE_DIRS}
)

# Compile options
target_compile_options(${SHARED_LIBRARY_NAME} PRIVATE ${COMMON_FLAGS})

# Link libraries
find_library(QATZIP_LIB qatzip)
if(NOT QATZIP_LIB)
    message(FATAL_ERROR "Could not find system library: qatzip")
endif()

target_link_libraries(${SHARED_LIBRARY_NAME} PRIVATE
    ${QATZIP_LIB}
    pthread
)

# Properties
set_target_properties(${SHARED_LIBRARY_NAME} PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    INSTALL_RPATH "$ORIGIN"
)

# Print configuration
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "Memory sanitizing: ${SANITIZE_MEMORY}")
message(STATUS "Compiler flags: ${COMMON_FLAGS}")

