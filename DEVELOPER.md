# Developers

Deposit Services is implemented using Spring Boot, which heavily relies on Spring-based annotations and conventions to
create and populate a Spring `ApplicationContext`, arguably the most important object managed by the Spring runtime.
Unfortunately, if you aren't familiar with Spring or its conventions, it can make the code harder to understand.

The entrypoint into the deposit services is the `DepositApp`, which accepts command line parameters that set the "mode"
of the deposit services runtime. Spring beans are created entirely in Java code by the `DepositConfig`  and `JmsConfig`
classes.

## Runners

### ListenerRunner

The `listen` argument will invoke the `ListenerRunner`, which waits for the Fedora repository to be available, otherwise
it shuts down the application. Two JMS listeners are started that listen to the `submission` and `deposit` queues.
The `submission` queue provides messages relating to `Submission` resources, and the `deposit` queue provides messages
relating to `Deposit` resources. Deposit Services does not listen or act on messages for other types of repository
resources.

The `PASS_FEDORA_USERNAME` and `PASS_FEDORA_PASSWORD` define the username and password used to perform HTTP `Basic`
authentication to the Fedora HTTP REST API (i.e. `PASS_FEDORA_BASEURL`).

### FailedDepositRunner

The `retry` argument invokes the `FailedDepositRunner` which will re-submit failed `Deposit` resources to the task queue
for processing. URIs for specific Deposits may be specified, otherwise the index is searched for failed Deposits, and
each one will be re-tried.

### SubmittedUpdateRunner

The `refresh` argument invokes the `SubmittedUpdateRunner` which will attempt to re-process a `Deposit`'s status
reference. URIs for specific Deposits may be specified, otherwise the index is searched for `SUBMITTED` Deposits, and
each one will be refreshed.

## Message flow and concurrency

Each JMS listener (one each for the `deposit` and `submission` queues) can process messages concurrently. The number of
messages each listener can process concurrently is set by the property `spring.jms.listener.concurrency` (or its
environment equivalent: `SPRING_JMS_LISTENER_CONCURRENCY`).

The `submission` queue is processed by the `JmsSubmissionProcessor`,which resolves the `Submission` resource represented
in the message, and hands off processing to the `SubmissionProcessor`. The `SubmissionProcessor` builds
a `DepositSubmission`, which is the Deposit Services' analog of a `Submission` containing all of the metadata and
custodial content associated with a  `Submission`. After building the `DepositSubmission`, the processor creates
a `DepositTask` and hands off the actual packaging and transfer of submission content to the deposit worker thread pool.
Importantly, the `SubmissionProcessor` updates the `Submission` resource in the repository as being _in progress_.

There is a thread pool of so-called "deposit workers" that perform the actual packaging and transport of custodial
content to downstream repositories. The size of the worker pool is determined by the
property `pass.deposit.workers.concurrency` (or its environment equivalent: `PASS_DEPOSIT_WORKERS_CONCURRENCY`). The
deposit worker pool accepts instances of `DepositTask`, which contains the primary logic for packaging, streaming, and
verifying the transfer of content from the PASS repository to downstream repositories. The `DepositTask` will determine
whether or not the transfer of custodial content has succeed, failed, or is indeterminable (i.e. an asyc deposit process
that has not yet concluded). The status of the `Deposit` resource associated with the `Submission` will be updated
accordingly.

## Common Abstractions and Patterns

### Failure Handling

Certain Spring sub-systems like Spring MVC, or Spring Messaging, support the notion of a "global" [`ErrorHandler`][2].
Deposit services provides an implementation **`DepositServicesErrorHandler`**, and it is used to catch exceptions thrown
by the `JmsDepositProcessor`, `JmsSubmissionProcessor`, and is adapted as a [`Thread.UncaughtExceptionHandler`][3] and
as a [`RejectedExecutionHandler`][4].

Deposit services provides a `DepositServicesRuntimeException` (`DSRE` for short), which has a
field `PassEntity resource`. If the `DepositServicesErrorHandler` catches a `DSRE` with a non-`null` resource, the error
handler will test the type of the resource, mark it as failed, and save it in the repository.

The take-home point is: `Deposit` and `Submission` resources will be marked as failed if
a `DepositServicesRuntimeException` is thrown from one of the JMS processors, or from the `DepositTask`. As a developer,
if an exceptional condition does **not** warrant a failure, then do not throw `DepositServicesRuntimeException`.
Instead, consider logging a warning or throwing a `DSRE` with a `null` resource. Likewise, to fail a resource, all you
need to do is throw a `DSRE` with a non-`null` resource. The `DepositServicesErrorHandler` will do the rest.

Finally, one last word. Because the state of a resource can be modified at any time by any actor in the PASS
infrastructure, the `DepositServicesErrorHandler` encapsulates the act of saving the failed state of a resource within
a `CRI`. A _pre-condition_ for updating the resource is that it must _not_ be in a _terminal_ state. For example, if the
error handler is updating the state from `SUBMITTED` to `FAILED`, but another actor has modified the state of the
resource to `REJECTED` in the interim, the _pre-condition_ will fail. It makes no sense to modify the state of a
resource after it is in its _terminal_ state. The take-home point is: the `DepositServicesErrorHandler` will not mark a
resource as failed if it is in a _terminal_ state.

### CriticalRepositoryInteraction

A central, yet awkwardly-named, abstraction is `CriticalRepositoryInteraction`. This interface is used to prevent
interleaved updates of individual repository resources by different threads. A `CriticalRepositoryInteraction` (`CRI`
for short) isolates the execution of a `Function` on a specific repository resource, and provides the boilerplate (i.e.
template) for retrieving and updating the state of the resource. There are four main components
to `CriticalRepositoryInteraction`: the repository resource itself, a _pre-condition_, _post-condition_, and the _
critical_ update (i.e. the `Function` to be executed). The only implementation of `CRI` is the class `CriticalPath`, and
the particulars of that implementation are discussed below.

1. First, `CriticalPath` obtains a lock over the string form of the URI of the resource being updated. This insures that
   any other threads executing a `CRI` for the _same resource_ _in the same JVM_ must wait their turn before executing
   their critical update of the resource.

> This occurs more often than one might think, as Deposit Services receives many messages for the same resource almost "all at once" when a submission occurs. The thread model for Spring and the Deposit Workers would be rife with conflicts unless something like the `CRI` was uniformly adopted in Deposit Services.

2. Second, the resource is read from the repository.

3. Third, the _pre-condition_ `Predicate` is executed over the resource. If the _pre-condition_ fails, the
   entire `CriticalPath` is failed, and returns.

4. Fourth, the critical `Function` is executed, assured that the resource _at the time it was retrieved_ in step 2 meets
   the _pre-condition_ applied in step 3. It is assumed that the `Function` modifies the state of the resource.
   The `Function` may return the updated state of the resource, or it may return an entirely different object (remember
   the `Function` is parameterized by two types; while it _must_ accept a `PassEntity`, it does not have to return
   a `PassEntity`).

5. After updating the state of the resource in step 4, an attempt is made to store and re-read the updated resource in
   the repository. In this step, an `UpdateConflictException` may occur, because some other process outside of the JVM
   may have modified the resource after step 2 but before step 5. If `UpdateConflictException` is caught, it is the
   responsibility of the `ConflictHandler` to resolve the conflict. Otherwise, the update is successful, and processing
   of the resource by the `CriticalPath` continues.

6. Finally, the _post-condition_ `BiPredicate` is executed. It accepts the resource as updated and read by step 5, and
   the object returned by the critical update in step 4. This determines the logical success or failure of
   the `CriticalPath`. Steps 1 through 5 may have executed without error, but the _post-condition_ has final say of the
   overall success of the `CriticalPath`.

### CriticalRepositoryInteraction Example

Here is a real example of a `CRI` in action, used when packaging and depositing custodial content to a downstream
repository.

The _pre-condition_ insures that we are operating on `Deposit` resources acceptable for processing. The _critical
update_ creates a package and streams it to the remote repository, and obtains a `TransportResult`. The status of
the `Deposit` resource is modified, and the `TransportResult` is returned by the _critical update_. Finally, the _
post-condition_ uses the state of the `Deposit` resource _and_ the `TransportResult` to evaluate the success of the _
critical update_.

Behind the scenes, the `CriticalPath` is insuring that the state of the `Deposit` is properly stored in the repository,
and that any conflicts are handled.

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

Deposit services primarily acts on three types of resources: `Submission`, `Deposit`, and `RepositoryCopy`. Each of
these resources carries a status. Managing and reacting to the values of resource status is a large part of what Deposit
services does.

Abstractly, Deposit services considers the value of any status to be _intermediate_, or _terminal_.

> It isn't clear, yet, whether this abstract notion of _intermediate_ and _terminal_ need to be shared amongst components of PASS. If so, then certain classes and interfaces in the Deposit Services code base should be extracted out into a shared component.

The semantics of _terminal_ state are that the resource has been through a workflow of some kind, and has reached the
end of that workflow. Because the workflow has reached a terminus, no additional state is expected to be placed on the
resource, and no existing state of the resource is expected to change.

The semantics of _intermediate_ state are that the resource is currently in a workflow of some kind, and has yet to
reach the end of that workflow. Because the workflow has _not_ reached a terminus, the resource is expected to be
modified at any time, until the _terminal_ state is achieved.

A general pattern within Deposit services is that resources with _terminal_ status are explicitly accounted for (this is
largely enforced by _policies_ which are documented elsewhere), and are considered "read-only".

#### Submission Status

Submission status is enumerated in the `AggregatedDepositStatus` class. Deposit services considers the following values:

* `NOT_STARTED` (_intermediate_): Incoming Submissions from the UI must have this status value
* `IN_PROGRESS` (_intermediate_): Deposit services places the Submission in an `IN_PROGRESS` state right away. When a
  thread observes a `Submission` in this state, it assumes that _another_ thread is processing this resource.
* `FAILED` (_intermediate_): Occurs when a non-recoverable error happens when processing the `Submission`
* `ACCEPTED` (_terminal_): Deposit services places the Submission into this state when all of its `Deposit`s have
  been `ACCEPTED`
* `REJECTED` (_terminal_): Deposit services places the Submission into this state when all of its `Deposit`s have
  been `REJECTED`

#### Deposit Status

Deposit status is enumerated in the `DepositStatus` class. Deposit services considers the following values:

* `SUBMITTED` (_intermediate_): the custodial content of the `Submission` has been successfully transferred to
  the `Deposit`s `Repository`
* `ACCEPTED` (_terminal_): the custodial content of the `Submission` has been accessioned by the `Deposit`
  s `Repository` (i.e. custody of the `Submission` has successfully been transferred to the downstream `Repository`)
* `REJECTED` (_terminal_): the custodial content of the `Submission` has been rejected by the `Deposit`'s `Repository` (
  i.e. the downstream `Repository` has refused to accept custody of the `Submission` content)
* `FAILED` (_intermediate_): the transfer of custodial content to the `Repository` failed, or there was some other error
  updating the status of the `Deposit`

#### RepositoryCopy Status

RepositoryCopy status is enumerated in the `CopyStatus` class. Deposit services considers the following values:

* `COMPLETE` (_terminal_): a copy of the custodial content is available in the `Repository` at this location
* `IN_PROGRESS` (_intermediate_): a copy of the custodial content is _expected to be_ available in the `Repository` at
  this location. The custodial content should not be expected to exist until the `Deposit` status is `ACCEPTED`
* `REJECTED` (_terminal_): the copy should be considered to be invalid. Even if the custodial content is made available
  at the location indicated by the `RepositoryCopy`, it should not be mistaken for a successful transfer of custody.

RepositoryCopy status is subservient to the Deposit status. They will always be congruent. For example, a RepositoryCopy
cannot be `COMPLETE` if the Deposit is `REJECTED`. If a Deposit is `REJECTED`, then the RepositoryCopy must also
be `REJECTED`.

#### Common Permutations

There are some common permutations of these statuses that will be observed:

* `ACCEPTED` `Submission`s will only have `Deposit`s that are `ACCEPTED`. Each `Deposit` will have
  a `COMPLETE` `RepositoryCopy`.
* `REJECTED` `Submission`s will only have `Deposit`s that are `REJECTED`.  `REJECTED` `Deposit`s will not have
  any `RepositoryCopy` at all.
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

[5]: https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Repository.md