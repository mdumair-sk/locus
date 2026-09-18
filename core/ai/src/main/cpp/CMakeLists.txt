cmake_minimum_required(VERSION 3.22.1)

project("locus-llama" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure llama.cpp flags before adding subdirectory (CPU-first, minimal static build)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries" FORCE)
set(GGML_CUDA OFF CACHE BOOL "ggml: use CUDA" FORCE)
set(GGML_METAL OFF CACHE BOOL "ggml: use Metal" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "ggml: use Vulkan" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "ggml: use OpenMP" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "ggml: llamafile" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "llama: use openssl" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "llama: build common utils library" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "llama: build tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "llama: build tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "llama: build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "llama: build server" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "llama: build app" FORCE)

add_subdirectory(llama.cpp)

add_library(locus_llama_jni SHARED
    locus_llama_jni.cpp
)

target_include_directories(locus_llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/src
)

target_link_libraries(locus_llama_jni PRIVATE
    llama
    llama-common
    android
    log
)
