/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.deposit.messaging.service;

import static java.time.Instant.ofEpochMilli;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.FAILED;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import javax.jms.JMSException;
import javax.jms.Session;

import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.TerminalDepositStatusPolicy;
import org.dataconservancy.pass.deposit.messaging.policy.TerminalSubmissionStatusPolicy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusEvaluator;
import org.dataconservancy.pass.deposit.messaging.status.SubmissionStatusEvaluator;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.JmsProperties;
import org.springframework.messaging.Message;

/**
 * Utility methods for deposit messaging.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositUtil {

    private DepositUtil() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DepositUtil.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final String UTC = "UTC";

    private static final TerminalDepositStatusPolicy TERMINAL_DEPOSIT_STATUS_POLICY =
            new TerminalDepositStatusPolicy(new DepositStatusEvaluator());

    private static final TerminalSubmissionStatusPolicy TERMINAL_SUBMISSION_STATUS_POLICY = new
        TerminalSubmissionStatusPolicy(new SubmissionStatusEvaluator());

    static final String UNKNOWN_DATETIME = "UNKNOWN";

    /**
     * Returns true if the {@code Message} in the supplied {@link MessageContext} has the specified {@code eventType}
     * and {@code resourceType}.  Useful for filtering creation events of Submission resources.
     *
     * @param eventType    the Fedora event type, may be a comma-delimited multi-value string
     * @param resourceType the Fedora resource type, may be a comma-delimited multi-value string
     * @param mc           the message context
     * @return true if the message matches {@code eventType} and {@code resourceType}
     */
    public static boolean isMessageA(String eventType, String resourceType, MessageContext mc) {
        if (!((csvStringContains(eventType, mc.eventType()) && csvStringContains(resourceType, mc.resourceType())))) {
            return false;
        }
        return true;
    }

    /**
     * Parses a timestamp into a formatted date and time string.
     *
     * @param timeStamp the timestamp
     * @return a formatted date and time string
     */
    public static String parseDateTime(long timeStamp) {
        return (timeStamp > 0) ? TIME_FORMATTER.format(
            ofEpochMilli(timeStamp).atZone(ZoneId.of(UTC))) : UNKNOWN_DATETIME;
    }

    /**
     * Obtain the acknowledgement mode of the {@link Session} as a String.
     *
     * @param session  the JMS session
     * @param dateTime the formatted date and time the message was received
     * @param id       the identifier of the received message
     * @return the acknowlegement mode as a {@code String}
     */
    public static String parseAckMode(Session session, String dateTime, String id) {
        String ackMode;
        try {
            JmsProperties.AcknowledgeMode mode = asAcknowledgeMode(session.getAcknowledgeMode());
            ackMode = mode.name();
        } catch (Exception e) {
            ackMode = "UNKNOWN";
        }
        return ackMode;
    }

    /**
     * Converts the acknowledgement mode from the JMS {@link Session} to the Spring {@link
     * JmsProperties.AcknowledgeMode}.
     *
     * @param mode the mode from the JMS {@code Session}
     * @return the Spring {@code AcknowledgeMode}
     * @throws RuntimeException if the supplied {@code mode} cannot be mapped to a Spring {@code AcknowledgeMode}.
     */
    public static JmsProperties.AcknowledgeMode asAcknowledgeMode(int mode) {
        switch (mode) {
            case Session.AUTO_ACKNOWLEDGE:
                return JmsProperties.AcknowledgeMode.AUTO;
            case Session.CLIENT_ACKNOWLEDGE:
                return JmsProperties.AcknowledgeMode.CLIENT;
            case Session.DUPS_OK_ACKNOWLEDGE:
                return JmsProperties.AcknowledgeMode.DUPS_OK;
            default:
        }

        throw new RuntimeException("Unknown acknowledgement mode for session: " + mode);
    }

    /**
     * Splits a comma-delimited multi-valued string into individual strings, and tests whether {@code toMatch} matches
     * any of the values.
     *
     * @param toMatch       the String to match
     * @param csvCandidates a String that may contain multiple values separated by commas
     * @return true if {@code toMatch} is contained within {@code csvCandidates}
     */
    public static boolean csvStringContains(String toMatch, String csvCandidates) {
        if (csvCandidates == null || csvCandidates.trim().length() == 0) {
            return false;
        }

        return Stream.of(csvCandidates.split(","))
                     .anyMatch(candidateType -> candidateType.trim().equals(toMatch));
    }

    /**
     * Creates a convenience object that holds references to the objects related to an incoming JMS message.
     *
     * @param resourceType the type of the resource in Fedora, comma-delimited multi-value
     * @param eventType    the type of the event from Fedora, comma-delimited multi-value
     * @param timestamp    the timestamp of the message
     * @param id           the identifier of the message
     * @param session      the JMS session that received the message
     * @param message      the message, in the Spring domain model
     * @param jmsMessage   the message, in the native JMS model
     * @return an Object with references to the context of an incoming JMS message
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static MessageContext toMessageContext(String resourceType, String eventType, long timestamp, String id,
                                                  Session
                                                      session, Message message, javax.jms.Message jmsMessage) {
        MessageContext mc = new MessageContext();
        mc.resourceType = resourceType;
        mc.eventType = eventType;
        mc.timestamp = timestamp;
        mc.dateTime = parseDateTime(timestamp);
        mc.id = id;
        mc.ackMode = parseAckMode(session, mc.dateTime, id);
        mc.message = message;
        mc.jmsMessage = jmsMessage;
        mc.session = session;

        return mc;
    }

    /**
     * Creates a convenience object that holds references to the objects related to performing a deposit.
     *
     * @param depositResource   the {@code Deposit} itself
     * @param submission        the {@code Submission} the {@code Deposit} is for
     * @param depositSubmission the {@code Submission} adapted to the deposit services model
     * @param repository        the {@code Repository} the custodial content should be transferred to
     * @param packager          the {@code Packager} used to assemble and stream the custodial content
     * @return an Object with references necessary for a {@code DepositTask} to be executed
     */
    public static DepositWorkerContext toDepositWorkerContext(Deposit depositResource, Submission submission,
                                                              DepositSubmission depositSubmission,
                                                              Repository repository, Packager packager) {
        DepositWorkerContext dc = new DepositWorkerContext();
        dc.depositResource = depositResource;
        dc.depositSubmission = depositSubmission;
        dc.repository = repository;
        dc.packager = packager;
        dc.submission = submission;
        return dc;
    }

    /**
     * {@link Session#CLIENT_ACKNOWLEDGE acknowledges} a JMS message.
     *
     * @param mc the MessageContext
     */
    public static void ackMessage(MessageContext mc) {
        try {
            LOG.trace("Acking JMS message {}", mc.id());
            mc.jmsMessage().acknowledge();
        } catch (JMSException e) {
            LOG.error("Error acknowledging message (ack mode: {}): {} {}", mc.ackMode(), mc.dateTime(), mc.id(), e);
        }
    }

    /**
     * Uses the {@code cri} to update the referenced {@code Submission} {@code aggregatedDepositStatus} to {@code
     * FAILED}.  Submissions that are already in a <em>terminal</em> state will <em>not</em> be modified by this method.
     * That is to say, a {@code Submission} that has already been marked {@code ACCEPTED} or {@code REJECTED} cannot be
     * later marked as {@code FAILED} (even if the thread calling this method perceives a {@code Submission} as {@code
     * FAILED}, another thread may have succeeded in the interim).
     *
     * @param submissionUri the URI of the submission
     * @param cri           the critical repository interaction
     * @return true if the {@code Submission} was marked {@code FAILED}
     */
    public static boolean markSubmissionFailed(URI submissionUri, CriticalRepositoryInteraction cri) {
        CriticalResult<Submission, Submission> updateResult = cri.performCritical(
                submissionUri, Submission.class,
                (submission) -> !TERMINAL_SUBMISSION_STATUS_POLICY.test(submission.getAggregatedDepositStatus()),
                (submission) -> submission.getAggregatedDepositStatus() == FAILED,
                (submission) -> {
                    submission.setAggregatedDepositStatus(FAILED);
                    return submission;
                });

        if (!updateResult.success()) {
            LOG.debug(
                    "Updating status of {} to {} failed: {}",
                    submissionUri,
                    FAILED,
                    updateResult.throwable().isPresent() ?
                            updateResult.throwable().get().getMessage() : "(missing Throwable cause)",
                    updateResult.throwable().get());
        } else {
            LOG.debug("Marked {} as FAILED.", submissionUri);
        }

        return updateResult.success();
    }

    /**
     * Uses the {@code cri} to update the referenced {@code Deposit} {@code DepositStatus} to {@code FAILED}.  Deposits
     * that are already in a <em>terminal</em> state will <em>not</em> be modified by this method. That is to say, a
     * {@code Deposit} that has already been marked {@code ACCEPTED} or {@code REJECTED} cannot be later marked as
     * {@code FAILED} (even if the thread calling this method perceives a {@code Deposit} as {@code FAILED}, another
     * thread may have succeeded in the interim).
     *
     * @param depositUri the URI of the deposit
     * @param cri        the critical repository interaction
     * @return true if the {@code Deposit} was marked {@code FAILED}
     */
    public static boolean markDepositFailed(URI depositUri, CriticalRepositoryInteraction cri) {
        CriticalResult<Deposit, Deposit> updateResult = cri.performCritical(
                depositUri, Deposit.class,
                (deposit) -> !TERMINAL_DEPOSIT_STATUS_POLICY.test(deposit.getDepositStatus()),
                (deposit) -> deposit.getDepositStatus() == Deposit.DepositStatus.FAILED,
                (deposit) -> {
                    deposit.setDepositStatus(Deposit.DepositStatus.FAILED);
                    return deposit;
                });

        if (!updateResult.success()) {
            LOG.debug("Updating status of {} to {} failed: {}", depositUri, Deposit.DepositStatus.FAILED,
                      updateResult.throwable()
                                  .isPresent() ? updateResult.throwable().get()
                                                             .getMessage() : "(missing Throwable cause)",
                      updateResult.throwable().get());
        } else {
            LOG.debug("Marked {} as FAILED.", depositUri);
        }

        return updateResult.success();
    }

    /**
     * Holds references to objects related to an incoming JMS message.
     */
    public static class MessageContext {
        private String resourceType;
        private String eventType;
        private long timestamp;
        private String dateTime;
        private String id;
        private String ackMode;
        private Session session;
        private Message<String> message;
        private javax.jms.Message jmsMessage;

        /**
         * The type of the resource in Fedora, comma-delimited multi-value
         *
         * @return the multi-valued Fedora resource type
         */
        public String resourceType() {
            return resourceType;
        }

        /**
         * The type of the event from Fedora, comma-delimited multi-value
         *
         * @return the multi-valued Fedora event type
         */
        public String eventType() {
            return eventType;
        }

        /**
         * The identifier of the message
         *
         * @return the message identifier
         */
        public String id() {
            return id;
        }

        /**
         * The JMS acknowledgement mode, as a String
         *
         * @return the JMS acknowledgement mode
         */
        public String ackMode() {
            return ackMode;
        }

        /**
         * The formatted timestamp of the message
         *
         * @return the formatted message timestamp
         */
        public String dateTime() {
            return dateTime;
        }

        /**
         * The timestamp of the message
         *
         * @return the message timestamp
         */
        public long timestamp() {
            return timestamp;
        }

        /**
         * The JMS session that received the message
         *
         * @return the JMS Session
         */
        public Session session() {
            return session;
        }

        /**
         * The message, in the Spring domain model
         *
         * @return the Spring JMS Message
         */
        public Message<String> message() {
            return message;
        }

        /**
         * The message, in the native JMS model
         *
         * @return the native JMS Message
         */
        public javax.jms.Message jmsMessage() {
            return jmsMessage;
        }
    }

    /**
     * Holds references to objects related to performing a deposit by a {@link DepositTask}
     */
    public static class DepositWorkerContext {
        private Deposit depositResource;
        private DepositSubmission depositSubmission;
        private Submission submission;
        private Repository repository;
        private Packager packager;
        private RepositoryCopy repoCopy;
        private String statusUri;

        /**
         * the {@code Deposit} itself
         *
         * @return the Deposit
         */
        public Deposit deposit() {
            return depositResource;
        }

        public void deposit(Deposit deposit) {
            this.depositResource = deposit;
        }

        /**
         * the {@code Submission} adapted to the deposit services model
         *
         * @return the DepositSubmission
         */
        public DepositSubmission depositSubmission() {
            return depositSubmission;
        }

        /**
         * the {@code Repository} the custodial content should be transferred to
         *
         * @return the Repository
         */
        public Repository repository() {
            return repository;
        }

        public void repository(Repository repository) {
            this.repository = repository;
        }

        /**
         * the {@code Packager} used to assemble and stream the custodial content
         *
         * @return the Packager
         */
        public Packager packager() {
            return packager;
        }

        /**
         * the {@code Submission} the {@code Deposit} is for
         *
         * @return the Submission
         */
        public Submission submission() {
            return submission;
        }

        public void submission(Submission submission) {
            this.submission = submission;
        }

        /**
         * the {@code RepositoryCopy} created by a successful deposit
         *
         * @return the RepositoryCopy
         */
        public RepositoryCopy repoCopy() {
            return repoCopy;
        }

        public void repoCopy(RepositoryCopy repoCopy) {
            this.repoCopy = repoCopy;
        }

        /**
         * a URI that may be polled to determine the status of a Deposit
         *
         * @return the status URI
         */
        public String statusUri() {
            return statusUri;
        }

        public void statusUri(String statusUri) {
            this.statusUri = statusUri;
        }

        @Override
        public String toString() {
            return "DepositWorkerContext{" +
                   "depositResource=" + depositResource +
                   ", depositSubmission=" + depositSubmission +
                   ", submission=" + submission +
                   ", repository=" + repository +
                   ", packager=" + packager +
                   ", repoCopy=" + repoCopy +
                   ", statusUri='" + statusUri + '\'' +
                   '}';
        }
    }
}
