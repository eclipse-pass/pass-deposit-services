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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

@EnableJms
public class DrainQueueConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DrainQueueConfig.class);

    @JmsListener(destination = "deposit")
    @JmsListener(destination = "submission")
    public void drain(Message msg) {
        try {
            LOG.trace(">>>> draining message {}", msg.getJMSMessageID());
            msg.acknowledge();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public DefaultJmsListenerContainerFactory drainQueueJmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setConcurrency("2");
        factory.setConnectionFactory(connectionFactory);
        factory.setAutoStartup(true);
        return factory;
    }
}
