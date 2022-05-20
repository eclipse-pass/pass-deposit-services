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

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.runner.FailedDepositRunner;
import org.dataconservancy.pass.deposit.messaging.runner.ListenerRunner;
import org.dataconservancy.pass.deposit.messaging.runner.SubmittedUpdateRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;


/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootApplication
@Import(DepositConfig.class)
@ComponentScan("org.dataconservancy.pass")
public class DepositApp {

    private static final Logger LOG = LoggerFactory.getLogger(DepositApp.class);

    private static final String GIT_BUILD_VERSION_KEY = "git.build.version";

    private static final String GIT_BUILD_TIME = "git.build.time";

    private static final String GIT_COMMIT_HASH_KEY = "git.commit.id.abbrev";

    private static final String GIT_COMMIT_TIME_KEY = "git.commit.time";

    private static final String GIT_DIRTY_FLAG = "git.dirty";

    private static final String GIT_BRANCH = "git.branch";

    private String fcrepoUser;

    private String fcrepoPass;

    private String fcrepoBaseUrl;

    private static final String GIT_PROPERTIES_RESOURCE_PATH = "/deposit-services-git.properties";

    public static void main(String[] args) {

        URL gitPropertiesResource = DepositApp.class.getResource(GIT_PROPERTIES_RESOURCE_PATH);
        if (gitPropertiesResource == null) {
            LOG.info("Starting DepositServices (no Git commit information available)");
        } else {
            Properties gitProperties = new Properties();
            try {
                gitProperties.load(gitPropertiesResource.openStream());
                boolean isDirty = Boolean.valueOf(gitProperties.getProperty(GIT_DIRTY_FLAG));

                LOG.info("Starting DepositServices (version: {} branch: {} commit: {} commit date: {} build date: {})",
                         gitProperties.get(GIT_BUILD_VERSION_KEY),
                         gitProperties.get(GIT_BRANCH),
                         gitProperties.get(GIT_COMMIT_HASH_KEY),
                         gitProperties.get(GIT_COMMIT_TIME_KEY),
                         gitProperties.get(GIT_BUILD_TIME));

                if (isDirty) {
                    LOG.warn("** Deposit Services was compiled from a Git repository with uncommitted changes! **");
                }
            } catch (IOException e) {
                LOG.warn(
                    "Starting DepositService (" + GIT_PROPERTIES_RESOURCE_PATH + " could not be parsed: " +
                            e.getMessage() + ")");
            }
        }

        if (args.length < 1 || args[0] == null) {
            throw new IllegalArgumentException("Requires at least one argument!");
        }

        SpringApplication app = null;

        switch (args[0]) {
            case "listen": {
                app = new SpringApplicationBuilder(DepositApp.class, ListenerRunner.class)
                    .banner(new DepositAppBanner())
                    .build();
                break;
            }
            case "refresh": {
                app = new SpringApplicationBuilder(DepositApp.class, SubmittedUpdateRunner.class)
                    .banner(new DepositAppBanner())
                    .build();
                // TODO figure out elegant way to exclude JMS-related beans like SubmissionProcessor from being spun up
                app.setDefaultProperties(new HashMap<String, Object>() {
                    {
                        put("spring.jms.listener.auto-startup", Boolean.FALSE);
                    }
                });
                break;
            }
            case "retry": {
                app = new SpringApplicationBuilder(DepositApp.class, FailedDepositRunner.class)
                    .banner(new DepositAppBanner())
                    .build();
                app.setDefaultProperties(new HashMap<String, Object>() {
                    {
                        put("spring.jms.listener.auto-startup", Boolean.FALSE);
                    }
                });
                break;
            }
            default:
        }

        if (app == null) {
            throw new IllegalArgumentException("Unknown command line argument: '" + args[0]);
        }

        app.run(args);
    }

    // When the Spring application starts and calls the Banner implementation, we take that opportunity
    // to print the contents of the environment variables and resolved Spring properties using the
    // provided Environment.
    private static class DepositAppBanner implements Banner {

        @Override
        public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
            LOG.info("Environment:");

            // Sort the variables by name and find the longest name
            Map<String, String> vars = System.getenv();
            List<String> keys = new ArrayList<>();
            int maxLen = 0;
            for (String varName : vars.keySet()) {
                keys.add(varName);
                if (varName.length() > maxLen) {
                    maxLen = varName.length();
                }
            }
            Collections.sort(keys);

            // Print the variable names and values
            for (String varName : keys) {
                String nameString = StringUtils.rightPad(varName, maxLen);
                LOG.info("   {} '{}'", nameString, vars.get(varName));
            }

            // Print out any resolved Spring property placeholders
            boolean firstOne = true;
            for (String varName : keys) {
                String origValue = vars.get(varName);
                String resolvedValue;
                String errorMsg = "";
                try {
                    resolvedValue = environment.resolvePlaceholders(origValue);
                } catch (Exception e) {
                    resolvedValue = origValue;
                    errorMsg = "(could not resolve property: " + e.getMessage() + ")";
                }
                if (!resolvedValue.equals(origValue) || !errorMsg.isEmpty()) {
                    if (firstOne) {
                        LOG.info("Resolved Spring Environment property values:");
                        firstOne = false;
                    }
                    String nameString = StringUtils.rightPad(varName, maxLen);
                    LOG.info("   {} '{}' {}", nameString, resolvedValue, errorMsg);
                }
            }
        }
    }
}