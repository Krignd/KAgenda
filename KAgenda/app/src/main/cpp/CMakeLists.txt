cmake_minimum_required(VERSION 3.22.1)
project(kagenda_ai LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
# Unoptimized tensor kernels make debug builds unusably slow, even on an emulator.
add_compile_options("$<$<CONFIG:Debug>:-O2>")

# Keep the regular app installable on 32-bit phones; local inference is 64-bit only.
if(ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "x86")
    add_library(kagenda_ai SHARED unsupported.cpp)
    return()
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
# The NDK arm64 ABI already targets baseline Armv8-A; do not raise the ISA globally.
set(GGML_CPU_ARM_ARCH "" CACHE STRING "" FORCE)
if(KAGENDA_LLAMA_SOURCE)
    add_subdirectory("${KAGENDA_LLAMA_SOURCE}" "${CMAKE_BINARY_DIR}/llama")
else()
    include(FetchContent)
    # llama.cpp b6500, immutable commit and verified upstream source archive.
    FetchContent_Declare(llama
        URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/a7a98e0fffed794396b3fbad4dcdbbc184963645
        URL_HASH SHA256=4d78e6aa4a9124b58dff994416525294fbab2990fe905a640d4cbd26bf563a31)
    FetchContent_MakeAvailable(llama)
endif()
add_library(kagenda_ai SHARED local_ai.cpp)
target_link_libraries(kagenda_ai PRIVATE llama android log)
target_link_options(kagenda_ai PRIVATE "-Wl,-z,max-page-size=16384")
