cmake_minimum_required(VERSION 3.11.0)

project(x11)
find_package(Java COMPONENTS Development)
find_package(JNI REQUIRED)

if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message (STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
endif()

add_library(x11 SHARED aes_helper.c blake.c bmw.c cubehash.c echo.c groestl.c hashblock.cpp keccak.c jh.c luffa.c shavite.c simd.c skein.c)
include_directories(${JNI_INCLUDE_DIRS})
