#! /bin/sh
rm -r build
mkdir build
cd build
cmake ../
cmake --build . -- -j 6
cd ..