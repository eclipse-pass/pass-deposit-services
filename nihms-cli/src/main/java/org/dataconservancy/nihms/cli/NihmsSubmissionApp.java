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

import java.io.File;
import java.io.IOException;

import org.dataconservancy.nihms.assembler.nihmsnative.NihmsAssembler;
import org.dataconservancy.nihms.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.nihms.submission.SubmissionEngine;
import org.dataconservancy.nihms.submission.SubmissionFailure;
import org.dataconservancy.nihms.transport.ftp.DefaultFtpClientFactory;
import org.dataconservancy.nihms.transport.ftp.FtpTransport;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsSubmissionApp {

    /**
     * Arguments - just the property file containing the submission elements
     */
    @Argument(required = true, index = 0, metaVar = "[properties file]", usage = "properties file for the submission")
    public File propertiesFile = null;

    /**
     *
     * General Options
     */

    /** Request for help/usage documentation */
    @Option(name = "-h", aliases = { "-help", "--help" }, usage = "print help message")
    public boolean help = false;

    /** Requests the current version number of the cli application. */
    @Option(name = "-v", aliases = { "-version", "--version" }, usage = "print version information")
    public boolean version = false;

    public NihmsSubmissionApp(){

    }
    public static void main(String[] args) {

        final NihmsSubmissionApp application = new NihmsSubmissionApp();
        CmdLineParser parser = new CmdLineParser(application);
        //parser.setUsageWidth(80);

        try {
            parser.parseArgument(args);
            /* Handle general options such as help, version */
            if (application.help) {
                parser.printUsage(System.err);
                System.err.println();
                System.exit(0);
            } else if (application.version) {
                System.err.println(NihmsCliException.class.getPackage()
                        .getImplementationVersion());
                System.exit(0);
            }

            /* Run the package generation application proper */
            application.run();

        } catch (CmdLineException e) {
            /**
             * This is an error in command line args, just print out usage data
             * and description of the error.
             */
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);
        } catch (NihmsCliException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private void run() throws NihmsCliException {
        try {
            NihmsAssembler assembler = new NihmsAssembler();
            FilesystemModelBuilder builder = new FilesystemModelBuilder();
            DefaultFtpClientFactory factory = new DefaultFtpClientFactory();
            FtpTransport transport = new FtpTransport(factory);
            SubmissionEngine engine = new SubmissionEngine(builder,assembler,transport);
            engine.submit(propertiesFile.getCanonicalPath());
            System.exit(0);
        } catch (IOException | SubmissionFailure ex) {
            ex.printStackTrace();
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }
}
