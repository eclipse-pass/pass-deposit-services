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

import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.JmsProperties;
import org.springframework.messaging.Message;

import javax.jms.JMSException;
import javax.jms.Session;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import static java.time.Instant.ofEpochMilli;

/**
 * Utility methods for deposit messaging.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DepositUtil.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final String UTC = "UTC";

    static final String UNKNOWN_DATETIME = "UNKNOWN";

    /**
     * Returns true if the {@code Message} in the supplied {@link MessageContext} has the specified {@code eventType}
     * and {@code resourceType}.  Useful for filtering creation events of Submission resources.
     *
     * @param eventType the Fedora event type, may be a comma-delimited multi-value string
     * @param resourceType the Fedora resource type, may be a comma-delimited multi-value string
     * @param mc the message context
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
        return (timeStamp > 0) ? TIME_FORMATTER.format(ofEpochMilli(timeStamp).atZone(ZoneId.of(UTC))) : UNKNOWN_DATETIME;
    }

    /**
     * Obtain the acknowledgement mode of the {@link Session} as a String.
     *
     * @param session the JMS session
     * @param dateTime the formatted date and time the message was received
     * @param id the identifier of the received message
     * @return the acknowlegement mode as a {@code String}
     */
    public static String parseAckMode(Session session, String dateTime, String id) {
        String ackMode;
        try {
            JmsProperties.AcknowledgeMode mode = asAcknowledgeMode(session.getAcknowledgeMode());
            ackMode = mode.name();
        } catch (Exception e) {
            LOG.trace("Unknown acknowledgement mode for message received {}, id: {}", dateTime, id);
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
        }

        throw new RuntimeException("Unknown acknowledgement mode for session: " + mode);
    }

    /**
     * Splits a comma-delimited multi-valued string into individual strings, and tests whether {@code toMatch} matches
     * any of the values.
     *
     * @param toMatch the String to match
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
     * @param eventType the type of the event from Fedora, comma-delimited multi-value
     * @param timestamp the timestamp of the message
     * @param id the identifier of the message
     * @param session the JMS session that received the message
     * @param message the message, in the Spring domain model
     * @param jmsMessage the message, in the native JMS model
     * @return an Object with references to the context of an incoming JMS message
     */
    public static MessageContext toMessageContext(String resourceType, String eventType, long timestamp, String id, Session
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
     * @param depositResource the {@code Deposit} itself
     * @param submission the {@code Submission} the {@code Deposit} is for
     * @param depositSubmission the {@code Submission} adapted to the deposit services model
     * @param repository the {@code Repository} the custodial content should be transferred to
     * @param packager the {@code Packager} used to assemble and stream the custodial content
     * @return an Object with references necessary for a {@code DepositTask} to be executed
     */
    public static DepositWorkerContext toDepositWorkerContext(Deposit depositResource, Submission submission, DepositSubmission depositSubmission,
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
            mc.jmsMessage().acknowledge();
        } catch (JMSException e) {
            LOG.error("Error acknowledging message (ack mode: {}): {} {}", mc.ackMode(), mc.dateTime(), mc.id(), e);
        }
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
         * @return
         */
        public String resourceType() {
            return resourceType;
        }

        /**
         * The type of the event from Fedora, comma-delimited multi-value
         *
         * @return
         */
        public String eventType() {
            return eventType;
        }

        /**
         * The identifier of the message
         *
         * @return
         */
        public String id() {
            return id;
        }

        /**
         * The JMS acknowledgement mode, as a String
         *
         * @return
         */
        public String ackMode() {
            return ackMode;
        }

        /**
         * The formatted timestamp of the message
         *
         * @return
         */
        public String dateTime() {
            return dateTime;
        }

        /**
         * The timestamp of the message
         *
         * @return
         */
        public long timestamp() {
            return timestamp;
        }

        /**
         * The JMS session that received the message
         *
         * @return
         */
        public Session session() {
            return session;
        }

        /**
         * The message, in the Spring domain model
         *
         * @return
         */
        public Message<String> message() {
            return message;
        }

        /**
         * The message, in the native JMS model
         *
         * @return
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
         * @return
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
         * @return
         */
        public DepositSubmission depositSubmission() {
            return depositSubmission;
        }

        /**
         * the {@code Repository} the custodial content should be transferred to
         *
         * @return
         */
        public Repository repository() {
            return repository;
        }

        /**
         * the {@code Packager} used to assemble and stream the custodial content
         *
         * @return
         */
        public Packager packager() {
            return packager;
        }

        /**
         * the {@code Submission} the {@code Deposit} is for
         *
         * @return
         */
        public Submission submission() {
            return submission;
        }

        /**
         * the {@code RepositoryCopy} created by a successful deposit
         *
         * @return
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
         * @return
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
