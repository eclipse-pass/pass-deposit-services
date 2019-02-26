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
package org.dataconservancy.pass.deposit.messaging.support.quartz;

import org.dataconservancy.pass.deposit.messaging.service.SubmissionStatusUpdater;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
@DisallowConcurrentExecution
public class SubmissionStatusUpdaterJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusUpdaterJob.class);

    private SubmissionStatusUpdater updater;

    @Autowired
    public SubmissionStatusUpdaterJob(SubmissionStatusUpdater updater) {
        this.updater = updater;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        LOG.trace("Starting {}", this.getClass().getSimpleName());
        updater.doUpdate();
        LOG.trace("Finished {}", this.getClass().getSimpleName());
    }

}
