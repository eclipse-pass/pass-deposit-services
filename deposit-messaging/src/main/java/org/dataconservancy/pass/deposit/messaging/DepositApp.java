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
import org.dataconservancy.pass.deposit.messaging.runner.ListenerRunner;
import org.dataconservancy.pass.deposit.messaging.runner.SubmittedUpdateRunner;
import org.dataconservancy.pass.deposit.messaging.runner.FailedDepositRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Properties;


/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootApplication
@Import({DepositConfig.class})
@ComponentScan("org.dataconservancy.pass")
@ComponentScan("org.dataconservancy.nihms")
public class DepositApp {

    private static final Logger LOG = LoggerFactory.getLogger(DepositApp.class);

    private static final String GIT_BUILD_VERSION_KEY = "git.build.version";

    private static final String GIT_COMMIT_HASH_KEY = "git.commit.id.abbrev";

    private static final String GIT_COMMIT_TIME_KEY = "git.commit.time";

    private static final String GIT_DIRTY_FLAG = "git.dirty";

    private static final String GIT_BRANCH = "git.branch";

    private String fcrepoUser;

    private String fcrepoPass;

    private String fcrepoBaseUrl;

    private static final String GIT_PROPERTIES_RESOURCE_PATH = "/git.properties";

    public static void main(String[] args) {

        URL gitPropertiesResource = DepositApp.class.getResource(GIT_PROPERTIES_RESOURCE_PATH);
        if (gitPropertiesResource == null) {
            LOG.info(">>>> Starting DepositServices (no Git commit information available)");
        } else {
            Properties gitProperties = new Properties();
            try {
                gitProperties.load(gitPropertiesResource.openStream());
                boolean isDirty = Boolean.valueOf(gitProperties.getProperty(GIT_DIRTY_FLAG));

                LOG.info(">>>> Starting DepositServices (version: {} branch: {} commit: {} commit date: {})",
                        gitProperties.get(GIT_BUILD_VERSION_KEY), gitProperties.get(GIT_BRANCH), gitProperties.get(GIT_COMMIT_HASH_KEY), gitProperties.getProperty(GIT_COMMIT_TIME_KEY));

                if (isDirty) {
                    LOG.warn(">>>> ** Deposit Services was compiled from a Git repository with uncommitted changes! **");
                }
            } catch (IOException e) {
                LOG.warn(">>>> Starting DepositService (" + GIT_PROPERTIES_RESOURCE_PATH + " could not be parsed: " + e.getMessage() + ")");
            }
        }

        if (args.length < 1 || args[0] == null) {
            throw new IllegalArgumentException("Requires at least one argument!");
        }

        SpringApplication app = null;

        switch (args[0]) {
            case "listen": {
                app = new SpringApplication(DepositApp.class, ListenerRunner.class);
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
            case "retry": {
                app = new SpringApplication(DepositApp.class, FailedDepositRunner.class);
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
