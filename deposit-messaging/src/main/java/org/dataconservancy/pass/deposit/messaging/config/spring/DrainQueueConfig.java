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

package org.dataconservancy.pass.deposit.messaging.config.spring;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/**
 * Provides a JMS listener which immediately {@link Message#acknowledge() acknowledge} each message received for its
 * configured queue or topic.  Effectively the listener will absorb any JMS messages without invoking any other
 * business logic.
 * <p>
 * This is useful, for example, when an executing IT produces JMS messages as a byproduct of test execution.  The IT may
 * not be interested at all in the JMS messages produced - they just occur as side affect of manipulating resources in
 * the Fedora repository, for example.
 * </p>
 * <p>
 * When {@code DrainQueueConfig} is introduced, it will connect to the {@code deposit} and {@code submission} queues.
 * The included {@code JmsListenerContainerFactory} insures the the listeners are started automatically, and set the
 * correct acknowledgement mode.
 * </p>
 * <p>
 * Importantly, {@code DrainQueueConfig} will conflict with {@link JmsConfig} if they are both present in a Spring
 * Application Context.  Only one or the other should be present.  Concretely, once an IT introduces
 * {@code DrainQueueConfig} into the Spring Application Context, it will be present in the context until removed.  To
 * easily avoid this issue, a good rule of thumb is: <em>If an IT uses {@code DrainQueueConfig}, the IT ought to
 * annotate the class as {@code DirtiesContext}</em>
 * </p>
 */
@EnableJms
public class DrainQueueConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DrainQueueConfig.class);

    @JmsListener(destination = "deposit")
    @JmsListener(destination = "submission")
    public void drain(Message msg) {
        try {
            LOG.trace("draining message {}", msg.getJMSMessageID());
            msg.acknowledge();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public DefaultJmsListenerContainerFactory drainQueueJmsListenerContainerFactory(
        ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setConcurrency("2");
        factory.setConnectionFactory(connectionFactory);
        factory.setAutoStartup(true);
        return factory;
    }
}
