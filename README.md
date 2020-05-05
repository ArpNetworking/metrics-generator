Metrics Generator
=================

<a href="https://raw.githubusercontent.com/ArpNetworking/metrics-generator/master/LICENSE">
    <img src="https://img.shields.io/hexpm/l/plug.svg"
         alt="License: Apache 2">
</a>
<a href="https://travis-ci.com/ArpNetworking/metrics-generator">
    <img src="https://travis-ci.com/ArpNetworking/metrics-generator.svg?branch=master"
         alt="Travis Build">
</a>
<a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22generator%22">
    <img src="https://img.shields.io/maven-central/v/com.arpnetworking.metrics/generator.svg"
         alt="Maven Artifact">
</a>

Generates realistic-looking metrics data for use in system testing.

Usage
-----

### Integrated Library

#### Add Dependency

Determine the latest version of the commons in [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22generator%22).

##### Maven

Add a dependency to your pom:

```xml
<dependency>
    <groupId>com.arpnetworking.metrics</groupId>
    <artifactId>generator</artifactId>
    <version>VERSION</version>
</dependency>
```

The Maven Central repository is included by default.

##### Gradle

Add a dependency to your build.gradle:

    compile group: 'com.arpnetworking.metrics', name: 'metrics', version: 'VERSION'

Add the Maven Central Repository into your *build.gradle*:

```groovy
repositories {
    mavenCentral()
}
```

##### SBT

Add a dependency to your project/Build.scala:

```scala
val appDependencies = Seq(
    "com.arpnetworking.metrics" % "metrics" % "VERSION"
)
```

The Maven Central repository is included by default.

#### Invocation

The following function generates the specified data. Specify:

* The number of units of work.
* The number of unique metrics (aka names).
* The number of samples per metric per unit of work.

```java
public void generate(final int uowCount, final int metricsCount, final int samplesCount) {
        final RandomGenerator random = new MersenneTwister(1298); // Just pick a number as the seed.
        final Path path = Paths.get("build/tmp/perf/application-generated-sample.log");

        final DateTime start = DateTime.now().hourOfDay().roundFloorCopy();
        final DateTime stop = start.plusMinutes(10);
        final TestFileGenerator generator = new TestFileGenerator.Builder()
                .setRandom(random)
                .setUnitOfWorkCount(uowCount)
                .setNamesCount(metricsCount)
                .setSamplesCount(samplesCount)
                .setStartTime(start)
                .setEndTime(stop)
                .setFileName(path)
                .setClusterName("application_cluster")
                .setServiceName("application_service")
                .build();
        generator.generate();
}
```

### Stand Alone Application

#### Installation

The metrics-generator artifacts may be installed and executed as a stand-alone application. You can find the artifacts from the build in *target/appassembler/* and these should be copied to an appropriate directory on your target host(s).

#### Execution

There are generated scripts in *target/appassembler/bin/* to run the metrics-generator: *metrics-generator* (Linux) and *metrics-generator.bat* (Windows).  One of these should be executed on system start with appropriate parameters; for example:

    metrics-generator> ./target/appassembler/bin/metrics-generator --continuous

#### Configuration

All configuration is provided on the command line.

* By default without any arguments the metrics generator will produce a set of test files in the current directory.
* Specifying the "--continuous" argument the generator will produce a continuous stream of metrics into a single file.

Building
--------

Prerequisites:
* _None_

Building:

    metrics-generator> ./jdk-wrapper.sh ./mvnw verify

To use the local version you must first install it locally:

    metrics-generator> ./jdk-wrapper.sh ./mvnw install

You can determine the version of the local build from the pom file.  Using the local version is intended only for testing or development.

You may also need to add the local repository to your build in order to pick-up the local version:

* Maven - Included by default.
* Gradle - Add *mavenLocal()* to *build.gradle* in the *repositories* block.
* SBT - Add *resolvers += Resolver.mavenLocal* into *project/plugins.sbt*.

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Groupon Inc., 2014
