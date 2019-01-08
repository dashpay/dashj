#
# dashj-bls
#
# requires that libbls.a has been built with the build-bls-signatures.sh script
#
cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_CXX_STANDARD 11)

set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

add_library( # Sets the name of the library.
        dashjbls
        # Sets the library as a shared library.
       SHARED
        # Provides a relative path to your source file(s).
        src/main/cpp/dashj-bls-signature-wrapper.cpp)

# Find Java

find_package(JNI)
if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message (STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
endif()

include_directories(${JNI_INCLUDE_DIRS}
					${CMAKE_SOURCE_DIR}/src/main/
					${CMAKE_SOURCE_DIR}/src/main/cpp
					${CMAKE_SOURCE_DIR}/src/main/cpp/bls-signatures/src
					${CMAKE_BINARY_DIR}/src/main/cpp/bls-signatures/contrib/relic/include
					${CMAKE_SOURCE_DIR}/src/main/cpp/bls-signatures/contrib/relic/include
)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

# This removes the 'lib' prefix from target library name on windows
IF ("${CMAKE_SYSTEM_NAME}" STREQUAL "Windows")
set_target_properties(dashjbls PROPERTIES PREFIX "")
ENDIF()

target_link_libraries( # Specifies the target library.
        dashjbls

        # Links the target library to the bls library
        bls)

add_subdirectory(src/main/cpp)