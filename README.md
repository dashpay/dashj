# dashj

> A Java library for working with Dash

[![Build Status](https://travis-ci.com/dashevo/dashj.svg?token=Pzix7aqnMuGS9c6BmBz2&branch=master)](https://travis-ci.com/dashevo/dashj)

### Welcome to dashj

The dashj library is a Java implementation of the Dash protocol, which allows it to maintain a wallet and send/receive transactions without needing a local copy of Dash Core. It comes with full documentation and some example apps showing how to use it.

This branch is up to date with bitcoinj 0.15.4.

### Technologies

* Java 8 for the core modules, Java 8 for everything else
* [Maven 3+](http://maven.apache.org) - for building the project
* [Google Protocol Buffers](https://github.com/google/protobuf) - for use with serialization and hardware communications

### Getting started

To get started, it is best to have the latest JDK and Maven installed. The HEAD of the `master` branch contains the latest development code and various production releases are provided on feature branches.

#### Building from the command line
To initialize the repo after cloning it: 
```
git submodule update  --init --recursive
```
To perform a full build use (this includes the dashjbls shared library):
```
cd bls
mvn clean package -Pbuild-bls-only -pl :dashj-bls -DskipTests -Dmaven.javadoc.skip=true --settings ../maven-settings.xml
cd ..
mvn clean package --settings maven-settings.xml
```
To perform a full build without building the bls shared library and skip the test:
```

mvn clean package -Pno-build-bls -DskipTests --settings maven-settings.xml
```
To perform a full build and install it in the local maven repository:
```
mvn clean install --settings maven-settings.xml
```
You can also run
```
mvn site:site
```
to generate a website with useful information like JavaDocs.

The outputs are under the `target` directory.

#### Deployment

To deploy to the maven repository:

mvn clean deploy -DskipTests -P release

#### Building from an IDE

Alternatively, just import the project using your IDE. [IntelliJ](http://www.jetbrains.com/idea/download/) has Maven integration built-in and has a free Community Edition. Simply use `File | Import Project` and locate the `pom.xml` in the root of the cloned project source tree.

The dashjbls library must still be built with `mvn`.

### Example applications

These are found in the `examples` module.

### Where next?

Now you are ready to [follow the tutorial](https://bitcoinj.github.io/getting-started).  Though this is for bitcoinj, there is no equivalent site for dashj.
