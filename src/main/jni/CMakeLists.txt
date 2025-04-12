# ******************************************************************************
# Copyright (C) 2023 Intel Corporation
#
# SPDX-License-Identifier: BSD
# ******************************************************************************

cmake_minimum_required(VERSION 3.10.0)
project(qat-java LANGUAGES C)

# Set default build type if not specified
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Define source files
set(SOURCE_FILES
    com_intel_qat_InternalJNI.c
    util.c
)

set(SHARED_LIBRARY_NAME qat-java)

# Find JNI package
find_package(JNI REQUIRED)

# Include JNI headers
include_directories(${JNI_INCLUDE_DIRS})

# Define compiler flags
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Define QAT-ZSTD directory
set(QAT_ZSTD_DIR ${CMAKE_SOURCE_DIR}/external/qat-zstd-plugin)

# Include QAT-ZSTD headers
include_directories(${QAT_ZSTD_DIR}/src)

set(COMMON_FLAGS
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

# Configure git submodule update
find_package(Git QUIET)
if(GIT_FOUND)
    execute_process(
        COMMAND ${GIT_EXECUTABLE} submodule update --init --recursive
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        RESULT_VARIABLE GIT_SUBMODULE_RESULT
        OUTPUT_VARIABLE GIT_SUBMODULE_OUTPUT
        ERROR_VARIABLE GIT_SUBMODULE_ERROR
    )
    
    if(NOT GIT_SUBMODULE_RESULT EQUAL 0)
        message(FATAL_ERROR "Git submodule update failed: ${GIT_SUBMODULE_ERROR}")
    endif()

    # Checkout specific tag for qat-zstd-plugin
    execute_process(
        COMMAND ${GIT_EXECUTABLE} fetch --tags
        WORKING_DIRECTORY ${QAT_ZSTD_DIR}
        RESULT_VARIABLE GIT_FETCH_RESULT
    )
    execute_process(
        COMMAND ${GIT_EXECUTABLE} checkout tags/v0.2.0
        WORKING_DIRECTORY ${QAT_ZSTD_DIR}
        RESULT_VARIABLE GIT_CHECKOUT_RESULT
    )
endif()

# Create custom target for submodule build
add_custom_target(build_submodule
    COMMAND ${CMAKE_COMMAND} -E env ${CMAKE_MAKE_PROGRAM}
    WORKING_DIRECTORY ${QAT_ZSTD_DIR}
    COMMENT "Building qat-zstd-plugin submodule"
)

# Create shared library
add_library(${SHARED_LIBRARY_NAME} SHARED ${SOURCE_FILES})

# Set compile options
target_compile_options(${SHARED_LIBRARY_NAME} PRIVATE ${COMMON_FLAGS})

# Add dependency
add_dependencies(${SHARED_LIBRARY_NAME} build_submodule)

# Link libraries
target_link_libraries(${SHARED_LIBRARY_NAME} PRIVATE
    qatzip
    ${CMAKE_SOURCE_DIR}/external/qat-zstd-plugin/src/libqatseqprod.a
)

# Print configuration
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "Memory sanitizing: ${SANITIZE_MEMORY}")
message(STATUS "Compiler flags: ${COMMON_FLAGS}")
