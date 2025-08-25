# dashj

> A Java library for working with Dash
> 
[![License](https://img.shields.io/github/license/dashevo/dashj)](https://github.com/dashevo/dashj/blob/master/COPYING)
[![dashevo/dashj](https://tokei.rs/b1/github/dashevo/dashj?category=code)](https://github.com/dashevo/dashj)

| Branch | Tests                                                                                                                                         | Coverage                                                                                                                              | Linting |
|--------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|---------|
| master | [![Tests](https://github.com/dashevo/dashj/workflows/Java%20CI/badge.svg?branch=master)](https://github.com/dashevo/dashj/actions) | [![codecov](https://codecov.io/gh/dashevo/dashj/branch/master/graph/badge.svg)](https://codecov.io/gh/dashevo/android-dpp) | N/A     |

### Welcome to dashj

The dashj library is a Java implementation of the Dash protocol, which allows it to maintain a wallet and send/receive transactions without needing a local copy of Dash Core. It comes with full documentation and some example apps showing how to use it.

This branch is up to date with bitcoinj (https://github.com/bitcoinj/bitcoinj) 0.15.10.

### Technologies

* Java 8+ (needs Java 8 API or Android 6.0 API, compiles to Java 8 bytecode) and Gradle 4.4+ for the `core` module
* Java 8+ and Gradle 5.6 for `tools` and `examples`
* Java 11+ and Gradle 5.6 for the JavaFX-based `wallettemplate`
* [Gradle](https://gradle.org/) - for building the project
* [Google Protocol Buffers](https://github.com/google/protobuf) - for use with serialization and hardware communications

### Getting started

To get started, it is best to have the latest JDK and Maven installed. The HEAD of the `master` branch contains the latest development code and various production releases are provided on feature branches.

#### Building from the command line
Official builds are currently using JDK 8. Our GitHub Actions build and test with JDK 8 and JDK 11.

To initialize the repo after cloning it (this will build the bls shared library): 
```shell
git submodule update  --init --recursive
cd contrib/dashj-bls
mvn package -DskipTests
cd ../..
```
To use the optional x11 native library:
```shell
cd contrib/x11
mkdir build
cd build
cmake ..
cmake --build .
cd ../../..
```

To perform a full build use (this includes the dashjbls shared library):

To perform a full build (*including* JavaDocs and unit/integration *tests*) use JDK 11+.
```shell
./gradlew clean build
```
If you are using Gradle 4.10 or later, the build will automatically include the JavaFX-based `wallettemplate` module. The outputs are under the `build` directory.

To perform a full build *without* unit/integration *tests* use:
```shell
./gradlew clean build -x test
```

To perform a full build and install it in the local maven repository:
```shell
./gradlew assemble
```

to generate a website with useful information like JavaDocs.

The outputs are under the `build` directory.

#### Deployment

To deploy to the maven repository:
```bash
./gradlew clean
./gradlew publish
./gradlew jreleaserDeploy
```
#### Building from an IDE

Alternatively, just import the project using your IDE. [IntelliJ](http://www.jetbrains.com/idea/download/) has Gradle integration built-in and has a free Community Edition. Simply use `File | New | Project from Existing Sources` and locate the `build.gradle` in the root of the cloned project source tree.

The dashjbls library must still be built using the instructions above.

### Example applications

These are found in the `examples` module.

### Where next?

Now you are ready to [follow the tutorial](https://bitcoinj.github.io/getting-started).  Though this is for bitcoinj, there is no equivalent site for dashj.

### Building and Using the Wallet Tool

The **dashj** `tools` subproject includes a command-line Wallet Tool (`wallet-tool`) that can be used to create and manage **dashj**-based wallets (both the HD keychain and SPV blockchain state.) Using `wallet-tool` on Dash's test net is a great way to learn about Dash and **dashj**.

To build an executable shell script that runs the command-line Wallet Tool, use:
```
gradle dashj-tools:installDist
```

You can now run the `wallet-tool` without parameters to get help on its operation:
```
./tools/build/install/wallet-tool/bin/wallet-tool
```

To create a test net wallet file in `~/dashj/dashj-test.wallet`, you would use:
```
mkdir ~/dashj
```
```
./tools/build/install/wallet-tool/bin/wallet-tool --net=TEST --wallet=$HOME/dashj/dashj-test.wallet create
```

To sync the newly created wallet in `~/dashj/dashj-test.wallet` with the test net, you would use:
```
./tools/build/install/wallet-tool/bin/wallet-tool --net=TEST --wallet=$HOME/dashj/dashj-test.wallet sync
```

To dump the state of the wallet in `~/dashj/dashj-test.wallet` with the test net, you would use:
```
./tools/build/install/wallet-tool/bin/wallet-tool --net=TEST --wallet=$HOME/dashj/dashj-test.wallet dump
```

Note: These instructions are for macOS/Linux, for Windows use the `tools/build/install/wallet-tool/bin/wallet-tool.bat` batch file with the equivalent Windows command-line commands and options.

### Example applications

These are found in the `examples` module.

### Where next?

Now you are ready to [follow the tutorial](https://bitcoinj.github.io/getting-started).

### Testing a SNAPSHOT build

Building apps with official releases of **dashj** is covered in the [tutorial](https://bitcoinj.github.io/getting-started).

