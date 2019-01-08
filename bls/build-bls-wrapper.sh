#! /bin/sh
rm -r build
mkdir build
cd build
if [[ "$OSTYPE" == "msys" ]]; then
cmake ../ -G "MinGW Makefiles" -DCMAKE_SH="CMAKE_SH-NOTFOUND"
else
cmake ../
fi
cmake --build . -- -j 6
cd ..