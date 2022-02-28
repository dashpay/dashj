#Contrib

This folder contains some required and optional components for dashj
* dashj-bls (required for tests)
* x11 (optional)

Tools such as Java should already be installed.  gcc --version should report 8.1 to 10.3. One of the dependencies of dashj-bls cannot be build with gcc 11.x.

#Requirements for Building

Update the submodules:
```shell
git submodule update --init --recursive
```

## Linux or WSL (eg Ubuntu <= 21.x)
```shell
sudu apt install maven
sudo apt install build-essential
```
## Mac
```shell
homebrew install maven
```
## Windows
```
choco install maven
choco install mingw --version 9.4.0 -y
```