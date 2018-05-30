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
package org.dataconservancy.pass.deposit.messaging;

import org.dataconservancy.pass.deposit.messaging.config.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.JmsConfig;
import org.dataconservancy.pass.deposit.messaging.runner.SubmittedUpdateRunner;
import org.dataconservancy.pass.deposit.messaging.runner.DirtyDepositRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.util.HashMap;


/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootApplication
@Import({DepositConfig.class})
@ComponentScan("org.dataconservancy.pass")
@ComponentScan("org.dataconservancy.nihms")
public class DepositApp {

    private static final Logger LOG = LoggerFactory.getLogger(DepositApp.class);

    private String fcrepoUser;

    private String fcrepoPass;

    private String fcrepoBaseUrl;

    public static void main(String[] args) {
        LOG.info(">>>> Starting DepositService");

        if (args.length < 1 || args[0] == null) {
            throw new IllegalArgumentException("Requires at least one argument!");
        }

        SpringApplication app = null;

        switch (args[0]) {
            case "listen": {
                app = new SpringApplication(DepositApp.class, JmsConfig.class);
                break;
            }
            case "update": {
                app = new SpringApplication(DepositApp.class, SubmittedUpdateRunner.class);
                // TODO figure out elegant way to exclude JMS-related beans like SubmissionProcessor from being spun up
                app.setDefaultProperties(new HashMap<String, Object>() { {
                    put("spring.jms.listener.auto-startup", Boolean.FALSE);
                }});
                break;
            }
            case "process": {
                app = new SpringApplication(DepositApp.class, DirtyDepositRunner.class);
                app.setDefaultProperties(new HashMap<String, Object>() { {
                    put("spring.jms.listener.auto-startup", Boolean.FALSE);
                }});
                break;
            }
//                default: report
        }

        if (app == null) {
            throw new IllegalArgumentException("Unknown command line argument: '" + args[0]);
        }

        app.run(args);
    }

}
