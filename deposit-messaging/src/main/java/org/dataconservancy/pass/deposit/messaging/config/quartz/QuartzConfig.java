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

package org.dataconservancy.pass.deposit.messaging.config.quartz;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.deposit.util.loggers.Loggers.WORKERS_LOGGER;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.support.quartz.DepositUpdaterJob;
import org.dataconservancy.pass.deposit.messaging.support.quartz.SubmissionStatusUpdaterJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(name = "pass.deposit.jobs.disabled", havingValue = "false", matchIfMissing = true)
public class QuartzConfig {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzConfig.class);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final String DEPOSIT_JOB_GROUP = "depositServiceJobs";

    private final String DEPOSIT_UPDATER_JOB = "depositUpdater";

    private final String SUBMISSION_STATUS_JOB_GROUP = "submissionStatusJobs";

    private final String SUBMISSION_UPDATER_JOB = "submissionStatusUpdater";

    @Value("${pass.deposit.jobs.default-interval-ms}")
    private long defaultJobInterval;

    @Value("${pass.deposit.jobs.concurrency}")
    private int jobWorkerConcurrency;

    /**
     * This property is set to <em>true</em> if we are to disable the Quartz scheduler.  If the property is missing, it
     * will default to <em>false</em> (i.e. enable the scheduler).
     */
    @Value("${pass.deposit.jobs.disabled}")
    private boolean disabled;

    @Bean
    public JobDetail depositUpdaterJobDetail() {
        return JobBuilder.newJob(DepositUpdaterJob.class)
                         .withIdentity(DEPOSIT_UPDATER_JOB, DEPOSIT_JOB_GROUP)
                         .storeDurably()
                         .build();
    }

    @Bean
    public SimpleTrigger depositUpdaterTrigger(JobDetail depositUpdaterJobDetail) {
        return TriggerBuilder.newTrigger()
                             .forJob(DEPOSIT_UPDATER_JOB, DEPOSIT_JOB_GROUP)
                             .withSchedule(simpleSchedule()
                                               .withIntervalInMilliseconds(defaultJobInterval)
                                               .repeatForever())
                             .forJob(depositUpdaterJobDetail)
                             .build();
    }

    @Bean
    public JobDetail submissionStatusUpdaterJobDetail() {
        return JobBuilder.newJob(SubmissionStatusUpdaterJob.class)
                         .withIdentity(SUBMISSION_UPDATER_JOB, SUBMISSION_STATUS_JOB_GROUP)
                         .storeDurably()
                         .build();
    }

    @Bean
    public SimpleTrigger submissionStatusUpdateTrigger(JobDetail submissionStatusUpdaterJobDetail) {
        return TriggerBuilder.newTrigger()
                             .forJob(SUBMISSION_UPDATER_JOB, SUBMISSION_STATUS_JOB_GROUP)
                             .withSchedule(simpleSchedule()
                                               .withIntervalInMilliseconds(defaultJobInterval)
                                               .repeatForever())
                             .forJob(submissionStatusUpdaterJobDetail)
                             .build();
    }

    @Bean
    public ThreadPoolTaskExecutor quartzTaskExecutor(DepositServiceErrorHandler errorHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(jobWorkerConcurrency);
        int capacity = 10;
        executor.setQueueCapacity(capacity);
        executor.setRejectedExecutionHandler((rejectedTask, exe) -> {
            String msg = String.format("Task %s@%s rejected by the Quartz-Worker thread pool task executor.",
                                       rejectedTask.getClass().getSimpleName(),
                                       toHexString(identityHashCode(rejectedTask)));
            WORKERS_LOGGER.error(msg);
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("Quartz-Worker-");
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("Quartz-Worker-" + THREAD_COUNTER.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, throwable) -> errorHandler.handleError(throwable));
            return t;
        };
        executor.setThreadFactory(tf);

        WORKERS_LOGGER.debug("Created Quartz worker thread pool with maxPoolSize: {} and capacity {}",
                             jobWorkerConcurrency, capacity);

        return executor;
    }

    @Bean
    public SchedulerFactoryBeanCustomizer quartzCustomizer(ThreadPoolTaskExecutor quartzTaskExecutor) {
        return (factoryBean) -> {
            factoryBean.setTaskExecutor(quartzTaskExecutor);
            factoryBean.setAutoStartup(!disabled);
            if (disabled) {
                LOG.info("Quartz SchedulerFactoryBean autoStartup is disabled; Quartz jobs will not be run.  " +
                         "Check the value of 'pass.deposit.jobs.disabled' property or ENV variable.");
            }
        };
    }

}
