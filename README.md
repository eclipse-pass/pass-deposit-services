# PASS Deposit Services

Deposit Services are responsible for the transfer of custodial content and metadata from end users to repositories.  End users transfer custody of their content to PASS by performing a submission through the HTML user interface, and Deposit Services subsequently transfers the custody of content to downstream repositories.

Deposit Services is deployed as "back-end" infrastructure.  It has no user-facing elements.  In particular, Deposit Services is unaware of the internal/external duality of resource URIs.  This means that when looking at URIs in Deposit Services' logging output, some adjustment may be necessary for a developer or systems operator to retrieve the resource from their location in the network topology. 


 

## Configuration

The primary mechanism for configuring Deposit Services is through environment variables.  This aligns with the patterns used in development and production infrastructure which rely on Docker and its approach to runtime configuration.  Deposit Services relies on _existing_ variables common to the PASS infrastructure, and defines its own application-specific variables.

### Production Configuration Variables


|Environment Variable                       |Default Value                                                                  |Description|
|-------------------------------------------|-------------------------------------------------------------------------------|-----------|
|`FCREPO_HOST`                              |localhost                                                                      |the IP address or host name of the Fedora repository
|`FCREPO_PORT`                              |8080                                                                           |the TCP port running the Fedora HTTP REST API.
|`FCREPO_JMS_PORT`                          |61616                                                                          |the TCP port for the STOMP protocol.
|`ES_HOST`                                  |localhost                                                                      |the IP address or host name of the Elastic Search index.
|`ES_PORT`                                  |9200                                                                           |the TCP port running the Elastic Search HTTP API.
|`FTP_HOST`                                 |localhost                                                                      |the IP address or  host name of the NIH FTP server
|`FTP_PORT`                                 |21                                                                             |the TCP control port of the NIH FTP server
|`DSPACE_HOST`                              |localhost                                                                      |the IP address or host name of the server running the SWORD protocol version 2 endpoint
|`DSPACE_PORT`                              |8181                                                                           |the TCP port exposing the SWORD protocol version 2 endpoint
|`PASS_ELASTICSEARCH_LIMIT`                 |100                                                                            |the maximum number of results returned in a single search response
|`PASS_FEDORA_USER`                         |fedoraAdmin                                                                    |the username used for `Basic` HTTP authentication to the Fedora REST API
|`PASS_FEDORA_PASSWORD`                     |moo                                                                            |the password used for `Basic` HTTP authentication to the Fedora REST API
|`PASS_DEPOSIT_TRANSPORT_CONFIGURATION`     |classpath:/packagers.properties                                                |points to a properties file containing the configuration for the transport of custodial content to remote repositories.  Values must be [Spring Resource URIs][1].
|`PASS_DEPOSIT_WORKERS_CONCURRENCY`         |4                                                                              |the number of Deposit Worker threads that can simultaneously run.
|`PASS_DEPOSIT_STATUS_MAPPING`              |classpath:/statusmapping.json                                                  |points to a JSON file that identifies protocol-specific statuses as _terminal_ or _intermediate_.  Values must be [Spring Resource URIs][1].
|`PASS_DEPOSIT_HTTP_AGENT`                  |pass-deposit/x.y.z                                                             |the value of the `User-Agent` header supplied on Deposit Services' HTTP requests.
|`SPRING_JMS_LISTENER_CONCURRENCY`          |4                                                                              |the number of JMS messages that can be processed simultaneously by _each_ JMS queue
|`PASS_DEPOSIT_QUEUE_SUBMISSION_NAME`       |submission                                                                     |the name of the JMS queue that has messages pertaining to `Submission` resources (used by the `JmsSubmissionProcessor`)
|`PASS_DEPOSIT_QUEUE_DEPOSIT_NAME`          |deposit                                                                        |the name of the JMS queue that has messages pertaining to `Deposit` resources (used by the `JmsDepositProcessor`)
|`ACTIVEMQ_BROKER_URI`                      |`null`                                                                         |the publicly-supported (i.e. official PASS) variable for configuring the JMS broker URL.  used to compose the `SPRING_ACTIVEMQ_BROKER_URL`
|`SPRING_ACTIVEMQ_BROKER_URL`               |${activemq.broker.uri:tcp://${fcrepo.host:localhost}:${fcrepo.jms.port:61616}} |the internal variable for configuring the URI of the JMS broker (under normal circumstances, this environment variable should never be set)

> If the Fedora repository is deployed under a webapp context other than `/fcrepo`, the environment variable `PASS_FEDORA_BASEURL` must be set to the base of the Fedora REST API (e.g. `PASS_FEDORA_BASEURL=http://fcrepo:8080/fcrepo/rest`)

> If the Elastic Search index is deployed under a url other than `/pass`, the environment variable `PASS_ELASTICSEARCH_URL` must be set to the base of the Elastic Search HTTP API (e.g. `PASS_ELASTICSEARCH_URL=http://localhost:9200/pass`)

### Packager (Transport) Configuration

The `Packager` configuration contains the parameters used by the `Packager` for connecting to remote repositories (i.e. "transports").  Deposit Services comes with a default configuration, but a production environment may override the default, and tell Deposit Services the location of the new confguration.

#### Packager configuration format

The format of the configuration file are Java properties, and the keys are prefixed by well-known values.  Each remote repository will have a prefix for its keys.  The _default_ configuration of Deposit Services is listed below.  There are two repositories supported, the NIH FTP server and the JScholarship DSpace instance.  Each repository has a unique prefix: `transport.nihms.deposit.transport` for the NIH, and `transport.js.deposit.transport` for DSpace:

        transport.nihms.deposit.transport.authmode=userpass
        transport.nihms.deposit.transport.username=nihmsftpuser
        transport.nihms.deposit.transport.password=nihmsftppass
        transport.nihms.deposit.transport.server-fqdn=${ftp.host}
        transport.nihms.deposit.transport.server-port=${ftp.port}
        transport.nihms.deposit.transport.protocol=ftp
        transport.nihms.deposit.transport.protocol.ftp.basedir=/logs/upload/%s
        transport.nihms.deposit.transport.protocol.ftp.transfer-mode=stream
        transport.nihms.deposit.transport.protocol.ftp.use-pasv=true
        transport.nihms.deposit.transport.protocol.ftp.data-type=binary
        
        transport.js.deposit.transport.authmode=userpass
        transport.js.deposit.transport.username=dspace-admin@oapass.org
        transport.js.deposit.transport.password=foobar
        transport.js.deposit.transport.server-fqdn=${dspace.host}
        transport.js.deposit.transport.server-port=${dspace.port}
        transport.js.deposit.transport.protocol=swordv2
        transport.js.deposit.transport.protocol.swordv2.service-doc=http://${dspace.host}:${dspace.port}/swordv2/servicedocument
        transport.js.deposit.transport.protocol.swordv2.target-collection=http://${dspace.host}:${dspace.port}/swordv2/collection/123456789/2
        transport.js.deposit.transport.protocol.swordv2.on-behalf-of=
        transport.js.deposit.transport.protocol.swordv2.deposit-receipt=true
        transport.js.deposit.transport.protocol.swordv2.user-agent-string=pass-deposit/x.y.z 

A few observations of this example configuration:
* Deposit Services not provide _any_ default values to augment or complement the packager configuration, so when the default configuration is overridden, _all_ values must be represented in the new configuration, even if they remain unchanged from the default.
* Environment variables / properties are allowed!  This means that a configuration can be parameterized, and environment variables used to provide key values (keys themselves cannot be parameterized with variables).
* As the keys are well-known to Deposit Services, it is unlikely that the configuration would be overridden without a corresponding code update.

#### Important configuration keys

A production deployment of Deposit Services is likely to provide updated values for the following keys:
> Remember: if the default configuration is overridden, _all_ keys with their values must be in the new configuration, even if their value remains unchanged

|Property name                                                       |Default value                                                         |Description|
|--------------------------------------------------------------------|----------------------------------------------------------------------|-----------|
|`transport.nihms.deposit.transport.username`                        |nihmsftpuser                                                          |the username used when authenticating to the FTP server specified by `transport.nihms.deposit.transport.server-fqdn`
|`transport.nihms.deposit.transport.password`                        |nihmsftppass                                                          |the password used when authenticating to the FTP server specified by `transport.nihms.deposit.transport.server-fqdn`
|`transport.nihms.deposit.transport.server-fqdn`                     |localhost                                                             |the IP address or hostname of the NIH FTP server 
|`transport.nihms.deposit.transport.server-port`                     |21                                                                    |the FTP control port of the NIH FTP server
|`transport.js.deposit.transport.username`                           |dspace-admin@oapass.org                                               |the username used when accessing the SWORD protocol version 2 service document, and creating SWORD protocol version 2 deposits within DSpace
|`transport.js.deposit.transport.password`                           |foobar                                                                |the password used when accessing the SWORD protocol version 2 service document, and creating SWORD protocol version 2 deposits within DSpace
|`transport.js.deposit.transport.server-fqdn`                        |localhost                                                             |the IP address or hostname of the DSpace SWORD endpoint
|`transport.js.deposit.transport.server-port`                        |8181                                                                  |the TCP port of the DSpace SWORD endpoint
|`transport.js.deposit.transport.protocol.swordv2.service-doc`       |http://${dspace.host}:${dspace.port}/swordv2/servicedocument          |the location of the SWORD service document
|`transport.js.deposit.transport.protocol.swordv2.target-collection` |http://${dspace.host}:${dspace.port}/swordv2/collection/123456789/2   |the location of the SWORD collection accepting deposits (note that this collection _must_ be enumerated in the SWORD service document)

#### Creating and using an updated configuration

To create your own configuration, copy and paste the default configuration into an empty file and modify the key values as described above.  The configuration _must_ be referenced by the `pass.deposit.transport.configuration` property, or is environment equivalent `PASS_DEPOSIT_TRANSPORT_CONFIGURATION`.  Allowed values are any Spring Resource path (e.g. `classpath:/`, `classpath*:`, `file:`, `http://`, `https://`).  For example, if your configuration is stored as a file in `/etc/deposit-services.cfg`, then you would set the environment variable `PASS_DEPOSIT_TRANSPORT_CONFIGURATION=file:/etc/deposit-services.cfg` prior to starting Deposit Services.  Likewise, if you kept the configuration accessible at a URL, you could use `PASS_DEPOSIT_TRANSPORT_CONFIGURATION=http://example.org/deposit-services.cfg`.

## Build and Deployment

Deposit Services' primary artifact is a single self-executing jar.  The behavior, or "mode" of the deposit services application is directed by command line arguments and influenced by environment variables.  In the PASS infrastructure, the Deposit Services self-executing jar is deployed inside of a simple Docker container.

Deposit Services can be built by running:
* `mvn clean install`

The main Deposit Services deployment artifact is found in `deposit-messaging/target/deposit-messaging-<version>.jar`.  It is this jarfile that is included in the Docker image for Deposit Services, and posted on the GitHub Release  page.

### Supported modes

The mode is a required command-line argument which directs the deposit services application to take a specific action.

#### Listen

Listen mode is the "primary" mode, if you will, of Deposit Services.  In `listen` mode Deposit Services responds to JMS messages from the Fedora repository by creating and transferring packages of custodial content to remote repositories. 

Listen mode is invoked by starting Deposit services with `listen` as the single command-line argument:

> $ java -jar deposit-services.jar listen

Deposit Services will connect to a JMS broker specified by the environment variables `FCREPO_HOST` and `FCREPO_JMS_PORT`, and wait for the Fedora repository to be available as specified by `FCREPO_PORT`.  Notably, `listen` mode does not use the index.

> If the Fedora repository is deployed under a webapp context other than `/fcrepo`, the environment variable `PASS_FEDORA_BASEURL` must be set to the base of the Fedora REST API (e.g. `PASS_FEDORA_BASEURL=http://fcrepo:8080/fcrepo/rest`)

After successfully connecting to the JMS broker and the Fedora repository, deposit services will listen and respond to JMS messages relating to the submission and deposit of material to the Fedora repository.  Incoming `Submission` resources created by end-users of the UI will be processed:
* custodial content packaged
* packages sent to destination repositories
* confirmation of custody transfer
* recording the identities of content in destination repositories

Incoming `Deposit` resources will be used to update the overall success or failure of a `Submission`.

### Future modes

Modes to be supported by future releases of Deposit Services.

#### Report

TODO

#### Process

TODO

# Developers

Deposit Services is implemented using Spring Boot, which heavily relies on Spring-based annotations and conventions to create and populate a Spring `ApplicationContext`, arguably the most important object managed by the Spring runtime.  Unfortunately, if you aren't familiar with Spring or its conventions, it can make the code harder to understand.

The entrypoint into the deposit services is the `DepositApp`, which accepts command line parameters that set the "mode" of the deposit services runtime.  Spring beans are created entirely in Java code by the `DepositConfig`  and `JmsConfig` classes.

## Listen mode

The `listen` argument will invoke the `ListenerRunner`, which waits for the Fedora repository to be available, otherwise it shuts down the application.  Two JMS listeners are started that listen to the `submission` and `deposit` queues.  The `submission` queue provides messages relating to `Submission` resources, and the `deposit` queue provides messages relating to `Deposit` resources.  Deposit Services does not listen or act on messages for other types of repository resources.

The `PASS_FEDORA_USERNAME` and `PASS_FEDORA_PASSWORD` define the username and password used to perform HTTP `Basic` authentication to the Fedora HTTP REST API (i.e. `PASS_FEDORA_BASEURL`).

### Message flow and concurrency

Each JMS listener (one each for the `deposit` and `submission` queues) can process messages concurrently.  The number of messages each listener can process concurrently is set by the property `spring.jms.listener.concurrency` (or its environment equivalent: `SPRING_JMS_LISTENER_CONCURRENCY`).

The `submission` queue is processed by the `JmsSubmissionProcessor`,which resolves the `Submission` resource represented in the message, and hands off processing to the `SubmissionProcessor`.  The `SubmissionProcessor` builds a `DepositSubmission`, which is the Deposit Services' analog of a `Submission` containing all of the metadata and custodial content associated with a  `Submission`.  After building the `DepositSubmission`, the processor creates a `DepositTask` and hands off the actual packaging and transfer of submission content to the deposit worker thread pool.  Importantly, the `SubmissionProcessor` updates the `Submission` resource in the repository as being _in progress_.

There is a thread pool of so-called "deposit workers" that perform the actual packaging and transport of custodial content to downstream repositories.  The size of the worker pool is determined by the property `pass.deposit.workers.concurrency` (or its environment equivalent: `PASS_DEPOSIT_WORKERS_CONCURRENCY`).  The deposit worker pool accepts instances of `DepositTask`, which contains the primary logic for packaging, streaming, and verifying the transfer of content from the PASS repository to downstream repositories.  The `DepositTask` will determine whether or not the transfer of custodial content has succeed, failed, or is indeterminable (i.e. an asyc deposit process that has not yet concluded).  The status of the `Deposit` resource associated with the `Submission` will be updated accordingly.  

#### CriticalRepositoryInteraction

A central, yet awkwardly-named, abstraction is `CriticalRepositoryInteraction`.  This interface is used to prevent interleaved updates of individual repository resources by different threads.  A `CriticalRepositoryInteraction` (`CRI` for short) isolates the execution of a `Function` on a specific repository resource, and provides the boilerplate (i.e. template) for retrieving and updating the state of the resource.  There are four main components to `CriticalRepositoryInteraction`: the repository resource itself, a _pre-condition_, _post-condition_, and the _critical_ update (i.e. the `Function` to be executed).  The only implementation of `CRI` is the class `CriticalPath`, and the particulars of that implementation are discussed below.

1. First, `CriticalPath` obtains a lock over the string form of the URI of the resource being updated.  This insures that any other threads executing a `CRI` for the _same resource_ _in the same JVM_ must wait their turn before executing their critical update of the resource.

> This occurs more often than one might think, as Deposit Services receives many messages for the same resource almost "all at once" when a submission occurs.  The thread model for Spring and the Deposit Workers would be rife with conflicts unless something like the `CRI` was uniformly adopted in Deposit Services.

2. Second, the resource is read from the repository.

3. Third, the _pre-condition_ `Predicate` is executed over the resource.  If the _pre-condition_ fails, the entire `CriticalPath` is failed, and returns.

4. Fourth, the critical `Function` is executed, assured that the resource _at the time it was retrieved_ in step 2 meets the _pre-condition_ applied in step 3.  It is assumed that the `Function` modifies the state of the resource.  The `Function` may return the updated state of the resource, or it may return an entirely different object (remember the `Function` is parameterized by two types; while it _must_ accept a `PassEntity`, it does not have to return a `PassEntity`). 

5. After updating the state of the resource in step 4, an attempt is made to store and re-read the updated resource in the repository.  In this step, an `UpdateConflictException` may occur, because some other process outside of the JVM may have modified the resource after step 2 but before step 5.  If `UpdateConflictException` is caught, it is the responsibility of the `ConflictHandler` to resolve the conflict.  Otherwise, the update is successful, and processing of the resource by the `CriticalPath` continues.

6.  Finally, the _post-condition_ `BiPredicate` is executed.  It accepts the resource as updated and read by step 5, and the object returned by the critical update in step 4.  This determines the logical success or failure of the `CriticalPath`.  Steps 1 through 5 may have executed without error, but the _post-condition_ has final say of the overall success of the `CriticalPath`.  
  
#### CriticalRepositoryInteraction Example

Here is a real example of a `CRI` in action, used when packaging and depositing custodial content to a downstream repository.

The _pre-condition_ insures that we are operating on `Deposit` resources acceptable for processing.  The _critical update_ creates a package and streams it to the remote repository, and obtains a `TransportResult`.  The status of the `Deposit` resource is modified, and the `TransportResult` is returned by the _critical update_.  Finally, the _post-condition_ uses the state of the `Deposit` resource _and_ the `TransportResult` to evaluate the success of the _critical update_. 

Behind the scenes, the `CriticalPath` is insuring that the state of the `Deposit` is properly stored in the repository, and that any conflicts are handled. 

After the `CriticalPath` executes, its `CriticalResult` can be examined for success or failure.

    CriticalResult<TransportResponse, Deposit> result = critical.performCritical(dc.deposit().getId(), Deposit.class,

                /*
                 * Pre-condition: only "dirty" deposits can be processed by {@code DepositTask}
                 */
                (deposit) -> {
                    boolean accept = dirtyDepositPolicy.accept(deposit.getDepositStatus());
                    if (!accept) {
                        LOG.debug(">>>> Update precondition failed for {}", deposit.getId());
                    }

                    return accept;
                },

                /*
                 * Post-conditon: determines *physical* success of the Deposit: were the bytes of the package successfully received?
                 * Note: uses the TransportResponse as well as the resource state to determine the success of this CRI
                 */
                (deposit, tr) -> {
                    boolean success = deposit.getDepositStatus() == SUBMITTED;
                    if (!success) {
                        LOG.debug(">>>> Update postcondition failed for {} - expected status '{}' but actual status " +
                                "is '{}'", deposit.getId(), SUBMITTED, deposit.getDepositStatus());
                    }

                    success &= tr.success();

                    if (!success) {
                        LOG.debug(">>>> Update postcondition failed for {} - transport of package to endpoint " +
                                "failed: {}", deposit.getId(), tr.error().getMessage(), tr.error());
                    }

                    return success;
                },

                /*
                 * Critical update: Assemble and stream a package of content to the repository endpoint, update status to SUBMITTED
                 * Note: this Function accepts a Deposit resource, but returns a TransportResponse.  Both are used by the
                 * post-condition to determine the success of the CRI
                 */
                (deposit) -> {
                    Packager packager = dc.packager();
                    PackageStream packageStream = packager.getAssembler().assemble(dc.depositSubmission());
                    Map<String, String> packagerConfig = packager.getConfiguration();
                    try (TransportSession transport = packager.getTransport().open(packagerConfig)) {
                        TransportResponse tr = transport.send(packageStream, packagerConfig);
                        deposit.setDepositStatus(SUBMITTED);
                        return tr;
                    } catch (Exception e) {
                        throw new RuntimeException("Error closing transport session for deposit " +
                                dc.deposit().getId() + ": " + e.getMessage(), e);
                    }
                });


### Transport Configuration

### Status Mapping

### Policies



[1]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/spring-framework-reference/core.html#resources-implementations