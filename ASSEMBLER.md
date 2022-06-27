# Assemblers

Assemblers are responsible for gathering custodial and supplemental resources associated with a submission and returning
a stream of those resources - a `PackageStream` - according to a packaging specification. Deposit Services will then
stream the package to a downstream repository via a `Transport`.

## Use Case

Why develop an `Assembler`? Broadly speaking, an `Assembler` implementation is required for every packaging
specification you wish to support. For example, if you want to
produce [BagIt packages](https://tools.ietf.org/html/rfc8493)
and [DSpace METS packages](https://wiki.duraspace.org/display/DSPACE/DSpaceMETSSIPProfile), you would need
two `Assembler` implementations, each one responsible for producing packages that comport with their respective
specifications.

Another reason to develop an `Assembler` is to control how the metadata of a submission is mapped into your package. For
example, if your DSpace installation requires custom metadata elements, you would need to develop or extend an
existing `Assembler` (for example, by implementing a custom Package Provider) to include the custom metadata as
appropriate to your environment.

Fortunately, you should be able to extend a base `Assembler` class without having to write something from scratch.

## Quick Start

1. Create a Maven project for your Assembler (use any Maven groupId and artifactId).
2. Create a dependency on the core Deposit Services parent POM with `<scope>import</scope>`.

            <dependency>
                <groupId>org.eclipse.pass.deposit</groupId>
                <artifactId>deposit-parent</artifactId>
                <version>${deposit-services.version}</version>
                <scope>import</scope>
                <type>pom</type>
            </dependency>

3. Add the following dependencies (feel free to put them in a `<dependencyManagement>` element, and then use them as
   concrete dependencies when needed):

        <dependency>
            <groupId>org.eclipse.pass.deposit</groupId>
            <artifactId>deposit-model</artifactId>
            <version>${deposit-services.version}</version>
        </dependency>

        <dependency>
            <groupId>org.eclipse.pass.deposit</groupId>
            <artifactId>assembler-api</artifactId>
            <version>${deposit-services.version}</version>
        </dependency>

        <dependency>
            <groupId>org.eclipse.pass.deposit</groupId>
            <artifactId>shared-assembler</artifactId>
            <version>${deposit-services.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>

        <dependency>
            <groupId>org.eclipse.pass.deposit</groupId>
            <artifactId>shared-assembler</artifactId>
            <version>${deposit-services.version}</version>
            <classifier>tests</classifier>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.eclipse.pass.deposit</groupId>
            <artifactId>shared-resources</artifactId>
            <version>${deposit-services.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.eclipse.pass.deposit</groupId>
            <artifactId>fedora-builder</artifactId>
            <version>${deposit-services.version}</version>
            <scope>test</scope>
        </dependency>

4. Create your Assembler class that extends `org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler`
5. Create your Package Provider class that
   implements `org.dataconservancy.pass.deposit.assembler.shared.PackageProvider`

To get started with testing:
Create your package verifier that implements `org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier`
Extend and implement `org.dataconservancy.pass.deposit.assembler.shared.ThreadedAssemblyIT`

# API Overview

## Assembler API

The main entrypoint into the Assembler API is on
the [`Assembler`](https://github.com/OA-PASS/deposit-services/blob/master/assembler-api/src/main/java/org/dataconservancy/pass/deposit/assembler/Assembler.java)
interface:
`PackageStream assemble(DepositSubmission, Map<String, Object>)`
where the `DepositSubmission` is the internal representation of a `Submission`, and the `Map` is a set of package
options read from `repositories.json`.

The [`AbstractAssembler`](https://github.com/OA-PASS/deposit-services/blob/master/shared-assembler/src/main/java/org/dataconservancy/pass/deposit/assembler/shared/AbstractAssembler.java)
provides an implementation of `assemble(DepositSubmission, Map)`, and requires its subclasses to implement:

    PackageStream createPackageStream(DepositSubmission, List<DepositFileResource>, MetadataBuilder, ResourceBuilderFactory, Map<String, Object>)

Where the `List<DepositFileResource>` is the custodial content of the submission, the `MetadataBuilder` allowing
modification of the package-level metadata, and the `ResourceBuilderFactory` used to generate an instance
of `ResourceBuilder` for each `DepositFileResource`.

The primary benefit of extending `AbstractAssembler` is that the logic for identifying the custodial resources in the
submission and creating their representation as `List<DepositSubmission>` is shared. Subclasses of `AbstractAssembler`
must instantiate and return a `PackageStream`.

Examples: [`DspaceMetsAssembler`](https://github.com/OA-PASS/jhu-package-providers/blob/master/jscholarship-package-provider/src/main/java/edu/jhu/library/pass/deposit/provider/j10p/J10PDspaceMetsAssembler.java)
, [`NihmsAssembler`](https://github.com/OA-PASS/jhu-package-providers/blob/master/nihms-package-provider/src/main/java/org/dataconservancy/pass/deposit/provider/nihms/NihmsAssembler.java)
, [`BagItAssembler`](https://github.com/OA-PASS/jhu-package-providers/blob/master/bagit-package-provider/src/main/java/edu/jhu/library/pass/deposit/provider/bagit/BagItAssembler.java)

## PackageStream API

Assemblers are invoked by Deposit Services and return
a [`PackageStream`](https://github.com/OA-PASS/deposit-services/blob/master/assembler-api/src/main/java/org/dataconservancy/pass/deposit/assembler/PackageStream.java)
. The `PackageStream` represents the content to be sent to a downstream repository. Conceptually, the `PackageStream`
behaves like a Java `InputStream`: the bytes for the stream can come from anywhere (memory, a file on disk, or retrieved
from another network resource), and can generally only be read once.

Practically, the `PackageStream` represents an archive file: either a ZIP, TAR, or some variant like TAR.GZ. This is
encapsulated by
the [`ArchivingPackageStream`](https://github.com/OA-PASS/deposit-services/blob/master/shared-assembler/src/main/java/org/dataconservancy/pass/deposit/assembler/shared/ArchivingPackageStream.java)
class. Re-using the `ArchivingPackageStream` class has the advantage that your package resources will be bundled up in a
single archive file according to the options supplied to the `Assembler` (e.g. compression and archive type to use).

To instantiate an `ArchivingPackageStream` class requires an instance of `PackageProvider`.

## PackageProvider API

The [`PackageProvider`](https://github.com/OA-PASS/deposit-services/blob/master/shared-assembler/src/main/java/org/dataconservancy/pass/deposit/assembler/shared/PackageProvider.java)
interface was developed as an ad hoc lifecycle for streaming a package: there's a `start(...)` and `finish(...)` method,
along with a `packagePath(...)` method.   `PackageProvider` also defines a new
interface: [`SupplementalResource`](https://github.com/OA-PASS/deposit-services/blob/master/shared-assembler/src/main/java/org/dataconservancy/pass/deposit/assembler/shared/PackageProvider.java#L84)
. This interface is returned by the `finish(...)` method, allowing the `PackageProvider` implementation to generate
supplemental (i.e. BagIt tag files or METS.xml files) content after the rest of the package has been streamed.

Implementing this interface therefore allows for customizing where resources will appear in the package, and to
customize the metadata that appears in the package.

Because packaging specifications generally have something to say about what resources are included where in the package,
a Package Provider is loosely coupled to a package specification. For example, a Package Provider that placed custodial
resources in the `<package root>/foo` directory would be incompatible with a BagIt packaging specification, which
requires custodial resources to appear under `<package root>/data`. Similarly, if your Package Provider is to comport
with a DSpace METS packaging scheme, it will need to produce a `<package root>/METS.xml` file with the required content.
Therefore, any `PackageProvider` implementation can be used with any `Assembler` implementation as long as the package
specification shared between the two is not violated.

Examples: [`DspaceMetsPackageProvider`](https://github.com/OA-PASS/jhu-package-providers/blob/master/shared-dspace-provider/src/main/java/edu/jhu/library/pass/deposit/provider/shared/dspace/DspaceMetsPackageProvider.java)
, [`NihmsPackageProvider`](https://github.com/OA-PASS/jhu-package-providers/blob/master/nihms-package-provider/src/main/java/org/dataconservancy/pass/deposit/provider/nihms/NihmsPackageProvider.java)
, [`BagItPackageProvider`](https://github.com/OA-PASS/jhu-package-providers/blob/master/bagit-package-provider/src/main/java/edu/jhu/library/pass/deposit/provider/bagit/BagItPackageProvider.java)

## Recap

Implementations of `AbstractAssembler` return an `ArchivingPackageStream` which uses a `PackageProvider` to path
resources and generate supplemental metadata contained in the package.

When developing your own `Assembler`, you will need to:

* Extend `AbstractAssembler`
* Implement `PackageProvider`, including the logic to produce supplemental package content like BagIt tag files or
  DSpace METS.xml files
* Construct `ArchivingPackageStream` with your `PackageProvider` and return that from your `AbstractAssembler`
  implementation

Here are three examples:

* [`DspaceMetsPackageProvider`](https://github.com/OA-PASS/jhu-package-providers/blob/master/shared-dspace-provider/src/main/java/edu/jhu/library/pass/deposit/provider/shared/dspace/DspaceMetsPackageProvider.java)
* [`NihmsPackageProvider`](https://github.com/OA-PASS/jhu-package-providers/blob/master/nihms-package-provider/src/main/java/org/dataconservancy/pass/deposit/provider/nihms/NihmsPackageProvider.java)
* [`BagItPackageProvider`](https://github.com/OA-PASS/jhu-package-providers/blob/master/bagit-package-provider/src/main/java/edu/jhu/library/pass/deposit/provider/bagit/BagItPackageProvider.java)

# Concurrency

Assemblers exist in the Deposit Services runtime as singletons. A single `Assembler` instance may be invoked from
multiple threads, therefore all the code paths executed by an `Assembler` must be thread-safe.

`AbstractAssembler` and `ArchivingPackageStream` are already thread-safe; your concrete implementation
of `AbstractAssembler` and `PackageProvider` will need to maintain that thread safety. Streaming a package inherently
involves maintaining state, including the updating of metadata for resources as they are streamed. Package Providers
will often maintain state as they generate supplemental resources for a package;
the [`J10PMetadataDomWriter`](https://github.com/OA-PASS/jhu-package-providers/blob/master/jscholarship-package-provider/src/main/java/edu/jhu/library/pass/deposit/provider/j10p/J10PMetadataDomWriter.java)
, for example, builds a METS.xml file using a DOM.

One strategy for maintaining thread safety is to scope any state maintained over the course of streaming a package to
the executing thread.  `Assembler` implementations are free to use whatever mechanisms they wish to insure thread
safety, but Deposit Services accomplishes this in its codebase by simply instantiating a new instance of
state-maintaining classes each time the `Assembler.assemble(...)` is invoked, and insures that state is not shared (i.e.
kept on the Thread stack and not in the JVM heap). For example:

* `AbstractAssembler` instantiates a new `MetadataBuilder` each time using a factory pattern
* `AbstractAssembler` implementations instantiate a new `ArchivingPackageStream` each time
* `DefaultStreamWriterImpl` instantiates a new `ResourceBuilder` for each resource being streamed using a factory
  pattern
* The `DspaceMetsAssembler` uses a factory pattern to instantiate its state-maintaining objects.

The factory objects may be kept in shared memory (i.e. as instance member variables), but the objects produced by the
factories are maintained in the Thread stack (as method variables). After a `PackageStream` has been opened and
subsequently closed, these objects will be released and garbage collected by the JVM. To help insure thread safety,
there is an integration test
fixture, [`ThreadedAssemblyIT`](https://github.com/OA-PASS/deposit-services/blob/master/shared-assembler/src/test/java/org/dataconservancy/pass/deposit/assembler/shared/ThreadedAssemblyIT.java)
, which can be subclassed and used by `Assembler` integration tests to verify thread safety.

# Testing

Adequate test coverage of `Assemblers` includes proper unit testing. This document presumes that you've adequately unit
tested your implementation, and instead focuses on integration testing.

Integration testing of `Assemblers` is supported by some shared test fixtures in the core Deposit Services codebase.

## ThreadedAssemblyIT

The approach taken by the shared `ThreadedAssemblyIT` is to invoke the `Assembler` under test directly using
random `DepositSubmission`s.  
a singleton `Assembler` implementation under test is retrieved from the IT subclass that you provide a number of
different `DepositSubmission`s are used to concurrently invoke `Assembler.assemble(...)` on the singleton instance under
test the `PackageStreams` returned by the `Assembler` under test are streamed to and stored on the filesystem A package
verifier supplied by the IT subclass verifies the content of the packages.

The advantage of extending `ThreadedAssemblyIT` is that it insures that your `Assembler` can be invoked concurrently by
multiple threads while avoiding the complexity of setting up and configuring the Deposit Services runtime. The Spring
Framework is not used, the Deposit Services runtime is not required, and no Docker containers are needed: the IT is
simple Java and JUnit. The downside is that your full runtime is not being integration tested, only your `Assembler`.

To use `ThreadedAssemblyIT`, extend it, and implement the required methods:

* `assemblerUnderTest()`: provide an AbstractAssembler instance, fully initialized and ready to be invoked
* `packageOptions()`: provides a set of package options, used when creating the PackageStream and storing it on disk.
  The package options include:
    * The package specification to be used
    * The compression algorithm used when creating the package
    * The checksumming algorithm to be used when calculating package and package resource checksums
* `packageVerifier()`: answers a `PackageVerifier` which inspects a package stored on the filesystem and verifies its
  content. You must implement a `PackageVerifier` for each `Assembler` being tested.

The test logic will execute automatically in `ThreadedAssemblyIT.testMultiplePackageStreams()`. The `PackageVerifier` is
very important: it does most of the heavy lifting with respect to passing or failing the integration test, so it must be
well written and test all aspects of a generated package.

Example: [`BagItThreadedAssemblyIT`](https://github.com/OA-PASS/jhu-package-providers/blob/master/bagit-package-provider/src/test/java/edu/jhu/library/pass/deposit/provider/bagit/BagItThreadedAssemblyIT.java)
, [`J10PMetsThreadedAssemblyIT`](https://github.com/OA-PASS/jhu-package-providers/blob/master/jscholarship-package-provider/src/test/java/edu/jhu/library/pass/deposit/provider/j10p/J10PMetsThreadedAssemblyIT.java)
, [`NihmsThreadedAssemblyIT`](https://github.com/OA-PASS/jhu-package-providers/blob/master/nihms-package-provider/src/test/java/org/dataconservancy/pass/deposit/provider/nihms/NihmsThreadedAssemblyIT.java)

## SubmitAndValidatePackagesIT

The approach taken by this IT is to set up the entire Deposit Services runtime, including Fedora, Elastic Search, and
the Indexer using Docker, and treat it as a black box. It has a much higher barrier to entry than `ThreadedAssemblyIT`,
because your `Assembler`(s) must be encapsulated in a Deposit Services Docker image and deployed as a Docker container
prior to executing the `SubmitAndValidatePackagesIT`.

The advantage of this IT is that your full runtime environment is being tested at the expense of the complexity required
to set up and configure all of the required components. A series of Submission resources are created in the PASS
repository (Fedora), and triggered for submission (setting `Submission.submitted=true` for each resource).  
The Deposit Services runtime will accept these Submissions, and attempt to create a package for
each `{Submission, Repository}` tuple and store the resulting package.  
The IT uses the `RepositoryCopy` generated for each submission to store the location of each generated package. Each
package is resolved, exploded on the filesystem, and verified using a `PackageVerifier`. The test logic will execute
automatically in `SubmitAndValidatePackagesIT.verifyPackages()` using the `PackageVerifier` supplied by the IT subclass.
Again, it is important to emphasize that the `PackageVerifier` is the primary class used to insure the generated
packages are correct: your IT is only as good as your `PackageVerifier`.

### Runtime setup and Docker boilerplate

Using the `SubmitAndValidatePackagesIT` requires extensive boilerplate to configure the Docker environment (see the
deployment section for details). The first step is to insure that your Maven POM is properly configured to start the
necessary Docker containers, outlined below:

* Create a `Dockerfile` that will produce a Deposit Services runtime image that includes your `Assembler` and
  a `repositories.json` configured to invoke that Assembler
* Create a docker-maven-plugin configuration in your pom.xml that includes:
    * Your Deposit Services runtime image from step 1.
    * Each dependency, including:
        * Fedora
        * Elastic Search
        * Indexer At this point, you should be able to invoke `mvn docker:start` and interact with each service using
          your browser or `curl`, as appropriate. This confirms that your `maven-docker-plugin` configuration is
          correct: your Deposit Services runtime under test is being started, and all of its dependant services are up
          and available

### Configuration gotchas

There are a couple nuances to the `SubmitAndValidatePackagesIT` to be aware of. Firstly, you must provide a runtime
configuration for Deposit Services that uses the `FilesystemTransport`:

    "transport-config": {
     "protocol-binding": {
       "protocol": "filesystem",
       "baseDir": "/packages/jscholarship",
       "createIfMissing": "true",
       "overwrite": "false"
     }
    }

And the `docker-maven-plugin` configuration for the Deposit Services runtime must contain a volume that exposes
the `/packages` directory in the container to the Maven `target/packages` directory on the host:

    <image>
       <name>...</name>
       <build>
           ...
       </build>
       <run>
           ...
           <volumes>
               <bind>
                   ...
                   <!-- packages written by deposit services will be visible 
                        under 'target/packages' -->
    <volume>${project.build.directory}/packages:/packages</volume>
    
                   ...
               </bind>
           </volumes>
           <env>
               ...
           </env>
       </run>
    </image>

When the Deposit Services `FilesystemTransport` writes a package to the `/packages` directory in the _container_ it will
also be visible to the IT in the _Maven `target/packages` directory_, allowing the `PackageVerifier` access to the
package generated by the `Assembler` within Deposit Services.

### Test Code

Remember, `SubmitAndValidatePackagesIT` treats Deposit Services as a black box. It can only interact with the PASS
repository (Fedora), and observe the results of the Deposit Services runtime (the generation of `RepositoryCopy`
resources and package files). It cannot observe any state internal to the runtime.

Extend `SubmitAndValidatePackagesIT` and implement the required methods:

* returning a PackageVerifier
* determining the compression and archive algorithm used based on the name of the package file on disk

Example: [`ValidateDspaceAndNihmsProvidersIT`](https://github.com/OA-PASS/jhu-package-providers/blob/master/provider-integration/src/test/java/edu/jhu/library/pass/deposit/provider/integration/ValidateDspaceAndNihmsProvidersIT.java)
, [`pom.xml`](https://github.com/OA-PASS/jhu-package-providers/blob/master/provider-integration/pom.xml)
, [docker-related files](https://github.com/OA-PASS/jhu-package-providers/tree/master/provider-integration/src/main/docker)
, Deposit
Services [runtime config for ITs](https://github.com/OA-PASS/jhu-package-providers/blob/master/provider-integration/src/main/docker/repositories.json)

## PackageVerifier

Each `Assembler` that is developed should have a corresponding `PackageVerifier`. The `PackageVerifier` is the primary
interface for verifying that a package written to disk contains the expected content. The primary method to implement
is:
`void verify(DepositSubmission, ExplodedPackage, Map<String, Object>)` where the `DepositSubmission` is the original
submission, `ExplodedPackage` is the generated package on disk, and the `Map` includes the options supplied to
the `Assembler` that created the package.

The verifier is responsible for:

* Insuring that every custodial file from the submission is present and accounted for in the package
* Insuring there are no extraneous custodial files in the package that are not in the submission
* Insuring that the custodial files checksums are correct
* Insuring that the proper supplemental files are present in the package and have the correct content

Essentially all aspects of a generated package must be verified through a `PackageVerifier`.

The `PackageVerifier` interface does come with
a [helper method](https://github.com/OA-PASS/deposit-services/blob/master/shared-assembler/src/test/java/org/dataconservancy/pass/deposit/assembler/shared/PackageVerifier.java#L95)
for ensuring that there is a custodial file in the package for each submitted file, and that there are no unexplained
custodial files present in the package.
`void verifyCustodialFiles(DepositSubmission, File, FileFilter, BiFunction<File, File, DepositFile>)`
where `DepositSubmission` is the original submission, the `File` is the directory on the filesystem that contains the
exploded package, the `FileFilter` selects custodial files from the package directory, and the `BiFunction` accepts
a `DepositFile` from the submission and maps it to its expected location in the package directory.

Examples: [`DspaceMetsPackageVerifier`](https://github.com/OA-PASS/jhu-package-providers/blob/master/shared-dspace-provider/src/test/java/edu/jhu/library/pass/deposit/provider/shared/dspace/DspaceMetsPackageVerifier.java)
, [`NihmsPackageVerifier`](https://github.com/OA-PASS/jhu-package-providers/blob/master/nihms-package-provider/src/test/java/org/dataconservancy/pass/deposit/provider/nihms/NihmsPackageVerifier.java)

# Runtime

Deposit Services is a Spring Boot application, and `Assembler`s are simply a component executed within the application.
If you are familiar with Spring and/or Spring Boot, you are welcome to leverage its features as you wish. Regardless of
your views of Spring, you need to be aware of Spring in these cases:

* When extending `SubmitAndValidatePackagesIT` (your IT will need to use the `SpringRunner`)
* Your `Assembler` implementation must be annotated with `@Component`
* Your `Assembler` jar needs to support Spring Auto Configuration so that it can be discovered by the core Deposit
  Services runtime upon startup Use of Spring Bean names in Deposit Services runtime configuration (`repositories.json`)

## Wiring

So, how is your `Assembler`, `PackageStream`, and `PackageProvider` wired together? As outlined above, the wiring of
these components is straightforward. You can either "hardwire" your implementations at compile-time, or you can leverage
Spring dependency injection as you wish.

Deposit Services uses Spring Auto Configuration to discover your `Assembler` on the classpath on boot. Supporting Spring
Auto Configuration is very simple:

* Create an empty class at the _base_ of your package provider package hierarchy
    * This is so that the Spring component scanning will work properly
    * If your `Assembler` is under `org.foo.deposit.assembler.impl`, do _not_ place your `AutoConfiguration` class
      under `org.foo.deposit.spring.auto`, place it under `org.foo.assembler` or `org.foo.assembler.impl`.
* Annotate the class with two annotations:
    * `@ComponentScan`
    * `@Configuration`
* Create `src/main/resources/META-INF/spring.factories`, which takes the form of a Java properties file
* Add a single key `org.springframework.boot.autoconfigure.EnableAutoConfiguration` with a value of the fully qualified
  class name of the class created in step 1.
* Insure your `Assembler` implementation is annotated with `@Component`.

An example Package Provider Maven directory structure is below, which highlights the Spring Auto Configuration-related
files:

        nihms-package-provider/
        ├── Dockerfile
        ├── ...
        ├── pom.xml
        └── src
            ├── main
            │   ├── java
            │   │   └── org
            │   │       └── dataconservancy
            │   │           └── pass
            │   │               └── deposit
            │   │                   └── provider
            │   │                       └── nihms
            │   │                           ├── NihmsAssembler.java
            │   │                           ├── ...
            │   │                           ├── NihmsPackageProvider.java
            │   │                           ├── NihmsPackageProviderAutoConfiguration.java
            │   │                           ├── NihmsPackageProviderFactory.java
            │   │                           └── StreamingSerializer.java
            │   └── resources
            │       └── META-INF
            │           └── spring.factories
            └── test
                ├── java
                │   └── ...
                └── resources
                    └── ...

An example `spring.factories` file and Auto Configuration class:

    org.springframework.boot.autoconfigure.EnableAutoConfiguration=org.dataconservancy.pass.deposit.provider.nihms.NihmsPackageProviderAutoConfiguration
    
    package org.dataconservancy.pass.deposit.provider.nihms;
    
    import org.springframework.context.annotation.ComponentScan;
    import org.springframework.context.annotation.Configuration;
    
    @Configuration
    @ComponentScan
    public class NihmsPackageProviderAutoConfiguration {
    
    }

When Deposit Services boots up, it will search the classpath for `spring.factories` resources and execute the
annotations on the Auto Configuration class. In this case, the `@Configuration` and `@ComponentScan` annotations will
tell Spring to discover any Spring-annotated classes. Your `Assembler` implementation ought to be annotated
with `@Component`, and it will be discovered and wired by the core Deposit Services runtime:

    @Component
    public class NihmsAssembler extends AbstractAssembler {
     ...
    }

On boot, you should see information from the console indicating that your Assemblers have been discovered:

    INFO - Starting DepositApp on provider-integration-its-1.its with PID 1 (/app/BOOT-INF/classes started by root in /app)
    INFO - Running with Spring Boot v2.1.2.RELEASE, Spring v5.1.4.RELEASE
    INFO - No active profile set, falling back to default profiles: default
    INFO - >>>> Discovered Assembler implementation nihmsAssembler: org.dataconservancy.pass.deposit.provider.nihms.NihmsAssembler
    INFO - >>>> Discovered Assembler implementation dspaceMetsAssembler: edu.jhu.library.pass.deposit.provider.j10p.DspaceMetsAssembler
    INFO - >>>> Discovered Transport implementation filesystemTransport: org.dataconservancy.pass.deposit.transport.fs.FilesystemTransport
    INFO - >>>> Discovered Transport implementation ftpTransport: org.dataconservancy.pass.deposit.transport.ftp.FtpTransport
    INFO - >>>> Discovered Transport implementation sword2Transport: org.dataconservancy.pass.deposit.transport.sword2.Sword2Transport

## Deployment

Deploying your new Assembler means creating a Docker image that extends the core Deposit Services image by adding your
Assembler and its runtime dependencies to the image. The latest core Deposit Services image can be found on Docker Hub.
After you have added your `Assembler` and created your image, remember to update
the `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` environment variable to refer to the configuration containing your
new `Assembler`.

The core Deposit Services image is
a [Spring Boot](https://docs.spring.io/spring-boot/docs/2.1.4.RELEASE/reference/htmlsingle/) application
in [exploded form](https://docs.spring.io/spring-boot/docs/2.1.4.RELEASE/reference/htmlsingle/#executable-jar-jar-file-structure)
. Assuming your `Assembler` is packaged as a JAR, your `Dockerfile` would extend the core Deposit Services image and
copy your `Assembler` JAR and its runtime dependencies into the image `BOOT-INF/lib` directory. See the
example `Dockerfile` for the `DspaceMetsAssembler` here.

You can use whatever mechanisms you wish in order to create your `Assembler` image. The examples provided use
the `maven-dependency-plugin` and the `docker-maven-plugin` to build and deploy images.

* Production JHU Package Providers Dockerfile
    * Extends the core Deposit Services image
    * Published to Docker Hub automatically as a part of the build
    * Deployed to Production environment
* JHU Package Providers IT Dockerfile
    * Extends the Production JHU Package Providers image
    * Sets PASS_DEPOSIT_REPOSITORY_CONFIGURATION to point to the configuration used by the ITs (including the use of
      FilesystemTransport)
    * Deployed in the IT environment

### Dependency Gotchas

A good rule of thumb is to import the core Deposit Services parent POM into your Package Provider POM, and use the same
version of any dependencies that are shared between the core image and your Package Provider image.

When extending the core Deposit Services image, you must be mindful of the dependencies and classes that are already
present in the image. The `BOOT-INF/classes` and `BOOT-INF/lib` directories are shared between the core Deposit Services
image and your Assembler. Be sure that copying your Assembler and dependencies into `BOOT-INF/lib` will not pollute the
classpath with multiple versions of the same library. The easiest thing to do is use
the ["bill of materials"](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies) [pattern](https://www.baeldung.com/spring-maven-bom)
, where your Assembler's POM has dependency on the core Deposit Services parent pom with a scope of import:

    <dependency>
       <groupId>org.eclipse.pass.deposit</groupId>
       <artifactId>deposit-parent</artifactId>
       <version>0.2.0-3.2-SNAPSHOT</version>
       <scope>import</scope>
       <type>pom</type>
    </dependency>

If your Assembler shares a dependency with the core Deposit Services image, be sure to use the version of the dependency
that is provided by the core Deposit Services.