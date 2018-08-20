# PASS Deposit Services

Deposit Services are responsible for the transfer of custodial content and metadata from end users to repositories.  End users transfer custody of their content to PASS by performing a submission through the HTML user interface, and Deposit Services subsequently transfers the custody of content to downstream repositories.

Deposit Services is deployed as "back-end" infrastructure.  It has no user-facing elements.  In particular, Deposit Services is unaware of the internal/external duality of resource URIs.  This means that when looking at URIs in Deposit Services' logging output, some adjustment may be necessary for a developer or systems operator to retrieve the resource from their location in the network topology. 

## Configuration

The primary mechanism for configuring Deposit Services is through environment variables.  This aligns with the patterns used in development and production infrastructure which rely on Docker and its approach to runtime configuration.

### Production Configuration Variables


|Environment Variable                           |Default Value                                                                  |Description|
|-----------------------------------------------|-------------------------------------------------------------------------------|-----------|
|`ACTIVEMQ_BROKER_URI`                          |`null`                                                                         |the publicly-supported (i.e. official PASS) variable for configuring the JMS broker URL.  used to compose the `SPRING_ACTIVEMQ_BROKER_URL`
|`DSPACE_HOST`                                  |localhost                                                                      |the IP address or host name of the server running the SWORD protocol version 2 endpoint
|`DSPACE_PORT`                                  |8181                                                                           |the TCP port exposing the SWORD protocol version 2 endpoint
|`ES_HOST`                                      |localhost                                                                      |the IP address or host name of the Elastic Search index.
|`ES_PORT`                                      |9200                                                                           |the TCP port running the Elastic Search HTTP API.
|`FCREPO_HOST`                                  |localhost                                                                      |the IP address or host name of the Fedora repository
|`FCREPO_JMS_PORT`                              |61616                                                                          |the TCP port for the STOMP protocol.
|`FCREPO_PORT`                                  |8080                                                                           |the TCP port running the Fedora HTTP REST API.
|`FTP_HOST`                                     |localhost                                                                      |the IP address or  host name of the NIH FTP server
|`FTP_PORT`                                     |21                                                                             |the TCP control port of the NIH FTP server
|`PASS_DEPOSIT_HTTP_AGENT`                      |pass-deposit/x.y.z                                                             |the value of the `User-Agent` header supplied on Deposit Services' HTTP requests.
|`PASS_DEPOSIT_JOBS_CONCURRENCY`                |2                                                                              |the number of Quartz jobs that may be run concurrently.
|`PASS_DEPOSIT_JOBS_DEFAULT_INTERVAL_MS`        |600000                                                                         |the amount of time, in milliseconds, that Quartz launches jobs.
|`PASS_DEPOSIT_JOBS_DISABLED`                   |undefined                                                                      |set this environment variable to `true` to disable all Quartz jobs.  By default this environment variable is undefined for the production runtime.
|`PASS_DEPOSIT_QUEUE_SUBMISSION_NAME`           |submission                                                                     |the name of the JMS queue that has messages pertaining to `Submission` resources (used by the `JmsSubmissionProcessor`)
|`PASS_DEPOSIT_QUEUE_DEPOSIT_NAME`              |deposit                                                                        |the name of the JMS queue that has messages pertaining to `Deposit` resources (used by the `JmsDepositProcessor`)
|`PASS_DEPOSIT_REPOSITORY_CONFIGURATION`         |classpath:/repositories.json                                                  |points to a properties file containing the configuration for the transport of custodial content to remote repositories.  Values must be [Spring Resource URIs][1].  See below for customizing the repository configuration values.
|`PASS_DEPOSIT_TRANSPORT_SWORDV2_SLEEP_TIME_MS` |10000                                                                          |the number of milliseconds to wait between depositing a package using SWORD, and checking the SWORD statement for the deposit state
|`PASS_DEPOSIT_WORKERS_CONCURRENCY`             |4                                                                              |the number of Deposit Worker threads that can simultaneously run.
|`PASS_ELASTICSEARCH_LIMIT`                     |100                                                                            |the maximum number of results returned in a single search response
|`PASS_ELASTICSEARCH_URL`                       |http://${es.host:localhost}:${es.port:9200}/pass                               |the URL used to communicate with the Elastic search API.  Normally this this variable does not need to be changed (see note below)
|`PASS_FEDORA_BASEURL`                          |http://${fcrepo.host:localhost}:${fcrepo.port:8080}/fcrepo/rest/               |the URL used to communicate with the Fedora REST API.  Normally this variable does not need to be changed (see note below)
|`PASS_FEDORA_PASSWORD`                         |moo                                                                            |the password used for `Basic` HTTP authentication to the Fedora REST API
|`PASS_FEDORA_USER`                             |fedoraAdmin                                                                    |the username used for `Basic` HTTP authentication to the Fedora REST API
|`SPRING_ACTIVEMQ_BROKER_URL`                   |${activemq.broker.uri:tcp://${fcrepo.host:localhost}:${fcrepo.jms.port:61616}} |the internal variable for configuring the URI of the JMS broker
|`SPRING_ACTIVEMQ_PASSWORD`                     |`null`                                                                         |Password to use when authenticating to the broker
|`SPRING_ACTIVEMQ_USER`                         |`null`                                                                         |User name to use when authenticating to the broker
|`SPRING_JMS_LISTENER_CONCURRENCY`              |4                                                                              |the number of JMS messages that can be processed simultaneously by _each_ JMS queue

> If the Fedora repository is deployed under a webapp context other than `/fcrepo`, or if `https` ought to be used instead of `http`, the environment variable `PASS_FEDORA_BASEURL` must be set to the base of the Fedora REST API (e.g. `PASS_FEDORA_BASEURL=https://fcrepo:8080/rest`)

> If the Elastic Search index is deployed under a url other than `/pass`, or if `https` ought to be used instead of `http`, the environment variable `PASS_ELASTICSEARCH_URL` must be set to the base of the Elastic Search HTTP API (e.g. `PASS_ELASTICSEARCH_URL=https://localhost:9200/index`)

### Repositories Configuration

The Repository configuration contains the parameters used for connecting to remote repositories.  Deposit Services comes with a default configuration, but a production environment will want to override the default.  Defaults are overridden by creating a copy of the default configuration, editing it to suit, and setting `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` to point to the new location.

The format of the configuration file is JSON, containing multiple repository configurations in a single file.  Each repository configuration has a top-level key that is used to identify that particular configuration.  The _default_ configuration is replicated below:

```json
{

  "JScholarship": {

    "deposit-config": {

      "processing": {
        "beanName" : "org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor"
      },

      "mapping": {
        "http://dspace.org/state/archived": "accepted",
        "http://dspace.org/state/withdrawn": "rejected",
        "default-mapping": "submitted"
      }
    },

    "assembler": {
      "specification": "http://purl.org/net/sword/package/METSDSpaceSIP"
    },

    "transport-config": {
      "auth-realms": [
        {
          "mech": "basic",
          "username": "user",
          "password": "pass",
          "url": "https://jscholarship.library.jhu.edu/"
        },
        {
          "mech": "basic",
          "username": "user",
          "password": "pass",
          "url": "https://dspace-prod.mse.jhu.edu:8080/"
        },
        {
          "mech": "basic",
          "username": "dspace-admin@oapass.org",
          "password": "foobar",
          "url": "http://${dspace.host}:${dspace.port}/swordv2"
        }
      ],

      "protocol-binding": {
        "protocol": "SWORDv2",
        "username": "dspace-admin@oapass.org",
        "password": "foobar",
        "server-fqdn": "${dspace.host}",
        "server-port": "${dspace.port}",
        "service-doc": "http://${dspace.host}:${dspace.port}/swordv2/servicedocument",
        "default-collection": "http://${dspace.host}:${dspace.port}/swordv2/collection/123456789/2",
        "on-behalf-of": null,
        "deposit-receipt": true,
        "user-agent": "pass-deposit/x.y.z"
      }
    }
  },

  "PubMed Central": {

    "deposit-config": {

      "processing": {

      },

      "mapping": {
        "INFO": "accepted",
        "ERROR": "rejected",
        "WARN": "rejected",
        "default-mapping": "submitted"
      }
    },

    "assembler": {
      "specification": "nihms-native-2017-07"
    },

    "transport-config": {
      "protocol-binding": {
        "protocol": "ftp",
        "username": "nihmsftpuser",
        "password": "nihmsftppass",
        "server-fqdn": "${ftp.host}",
        "server-port": "${ftp.port}",
        "data-type": "binary",
        "transfer-mode": "stream",
        "use-pasv": true,
        "default-directory": "/logs/upload/%s"
      }
    }
  }
}
```

#### Important Packager keys

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

To create your own configuration, copy and paste the default configuration into an empty file and modify the key values as described above.  The configuration _must_ be referenced by the `pass.deposit.repository.configuration` property, or is environment equivalent `PASS_DEPOSIT_REPOSITORY_CONFIGURATION`.  Allowed values are any Spring Resource path (e.g. `classpath:/`, `classpath*:`, `file:`, `http://`, `https://`).  For example, if your configuration is stored as a file in `/etc/deposit-services.json`, then you would set the environment variable `PASS_DEPOSIT_REPOSITORY_CONFIGURATION=file:/etc/deposit-services.json` prior to starting Deposit Services.  Likewise, if you kept the configuration accessible at a URL, you could use `PASS_DEPOSIT_REPOSITORY_CONFIGURATION=http://example.org/deposit-services.json`.

## Failure Handling

A "failed" `Deposit` or `Submission` has `Deposit.DepositStatus = FAILED` or `Submission.AggregateDepositStatus = FAILED`.  When a resource has been marked `FAILED`, Deposit Services will ignore any messages relating to the resource when in `listen` mode (see below for more information on modes).  Intervention (automated or manual) is required to update the failed resource.

A resource will be considered as failed when errors occur during the processing of `Submission` and `Deposit` resources.  Some errors may be caused by transient network issues, or a server being rebooted, but for now Deposit Services does not contain any logic for retrying when there are low-level communication errors with an endpoint.

`Submission` resources are failed when:
1.  Failure to build the Deposit Services model for a Submission
1.  There are no files attached to the Submission
1.  Any file attached to the Submission is missing a location URI (the URI used to retrieve the bytes of the file).
1.  An error occurs saving the state of the `Submission` in the repository (arguably a transient error, but DS does not perform any retries when there are errors communicating with the repository)

See `SubmissionProcessor` for details.  Right now, when a `Submission` is failed, manual intervention is required.  Deposit Services does not provide any support for dealing with failed submissions.  It is likely the end-user will need to re-create the submission in the user interface, and resubmit it.

`Deposit` resources are failed when:
1. An error occurs building a package
1. An error occurs streaming a package to a `Repository` (arguably transient)
1. An error occurs polling (arguably transient, but DS does not perform retries) or parsing the status of a `Deposit`
1. An error occurs saving the state of a `Deposit` in the repository (again, arguably transient, but DS doesn't perform retries when there are errors communicating with the repository)

See `DepositTask` for details.  Deposits fail for transient reasons; a server being down, an interruption in network communication, or invalid credentials for the downstream repository are just a few examples.  Manual intervention is required to remediate failed deposits, but Deposit Services provides support for this case (see the `retry` mode documented below).

## Build and Deployment

Deposit Services' primary artifact is a single self-executing jar.  The behavior, or "mode" of the deposit services application is directed by command line arguments and influenced by environment variables.  In the PASS infrastructure, the Deposit Services self-executing jar is deployed inside of a simple Docker container.

Deposit Services can be built by running:
* `mvn clean install`

The main Deposit Services deployment artifact is found in `deposit-messaging/target/deposit-messaging-<version>.jar`.  It is this jarfile that is included in the Docker image for Deposit Services, and posted on the GitHub Release  page.

## Supported modes

The mode is a required command-line argument which directs the deposit services application to take a specific action.

### Listen

Listen mode is the "primary" mode, if you will, of Deposit Services.  In `listen` mode Deposit Services responds to JMS messages from the Fedora repository by creating and transferring packages of custodial content to remote repositories. 

Listen mode is invoked by starting Deposit services with `listen` as the single command-line argument:

> $ java -jar deposit-services.jar listen

Deposit Services will connect to a JMS broker specified by the `SPRING_ACTIVEMQ_BROKER_URL` environment variable (optionally authenticating if `SPRING_ACTIVEMQ_USER` and `SPRING_ACTIVEMQ_PASSWORD` are present), and wait for the Fedora repository to be available as specified by `FCREPO_HOST` and `FCREPO_PORT`.  Notably, `listen` mode does not use the index.

> If the Fedora repository is deployed under a webapp context other than `/fcrepo`, the environment variable `PASS_FEDORA_BASEURL` must be set to the base of the Fedora REST API (e.g. `PASS_FEDORA_BASEURL=http://fcrepo:8080/fcrepo/rest`)

After successfully connecting to the JMS broker and the Fedora repository, deposit services will listen and respond to JMS messages relating to the submission and deposit of material to the Fedora repository.  Incoming `Submission` resources created by end-users of the UI will be processed:
* custodial content packaged
* packages sent to destination repositories
* confirmation of custody transfer
* recording the identities of content in destination repositories

Incoming `Deposit` resources will be used to update the overall success or failure of a `Submission`.

### Retry

Retry mode is used to retry a `Deposit` that has failed.  Retry mode is invoked by starting Deposit services with `retry` as the first command-line argument, with an optional `--uris` argument, accepting a space-separated list of `Deposit` URIs to retry.  If no `--uris` argument is present, the index is searched for _all_ `Deposit` resources that have failed, and those are the deposits that are re-tried.

To retry all failed deposits:
> $ java -jar deposit-services.jar retry

To retry specific deposits:
> $ java -jar deposit-services.jar retry --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/8e/af/ac/a9/8eafaca9-1f24-413a-bf1e-fbbd673ba45b --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/4a/cb/04/bb/4acb04bb-4f79-40ef-8ff9-e105261aa7fb

#### Refresh

Refresh mode is used to re-process a Deposit in the `SUBMITTED` state that needs its deposit status refreshed.  When `refresh` is invoked, the optional `--uris` argument is used to identify the `Deposit` resources to refresh.  Otherwise a search of the index is performed for _all_ `Deposit` resources in the `SUBMITTED` state.

Refreshing a `Deposit` means that its deposit status reference will be retrieved, parsed, and processed.  The status returned from the reference will be stored on the `Deposit`, and the status of the corresponding `RepositoryCopy` will be updated as well.  If the `Deposit` status is updated to `ACCEPTED`, the `RepositoryCopy` will be updated to `COMPLETE`.  If the `Deposit` status is updated to `REJECTED`, the `RepositoryCopy` will be updated to `REJECTED` as well.

To refresh all deposits in the `SUBMITTED` state:
> $ java -jar deposit-services.jar refresh

To refresh specific deposits:
> $ java -jar deposit-services.jar refresh --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/8e/af/ac/a9/8eafaca9-1f24-413a-bf1e-fbbd673ba45b --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/4a/cb/04/bb/4acb04bb-4f79-40ef-8ff9-e105261aa7fb

### Future modes

Modes to be supported by future releases of Deposit Services.

#### Report

TODO


# Developers

Deposit Services is implemented using Spring Boot, which heavily relies on Spring-based annotations and conventions to create and populate a Spring `ApplicationContext`, arguably the most important object managed by the Spring runtime.  Unfortunately, if you aren't familiar with Spring or its conventions, it can make the code harder to understand.

The entrypoint into the deposit services is the `DepositApp`, which accepts command line parameters that set the "mode" of the deposit services runtime.  Spring beans are created entirely in Java code by the `DepositConfig`  and `JmsConfig` classes.

## Runners

### ListenerRunner

The `listen` argument will invoke the `ListenerRunner`, which waits for the Fedora repository to be available, otherwise it shuts down the application.  Two JMS listeners are started that listen to the `submission` and `deposit` queues.  The `submission` queue provides messages relating to `Submission` resources, and the `deposit` queue provides messages relating to `Deposit` resources.  Deposit Services does not listen or act on messages for other types of repository resources.

The `PASS_FEDORA_USERNAME` and `PASS_FEDORA_PASSWORD` define the username and password used to perform HTTP `Basic` authentication to the Fedora HTTP REST API (i.e. `PASS_FEDORA_BASEURL`).

### FailedDepositRunner

The `retry` argument invokes the `FailedDepositRunner` which will re-submit failed `Deposit` resources to the task queue for processing.  URIs for specific Deposits may be specified, otherwise the index is searched for failed Deposits, and each one will be re-tried. 

### SubmittedUpdateRunner

The `refresh` argument invokes the `SubmittedUpdateRunner` which will attempt to re-process a `Deposit`'s status reference.  URIs for specific Deposits may be specified, otherwise the index is searched for `SUBMITTED` Deposits, and each one will be refreshed.   

## Message flow and concurrency

Each JMS listener (one each for the `deposit` and `submission` queues) can process messages concurrently.  The number of messages each listener can process concurrently is set by the property `spring.jms.listener.concurrency` (or its environment equivalent: `SPRING_JMS_LISTENER_CONCURRENCY`).

The `submission` queue is processed by the `JmsSubmissionProcessor`,which resolves the `Submission` resource represented in the message, and hands off processing to the `SubmissionProcessor`.  The `SubmissionProcessor` builds a `DepositSubmission`, which is the Deposit Services' analog of a `Submission` containing all of the metadata and custodial content associated with a  `Submission`.  After building the `DepositSubmission`, the processor creates a `DepositTask` and hands off the actual packaging and transfer of submission content to the deposit worker thread pool.  Importantly, the `SubmissionProcessor` updates the `Submission` resource in the repository as being _in progress_.

There is a thread pool of so-called "deposit workers" that perform the actual packaging and transport of custodial content to downstream repositories.  The size of the worker pool is determined by the property `pass.deposit.workers.concurrency` (or its environment equivalent: `PASS_DEPOSIT_WORKERS_CONCURRENCY`).  The deposit worker pool accepts instances of `DepositTask`, which contains the primary logic for packaging, streaming, and verifying the transfer of content from the PASS repository to downstream repositories.  The `DepositTask` will determine whether or not the transfer of custodial content has succeed, failed, or is indeterminable (i.e. an asyc deposit process that has not yet concluded).  The status of the `Deposit` resource associated with the `Submission` will be updated accordingly.  

## Common Abstractions and Patterns

### Failure Handling

Certain Spring sub-systems like Spring MVC, or Spring Messaging, support the notion of a "global" [`ErrorHandler`][2].  Deposit services provides an implementation **`DepositServicesErrorHandler`**, and it is used to catch exceptions thrown by the `JmsDepositProcessor`, `JmsSubmissionProcessor`, and is adapted as a [`Thread.UncaughtExceptionHandler`][3] and as a [`RejectedExecutionHandler`][4].

Deposit services provides a `DepositServicesRuntimeException` (`DSRE` for short), which has a field `PassEntity resource`.  If the `DepositServicesErrorHandler` catches a `DSRE` with a non-`null` resource, the error handler will test the type of the resource, mark it as failed, and save it in the repository.

The take-home point is: `Deposit` and `Submission` resources will be marked as failed if a `DepositServicesRuntimeException` is thrown from one of the JMS processors, or from the `DepositTask`.  As a developer, if an exceptional condition does **not** warrant a failure, then do not throw `DepositServicesRuntimeException`.  Instead, consider logging a warning or throwing a `DSRE` with a `null` resource.  Likewise, to fail a resource, all you need to do is throw a `DSRE` with a non-`null` resource.  The `DepositServicesErrorHandler` will do the rest.

Finally, one last word.  Because the state of a resource can be modified at any time by any actor in the PASS infrastructure, the `DepositServicesErrorHandler` encapsulates the act of saving the failed state of a resource within a `CRI`.  A _pre-condition_ for updating the resource is that it must _not_ be in a _terminal_ state.  For example, if the error handler is updating the state from `SUBMITTED` to `FAILED`, but another actor has modified the state of the resource to `REJECTED` in the interim, the _pre-condition_ will fail.  It makes no sense to modify the state of a resource after it is in its _terminal_ state.  The take-home point is: the `DepositServicesErrorHandler` will not mark a resource as failed if it is in a _terminal_ state.

### CriticalRepositoryInteraction

A central, yet awkwardly-named, abstraction is `CriticalRepositoryInteraction`.  This interface is used to prevent interleaved updates of individual repository resources by different threads.  A `CriticalRepositoryInteraction` (`CRI` for short) isolates the execution of a `Function` on a specific repository resource, and provides the boilerplate (i.e. template) for retrieving and updating the state of the resource.  There are four main components to `CriticalRepositoryInteraction`: the repository resource itself, a _pre-condition_, _post-condition_, and the _critical_ update (i.e. the `Function` to be executed).  The only implementation of `CRI` is the class `CriticalPath`, and the particulars of that implementation are discussed below.

1. First, `CriticalPath` obtains a lock over the string form of the URI of the resource being updated.  This insures that any other threads executing a `CRI` for the _same resource_ _in the same JVM_ must wait their turn before executing their critical update of the resource.

> This occurs more often than one might think, as Deposit Services receives many messages for the same resource almost "all at once" when a submission occurs.  The thread model for Spring and the Deposit Workers would be rife with conflicts unless something like the `CRI` was uniformly adopted in Deposit Services.

2. Second, the resource is read from the repository.

3. Third, the _pre-condition_ `Predicate` is executed over the resource.  If the _pre-condition_ fails, the entire `CriticalPath` is failed, and returns.

4. Fourth, the critical `Function` is executed, assured that the resource _at the time it was retrieved_ in step 2 meets the _pre-condition_ applied in step 3.  It is assumed that the `Function` modifies the state of the resource.  The `Function` may return the updated state of the resource, or it may return an entirely different object (remember the `Function` is parameterized by two types; while it _must_ accept a `PassEntity`, it does not have to return a `PassEntity`). 

5. After updating the state of the resource in step 4, an attempt is made to store and re-read the updated resource in the repository.  In this step, an `UpdateConflictException` may occur, because some other process outside of the JVM may have modified the resource after step 2 but before step 5.  If `UpdateConflictException` is caught, it is the responsibility of the `ConflictHandler` to resolve the conflict.  Otherwise, the update is successful, and processing of the resource by the `CriticalPath` continues.

6.  Finally, the _post-condition_ `BiPredicate` is executed.  It accepts the resource as updated and read by step 5, and the object returned by the critical update in step 4.  This determines the logical success or failure of the `CriticalPath`.  Steps 1 through 5 may have executed without error, but the _post-condition_ has final say of the overall success of the `CriticalPath`.  
  
### CriticalRepositoryInteraction Example

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

### Status

Deposit services primarily acts on three types of resources: `Submission`, `Deposit`, and `RepositoryCopy`.  Each of these resources carries a status.  Managing and reacting to the values of resource status is a large part of what Deposit services does.

Abstractly, Deposit services considers the value of any status to be _intermediate_, or _terminal_.

> It isn't clear, yet, whether this abstract notion of _intermediate_ and _terminal_ need to be shared amongst components of PASS.  If so, then certain classes and interfaces in the Deposit Services code base should be extracted out into a shared component.  

The semantics of _terminal_ state are that the resource has been through a workflow of some kind, and has reached the end of that workflow.  Because the workflow has reached a terminus, no additional state is expected to be placed on the resource, and no existing state of the resource is expected to change.

The semantics of _intermediate_ state are that the resource is currently in a workflow of some kind, and has yet to reach the end of that workflow.  Because the workflow has _not_ reached a terminus, the resource is expected to be modified at any time, until the _terminal_ state is achieved.

A general pattern within Deposit services is that resources with _terminal_ status are explicitly accounted for (this is largely enforced by _policies_ which are documented elsewhere), and are considered "read-only".

#### Submission Status

Submission status is enumerated in the `AggregatedDepositStatus` class.  Deposit services considers the following values:
* `NOT_STARTED` (_intermediate_): Incoming Submissions from the UI must have this status value
* `IN_PROGRESS` (_intermediate_): Deposit services places the Submission in an `IN_PROGRESS` state right away.  When a thread observes a `Submission` in this state, it assumes that _another_ thread is processing this resource.
* `FAILED` (_intermediate_): Occurs when a non-recoverable error happens when processing the `Submission`
* `ACCEPTED` (_terminal_): Deposit services places the Submission into this state when all of its `Deposit`s have been `ACCEPTED`
* `REJECTED` (_terminal_): Deposit services places the Submission into this state when all of its `Deposit`s have been `REJECTED`

#### Deposit Status

Deposit status is enumerated in the `DepositStatus` class.  Deposit services considers the following values:
*  `SUBMITTED` (_intermediate_): the custodial content of the `Submission` has been successfully transferred to the `Deposit`s `Repository`
*  `ACCEPTED` (_terminal_): the custodial content of the `Submission` has been accessioned by the `Deposit`s `Repository` (i.e. custody of the `Submission` has successfully been transferred to the downstream `Repository`)
*  `REJECTED` (_terminal_): the custodial content of the `Submission` has been rejected by the `Deposit`'s `Repository` (i.e. the downstream `Repository` has refused to accept custody of the `Submission` content)
*  `FAILED` (_intermediate_): the transfer of custodial content to the `Repository` failed, or there was some other error updating the status of the `Deposit`

#### RepositoryCopy Status

RepositoryCopy status is enumerated in the `CopyStatus` class.  Deposit services considers the following values:
* `COMPLETE` (_terminal_): a copy of the custodial content is available in the `Repository` at this location
* `IN_PROGRESS` (_intermediate_): a copy of the custodial content is _expected to be_ available in the `Repository` at this location.  The custodial content should not be expected to exist until the `Deposit` status is `ACCEPTED`
* `REJECTED` (_terminal_): the copy should be considered to be invalid.  Even if the custodial content is made available at the location indicated by the `RepositoryCopy`, it should not be mistaken for a successful transfer of custody.

RepositoryCopy status is subservient to the Deposit status.  They will always be congruent.  For example, a RepositoryCopy cannot be `COMPLETE` if the Deposit is `REJECTED`.  If a Deposit is `REJECTED`, then the RepositoryCopy must also be `REJECTED`.

#### Common Permutations

There are some common permutations of these statuses that will be observed:
* `ACCEPTED` `Submission`s will only have `Deposit`s that are `ACCEPTED`.  Each `Deposit` will have a `COMPLETE` `RepositoryCopy`.
* `REJECTED` `Submission`s will only have `Deposit`s that are `REJECTED`.  `REJECTED` `Deposit`s will not have any `RepositoryCopy` at all.
* `IN_PROGRESS` `Submission`s may have zero or more `Deposit`s in any state.
* `FAILED` `Submission`s should have zero `Deposit`s.
* `ACCEPTED` `Deposit`s should have a `COMPLETE` `RepositoryCopy`.
* `REJECTED` `Deposit`s will have a `REJECTED` `RepositoryCopy`
* `SUBMITTED` `Deposit`s will have an `IN_PROGRESS` `RepositoryCopy`
* `FAILED` `Deposit`s will have no `RepositoryCopy`


### Policies



[1]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/spring-framework-reference/core.html#resources-implementations
[2]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/javadoc-api/org/springframework/util/ErrorHandler.html
[3]: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.UncaughtExceptionHandler.html
[4]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/RejectedExecutionHandler.html