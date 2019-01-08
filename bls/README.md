# dashj-bls
DashJ Wrapper for bls-signatures

Building the Java Wrapper:

It builds automatically on Linux.  On Windows Cmake (https://cmake.org/download/) and MinGW must be installed (http://mingw-w64.org/doku.php/download/mingw-builds)

##Windows (building through maven)
1.  Install MinGW
2.  `mvn clean install` from a windows command prompt

##Windows (building manually with CMake GUI)
1. Install MinGW and CMake.
2. Use the CMake GUI to configure and generate the build folder
3. `cmake --build . -- -j 6` from the build folder using the Git Bash Shell

#Windows (buildig manually with CMake)
1) Install MinGW and CMake.
2) Launch `Git Bash`
3) `git submodule update  --init --recursive`
4) `cd bls`
5) `mkdir build`
6) `cd build`
7) `cmake ../ -G "MinGW Makefiles" -DCMAKE_SH="CMAKE_SH-NOTFOUND"`
8) `cmake --build . -- -j 6` 
9) Include the destinatation lib folder in java.library.path

