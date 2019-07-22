# Deposit Services Overview

Provides an overview of Deposit Services with respect to developing Deposit Services package providers.

Developing a package provider primarily deals with extending or re-using `Assembler`-related abstract classes and implementations, but it helps to understand the context in which `Assembler`s operate.  This document serves to provide that context.

## Model

Overview of model entities relevant to Deposit Services.

### PASS Model

Deposit Services uses objects in the [PASS data model](https://github.com/OA-PASS/pass-data-model/tree/master/documentation) (distinct from the Deposit Services' internal model).  Objects in the PASS data model are persisted in the Fedora repository.  Thus, any interaction with PASS resources will require CRUD operations on resources held in Fedora (using the PASS Java client) by Deposit Services.

PASS objects used by Deposit Services are:

  * **`Submission`**: only ever read by Deposit Services, never modified.
  * **`Repository`**: only ever read by Deposit Services, never modified.
  * **`Deposit`**: are created and modified by Deposit Services.
  * **`RepositoryCopy`**: are created and modified by Deposit Services (note that the NIHMS loader also creates RepositoryCopy resources)

![Deposit Services PASS model](pass-model.png)

Each `Submission` resource links to one or more `Repository` resources.  Deposit Services will create a `Deposit` and `RepositoryCopy` resource for each `Repository` linked to the `Submission`.   A deposit attempt will be made by Deposit Services to the downstream system represented by the `Repository`.  The status of a deposit to a downstream repository will be recorded on the `Deposit` resource.  That is to say, the `Deposit` records the transaction and its success or failure with a `Repository`, and the `RepositoryCopy` records where the Repository stored the content of the `Deposit`.

### Deposit Services Internal Model

Deposit Services has an internal object model, distinct from the PASS data model.  Instances of the internal object model are not persisted in the PASS repository, or anywhere else.  Upon receipt of a `Submission` (i.e. the external PASS model), Deposit Services immediately converts it to an instance of the internal model using a Model Builder.

#### Deposit Model

  * **`DepositSubmission`**: internal representation of a PASS Submission
  * **`DepositMetadata`**: metadata describing the submission, parsed from the "metadata blob" (**`Submission.metadata`**) and other `Submission` properties.

#### Configuration Model

  * **`Packager`**: encapsulates configuration of the `Assembler`, `Transport`, and `DepositStatusProcessor` for every downstream repository in `repositories.json`.  Each repository configured in `repositories.json` ought to reference a `Repository` resource in the PASS repository.
  * **`RepositoryConfig`**: Java representation of a single repository configuration in `repositories.json`.  The configuration for a repository includes directives for the transport protocol used for deposit (including authentication credentials), packaging specification used for deposit, and packaging options.

#### Packaging Model

  * **`Assembler`**: responsible for creating and streaming the content (i.e. the files uploaded by the end-user and any metadata required by the packaging specification) of a Submission according to a packaging specification
  * **`PackageStream`**: content of a `Submission` to be deposited to a downstream repository as a stream, as opposed to bytes held in a buffer or stored on a file system
  * **`Transport`**: an abstraction representing the physical protocol used to transfer the package stream from the PASS repository to the downstream repository.

#### Messaging Model

  * **`DepositStatusProcessor`**: responsible for updating the `Deposit.depositStatus` property of a `Deposit` resource, typically by resolving the URL in the `Deposit.depositStatusRef` property and parsing its content.
  * **`CriticalRepositoryInteraction`**: CRI for short.  Performs an opportunistic (`If-Match` using an Etag) "critical" modification on a PASS resource, with a built-in retry mechanism when a modification fails.  Each CRI has a pre-condition, critical section, and post-condition.  The pre-condition must be met before the critical section is executed.  The post-condition determines whether the application of the critical section was successful.  The built-in retry mechanism re-uses the pre/post/critical functions in the case of a conflict.

## Model Builder

Upon receipt of a Submission, the `ModelBuilder` is invoked to produce an instance of `DepositSubmission`. The `ModelBuilder` interface accepts a string representation of a `Submission` for conversion to `DepositSubmission`.  The string may be a blob representing the entirety of the submission (i.e. a serialization of a `Submission` together with its linked resources), or it may be a URI reference to a `Submission` resource in the PASS repository.

### Fedora Builder

The `FedoraBuilder` implementation accepts a URI reference to a `Submission` resource in the PASS repository, and builds a `DepositSubmission` by retrieving the `Submission` and its linked resources.  This is the `ModelBuilder` implementation used in production.

## Assembler

Responsible for assembling the content of a submission into a streamable package (i.e. the `Assembler` returns a `PackageStream` instance).  This includes:
  * Resolving the custodial content being deposited from the PASS repository
  * Generating any metadata required by the packaging specification
  * Generating any metadata required by the downstream repository
  * Encapsulating all of the above into a stream of bytes that meets a packaging specification

The work to implement the Configurable Metadata Framework focuses on the support of pluggable Assemblers within Deposit Services: different `Assembler` implementations can include metadata required for their repository.

### Custodial and supplemental resources

The term _custodial resource_ is used throughout: a custodial resource is content that was uploaded by the end user for deposit to a downstream repository: their data sets, manuscripts, etc.  Non-custodial resources (i.e. _supplemental resources_) include metadata describing the content, for example, BagIt tag files or DSpace METS XML files.

#### Abstract Assembler and Archiving Package Stream

There are two abstract classes to help developers create `Assembler` implementations.  The `AbstractAssembler` and `ArchivingPackageStream`.

The `AbstractAssembler` contains shared logic for building a list of custodial resources to be deposited.  Concrete implementations accept the list of custodial resources (among other parameters, including the packaging specification) and produce the `PackageStream`.

The `ArchivingPackageStream` contains shared logic for assembling multiple files into a single zip, tar, or tar.gz file.

#### MetadataBuilder and PackageStream.Metadata

`PackageStream.Metadata` is an interface that provides package-level metadata.  The `MetadataBuilder` is a fluent API for creating physical package-level metadata such as:
  * Packaging specification - a URI identifying the package specification used
  * Package size (bytes) and its checksum
  * The package name
  * Mime type, compression used, and archive format

The `PackageStream.metadata()` method returns the `PackageStream.Metadata` for a `PackageStream` instance.  Because some metadata is unknown prior to streaming (e.g. the package size), the metadata returned by this method may be incomplete until after the stream has been read.

#### ResourceBuilder and PackageStream.Resource

`PackageStream.Resource` is an interface that provides metadata describing a resource within the package.  `ResourceBuilder` is a fluent API for creating physical metadata describing each resource (i.e. file) within the package:
  * File size (bytes)
  * Filename, including its path relative to the package root
  * Checksum and mime type

The `PackageStream.resources()` method answers an `Iterator` over each `Resource` in the `PackageStream`.

#### Package Provider

`PackageProvider` is an interface that is invoked by Deposit Services when a `PackageStream` is streamed to a repository via a `Transport`.  

`PackageProvider` represents a streaming lifecycle interface that has three methods: `start(...)`, `packagePath(...)`, and `finish(...)`.  The `start(...)` method is invoked after the custodial resources have been assembled, but before streaming has started.  The `packagePath(...)` method is invoked prior to streaming each custodial resource.  The `finish(...)` method is invoked after all the custodial resources have been streamed, and provides an opportunity for the `PackageProvider` to add supplemental resources to the package being streamed.

For example, a BagIt `PackageProvider` would insure that each custodial resource is pathed under `<package root>/data` when implementing `packagePath(...)`. After the the custodial resources are streamed, the BagIt `PackageProvider` would assemble and stream all of the BagIt metadata: bagit.txt and any other tag files.

## Transport

Responsible for transferring the bytes of a package (i.e. a `PackageStream`) to an endpoint.  The Transport API is designed to support any transport protocol.  Each downstream repository in `repositories.json` must be configured with a `Transport` implementation.  

The `Assembler` and the `PackageProvider` create the package, and the `Transport` is the "how" of how a package is transferred to a downstream repository.  Choosing the `Transport` to be used depends on the support of the downstream repository for things like FTP, SWORD, or other protocols.

For example, a BagIt `Assembler` and `PackageProvider` would produce BagIt packages.  Those packages may be transported to downstream repositories using FTP, SWORDv2, or a custom Transport implementation.  The `Transport` to be used is a matter of configuration in `repositories.json`.

### FTP
Supports the transport of the package stream using FTP.

#### SWORDv2

Supports the transport of the package stream using [SWORD protocol version 2](http://swordapp.org/sword-v2/sword-v2-specifications/).

## Runtime Configuration

Deposit Services is configured at runtime by a configuration file referenced by the system property `pass.deposit.repository.configuration` or the environment variable `PASS_DEPOSIT_REPOSITORY_CONFIGURATION`.  By default (i.e. if the system property or environment variable are not present) the classpath resource `/repositories.json` is used.