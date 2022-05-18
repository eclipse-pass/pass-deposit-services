/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.support;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Conditionally disables Quartz jobs prior to running an integration test, and unconditionally stops Quartz jobs after
 * running an integration test.
 * <p>
 * If the property {@code pass.deposit.jobs.disabled} is present in the Spring {@code Environment} with a value of
 * {@code true}, and the {@code SchedulerFactoryBean} is running, the scheduler will be stopped in {@link
 * #beforeTestClass(TestContext)}.
 * </p>
 * <p>
 * At the end of every integration test, the Quartz {@code SchedulerFactoryBean} is stopped to prevent jobs from one
 * Application Context affecting the state of tests executed in a different Application Context.
 * </p>
 * <h3>Background</h3>
 * Spring will construct, and cache, multiple Application Contexts when executing integration tests.  The problem is
 * that background threads, including Quartz jobs, launched by one Application Context live on after an integration
 * tests for that context complete.  Therefore, Quartz Jobs launched by Application Context "A" may act on the state of
 * Application Context "B", and potentially alter test outcome.
 * <p>
 * This test execution listener stops the Quartz jobs launched by its application at the {@link
 * #afterTestClass(TestContext) end} of each test.  If the Application Context is re-used (i.e. pulled from the cache
 * of Application Contexts and used for another test class), this test execution listener will start the Quartz jobs as
 * long as {@code pass.deposit.jobs.disabled} is not equal to {@code true}.
 * </p>
 * <p>
 * This {@code TestExecutionListener} is automatically configured on tests annotated with {@code SpringBootTest}
 * because it is automatically configured in {@code META-INF/spring.factories}; that is, it is considered a
 * <em>default</em> {@code TestExecutionListener}.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class QuartzTestExecutionListener extends AbstractTestExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzTestExecutionListener.class);

    private static final String DISABLED = "pass.deposit.jobs.disabled";

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        super.beforeTestClass(testContext);

        ApplicationContext appCtx = testContext.getApplicationContext();
        Environment env = appCtx.getEnvironment();
        SchedulerFactoryBean quartzScheduler = getSchedulerFactoryBean(appCtx);

        if (quartzScheduler == null) {
            return;
        }

        boolean isDisabled = Boolean.parseBoolean(env.getProperty(DISABLED));
        boolean isEnabled = !isDisabled;

        if (!quartzScheduler.isRunning()) {
            if (isEnabled) {
                LOG.debug("Starting {} ({}): the scheduler is not running, and it is enabled ({}={})",
                          classString(quartzScheduler), classString(appCtx), DISABLED, env.getProperty(DISABLED));
                quartzScheduler.start();
            } else {
                LOG.debug("{} ({}) taking no action: the scheduler is not running, and it is disabled ({}={})",
                          classString(quartzScheduler), classString(appCtx), DISABLED, env.getProperty(DISABLED));
            }

            return;
        }

        if (isDisabled) {
            LOG.debug("Stopping {} ({}): the scheduler is running, and it is disabled ({}={})",
                      classString(quartzScheduler), classString(appCtx), DISABLED, env.getProperty(DISABLED));
            quartzScheduler.stop();
        } else {
            LOG.debug("{} ({}) taking no action: the scheduler is running, and it is enabled ({}={})",
                      classString(quartzScheduler), classString(appCtx), DISABLED, env.getProperty(DISABLED));
        }
    }

    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        super.afterTestClass(testContext);

        ApplicationContext appCtx = testContext.getApplicationContext();
        SchedulerFactoryBean quartzScheduler = getSchedulerFactoryBean(appCtx);

        if (quartzScheduler == null) {
            return;
        }

        if (quartzScheduler.isRunning()) {
            LOG.debug("Stopping the {} ({}) so the running jobs do not act on future tests.",
                      classString(quartzScheduler), classString(appCtx));
            quartzScheduler.stop();
        }
    }

    private SchedulerFactoryBean getSchedulerFactoryBean(ApplicationContext appCtx) {
        SchedulerFactoryBean quartzScheduler = null;
        try {
            quartzScheduler = appCtx.getBean(SchedulerFactoryBean.class);
        } catch (BeansException e) {
            LOG.debug("{} will not run: {}", classString(this), e.getMessage(), e);
        }

        return quartzScheduler;
    }

    private static String classString(Object o) {
        return className(o) + "@" + hash(o);
    }

    private static String className(Object o) {
        return o.getClass().getSimpleName();
    }

    private static String hash(Object o) {
        return toHexString(identityHashCode(o));
    }
}
