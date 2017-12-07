/*
 * Copyright 2017 Johns Hopkins University
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
package org.dataconservancy.nihms.cli;

import org.dataconservancy.nihms.assembler.nihmsnative.NihmsAssembler;
import org.dataconservancy.nihms.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.nihms.submission.SubmissionEngine;
import org.dataconservancy.nihms.transport.ftp.DefaultFtpClientFactory;
import org.dataconservancy.nihms.transport.ftp.FtpTransport;

import java.io.File;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsSubmissionApp {

    private File propertiesFile;

    NihmsSubmissionApp(File propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    void run() throws NihmsCliException {
        try {
            NihmsAssembler assembler = new NihmsAssembler();
            FilesystemModelBuilder builder = new FilesystemModelBuilder();
            DefaultFtpClientFactory factory = new DefaultFtpClientFactory();
            FtpTransport transport = new FtpTransport(factory);
            SubmissionEngine engine = new SubmissionEngine(builder,assembler,transport);
            engine.submit(propertiesFile.getCanonicalPath());
        } catch (Exception e) {
            throw new NihmsCliException(e.getMessage(), e);
        }
    }

}

