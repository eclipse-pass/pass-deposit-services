# PASS Deposit Services

Deposit Services are responsible for the transfer of custodial content and metadata from end users to repositories. End
users transfer custody of their content to PASS by performing a submission through the HTML user interface, and Deposit
Services subsequently transfers the custody of content to downstream repositories.

Deposit Services is deployed as "back-end" infrastructure. It has no user-facing elements. In particular, Deposit
Services is unaware of the internal/external duality of resource URIs. This means that when looking at URIs in Deposit
Services' logging output, some adjustment may be necessary for a developer or systems operator to retrieve the resource
from their location in the network topology.

## Configuration

The primary mechanism for configuring Deposit Services is through environment variables. This aligns with the patterns
used in development and production infrastructure which rely on Docker and its approach to runtime configuration.

### Production Configuration Variables

|Environment Variable                           |Default Value                                                                  |Description|
|-----------------------------------------------|-------------------------------------------------------------------------------|-----------|
|`ACTIVEMQ_BROKER_URI`                          |`null`                                                                         |the publicly-supported (i.e. official PASS) variable for configuring the JMS broker URL. used to compose the `SPRING_ACTIVEMQ_BROKER_URL`
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
|`PASS_DEPOSIT_JOBS_DISABLED`                   |undefined                                                                      |set this environment variable to `true` to disable all Quartz jobs. By default this environment variable is undefined for the production runtime.
|`PASS_DEPOSIT_QUEUE_SUBMISSION_NAME`           |submission                                                                     |the name of the JMS queue that has messages pertaining to `Submission` resources (used by the `JmsSubmissionProcessor`)
|`PASS_DEPOSIT_QUEUE_DEPOSIT_NAME`              |deposit                                                                        |the name of the JMS queue that has messages pertaining to `Deposit` resources (used by the `JmsDepositProcessor`)
|`PASS_DEPOSIT_REPOSITORY_CONFIGURATION`         |classpath:/repositories.json                                                  |points to a properties file containing the configuration for the transport of custodial content to remote repositories. Values must be [Spring Resource URIs][1]. See below for customizing the repository configuration values.
|`PASS_DEPOSIT_TRANSPORT_SWORDV2_SLEEP_TIME_MS` |10000                                                                          |the number of milliseconds to wait between depositing a package using SWORD, and checking the SWORD statement for the deposit state
|`PASS_DEPOSIT_TRANSPORT_SWORDV2_FOLLOW_REDIRECTS`|false|Specifically controls whether or not the `AtomFeedStatusResolver` follows HTTP redirects or not
|`PASS_DEPOSIT_WORKERS_CONCURRENCY`             |4                                                                              |the number of Deposit Worker threads that can simultaneously run.
|`PASS_ELASTICSEARCH_LIMIT`                     |100                                                                            |the maximum number of results returned in a single search response
|`PASS_ELASTICSEARCH_URL`                       |http://${es.host:localhost}:${es.port:9200}/pass                               |the URL used to communicate with the Elastic search API. Normally this this variable does not need to be changed (see note below)
|`PASS_FEDORA_BASEURL`                          |http://${fcrepo.host:localhost}:${fcrepo.port:8080}/fcrepo/rest/               |the URL used to communicate with the Fedora REST API. Normally this variable does not need to be changed (see note below)
|`PASS_FEDORA_PASSWORD`                         |moo                                                                            |the password used for `Basic` HTTP authentication to the Fedora REST API
|`PASS_FEDORA_USER`                             |fedoraAdmin                                                                    |the username used for `Basic` HTTP authentication to the Fedora REST API
|`SPRING_ACTIVEMQ_BROKER_URL`                   |${activemq.broker.uri:tcp://${fcrepo.host:localhost}:${fcrepo.jms.port:61616}} |the internal variable for configuring the URI of the JMS broker
|`SPRING_ACTIVEMQ_PASSWORD`                     |`null`                                                                         |Password to use when authenticating to the broker
|`SPRING_ACTIVEMQ_USER`                         |`null`                                                                         |User name to use when authenticating to the broker
|`SPRING_JMS_LISTENER_CONCURRENCY`              |4                                                                              |the number of JMS messages that can be processed simultaneously by _
each_ JMS queue

> If the Fedora repository is deployed under a webapp context other than `/fcrepo`, or if `https` ought to be used instead of `http`, the environment variable `PASS_FEDORA_BASEURL` must be set to the base of the Fedora REST API (e.g. `PASS_FEDORA_BASEURL=https://fcrepo:8080/rest`)

> If the Elastic Search index is deployed under a url other than `/pass`, or if `https` ought to be used instead of `http`, the environment variable `PASS_ELASTICSEARCH_URL` must be set to the base of the Elastic Search HTTP API (e.g. `PASS_ELASTICSEARCH_URL=https://localhost:9200/index`)

### Repositories Configuration

The Repository configuration contains the parameters used for connecting and depositing custodial material to downstream
repositories. The format of the configuration file is JSON, defining multiple downstream repositories in a single file.

Each repository configuration has a top-level key that is used to identify a particular configuration. Importantly, each
top-level key _must_ map to a [`Repository` resource][5] within the PASS repository. This implies that the top-level
keys in `repositories.json` are not arbitrary. In fact, the top level key must be one of:

* the value of a `Repository.repositoryKey` field (of a `Repository` resource in the PASS repository)
* the full URI of a `Repository` resource in the PASS repository
* a portion of the URI path of a `Repository` resource in the PASS repository

Given a `Repository` with a `repositoryKey` of `my-repo` and a URI
of `https://pass.my.edu/fcrepo/rest/repositories/77/cc/80/64/77cc8064-a918-4823-968d-2b17386db76d`, any of the following
top level keys are acceptable:

* `my-repo`
* `https://pass.my.edu/fcrepo/rest/repositories/77/cc/80/64/77cc8064-a918-4823-968d-2b17386db76d`
* `/repositories/77/cc/80/64/77cc8064-a918-4823-968d-2b17386db76d`
* `77cc8064-a918-4823-968d-2b17386db76d`

Deposit Services comes with a default repository configuration, but a production environment will want to override the
default. Defaults are overridden by creating a copy of the default configuration, editing it to suit, and
setting `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` to point to the new location.

> Acceptable values for `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` must be a form of [Spring Resource URI][1].

The _default_ configuration is replicated below:

```json
{
  "JScholarship": {
    "deposit-config": {
      "processing": {
        "beanName": "org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor"
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

#### Customizing Repository Configuration Elements

The default repository configuration will not be suitable for production. A production deployment needs to provide
updated authentication credentials and insure the correct value for the default SWORD collection URL
- `default-collection`. Each `transport-config` section should be reviewed for correctness, paying special attention
to `protocol-binding` and `auth-realm` blocks: update `username` and `password` elements, and insure correct values for
URLs.

Values may be parameterized by any property or environment variable.

To create your own configuration, copy and paste the default configuration into an empty file and modify the JSON as
described above. The configuration _must_ be referenced by the `pass.deposit.repository.configuration` property, or is
environment equivalent `PASS_DEPOSIT_REPOSITORY_CONFIGURATION`. Allowed values are any [Spring Resource path][1] (
e.g. `classpath:/`, `classpath*:`, `file:`, `http://`, `https://`). For example, if your configuration is stored as a
file in `/etc/deposit-services.json`, then you would set the environment
variable `PASS_DEPOSIT_REPOSITORY_CONFIGURATION=file:/etc/deposit-services.json` prior to starting Deposit Services.
Likewise, if you kept the configuration accessible at a URL, you could
use `PASS_DEPOSIT_REPOSITORY_CONFIGURATION=http://example.org/deposit-services.json`.

## Failure Handling

A "failed" `Deposit` or `Submission` has `Deposit.DepositStatus = FAILED`
or `Submission.AggregateDepositStatus = FAILED`. When a resource has been marked `FAILED`, Deposit Services will ignore
any messages relating to the resource when in `listen` mode (see below for more information on modes). Intervention (
automated or manual) is required to update the failed resource.

A resource will be considered as failed when errors occur during the processing of `Submission` and `Deposit` resources.
Some errors may be caused by transient network issues, or a server being rebooted, but for now Deposit Services does not
contain any logic for retrying when there are low-level communication errors with an endpoint.

`Submission` resources are failed when:

1. Failure to build the Deposit Services model for a Submission
1. There are no files attached to the Submission
1. Any file attached to the Submission is missing a location URI (the URI used to retrieve the bytes of the file).
1. An error occurs saving the state of the `Submission` in the repository (arguably a transient error, but DS does not
   perform any retries when there are errors communicating with the repository)

See `SubmissionProcessor` for details. Right now, when a `Submission` is failed, manual intervention is required.
Deposit Services does not provide any support for dealing with failed submissions. It is likely the end-user will need
to re-create the submission in the user interface, and resubmit it.

`Deposit` resources are failed when:

1. An error occurs building a package
1. An error occurs streaming a package to a `Repository` (arguably transient)
1. An error occurs polling (arguably transient, but DS does not perform retries) or parsing the status of a `Deposit`
1. An error occurs saving the state of a `Deposit` in the repository (again, arguably transient, but DS doesn't perform
   retries when there are errors communicating with the repository)

See `DepositTask` for details. Deposits fail for transient reasons; a server being down, an interruption in network
communication, or invalid credentials for the downstream repository are just a few examples. Manual intervention is
required to remediate failed deposits, but Deposit Services provides support for this case (see the `retry` mode
documented below).

## Build and Deployment

Deposit Services' primary artifact is a single self-executing jar. The behavior, or "mode" of the deposit services
application is directed by command line arguments and influenced by environment variables. In the PASS infrastructure,
the Deposit Services self-executing jar is deployed inside of a simple Docker container.

Deposit Services can be built by running:

* `mvn clean install`

The main Deposit Services deployment artifact is found in `deposit-messaging/target/deposit-messaging-<version>.jar`. It
is this jarfile that is included in the Docker image for Deposit Services, and posted on the GitHub Release page.

## Supported modes

The mode is a required command-line argument which directs the deposit services application to take a specific action.

### Listen

Listen mode is the "primary" mode, if you will, of Deposit Services. In `listen` mode Deposit Services responds to JMS
messages from the Fedora repository by creating and transferring packages of custodial content to remote repositories.

Listen mode is invoked by starting Deposit services with `listen` as the single command-line argument:

> $ java -jar deposit-services.jar listen

Deposit Services will connect to a JMS broker specified by the `SPRING_ACTIVEMQ_BROKER_URL` environment variable (
optionally authenticating if `SPRING_ACTIVEMQ_USER` and `SPRING_ACTIVEMQ_PASSWORD` are present), and wait for the Fedora
repository to be available as specified by `FCREPO_HOST` and `FCREPO_PORT`. Notably, `listen` mode does not use the
index.

> If the Fedora repository is deployed under a webapp context other than `/fcrepo`, the environment variable `PASS_FEDORA_BASEURL` must be set to the base of the Fedora REST API (e.g. `PASS_FEDORA_BASEURL=http://fcrepo:8080/fcrepo/rest`)

After successfully connecting to the JMS broker and the Fedora repository, deposit services will listen and respond to
JMS messages relating to the submission and deposit of material to the Fedora repository. Incoming `Submission`
resources created by end-users of the UI will be processed:

* custodial content packaged
* packages sent to destination repositories
* confirmation of custody transfer
* recording the identities of content in destination repositories

Incoming `Deposit` resources will be used to update the overall success or failure of a `Submission`.

### Retry

Retry mode is used to retry a `Deposit` that has failed. Retry mode is invoked by starting Deposit services with `retry`
as the first command-line argument, with an optional `--uris` argument, accepting a space-separated list of `Deposit`
URIs to retry. If no `--uris` argument is present, the index is searched for _all_ `Deposit` resources that have failed,
and those are the deposits that are re-tried.

To retry all failed deposits:
> $ java -jar deposit-services.jar retry

To retry specific deposits:
> $ java -jar deposit-services.jar retry --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/8e/af/ac/a9/8eafaca9-1f24-413a-bf1e-fbbd673ba45b --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/4a/cb/04/bb/4acb04bb-4f79-40ef-8ff9-e105261aa7fb

#### Refresh

Refresh mode is used to re-process a Deposit in the `SUBMITTED` state that needs its deposit status refreshed.
When `refresh` is invoked, the optional `--uris` argument is used to identify the `Deposit` resources to refresh.
Otherwise a search of the index is performed for _all_ `Deposit` resources in the `SUBMITTED` state.

Refreshing a `Deposit` means that its deposit status reference will be retrieved, parsed, and processed. The status
returned from the reference will be stored on the `Deposit`, and the status of the corresponding `RepositoryCopy` will
be updated as well. If the `Deposit` status is updated to `ACCEPTED`, the `RepositoryCopy` will be updated to `COMPLETE`
. If the `Deposit` status is updated to `REJECTED`, the `RepositoryCopy` will be updated to `REJECTED` as well.

To refresh all deposits in the `SUBMITTED` state:
> $ java -jar deposit-services.jar refresh

To refresh specific deposits:
> $ java -jar deposit-services.jar refresh --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/8e/af/ac/a9/8eafaca9-1f24-413a-bf1e-fbbd673ba45b --uri=http://192.168.99.100:8080/fcrepo/rest/deposits/4a/cb/04/bb/4acb04bb-4f79-40ef-8ff9-e105261aa7fb


[1]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/spring-framework-reference/core.html#resources-implementations

[2]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/javadoc-api/org/springframework/util/ErrorHandler.html

[3]: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.UncaughtExceptionHandler.html

[4]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/RejectedExecutionHandler.html

[5]: https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Repository.md