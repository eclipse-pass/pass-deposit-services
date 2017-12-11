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

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsSubmissionCLI {

    /**
     * Arguments - just the property file containing the submission elements
     */
    @Argument(required = true, index = 0, metaVar = "[properties file]", usage = "properties file for the submission")
    public static File propertiesFile = null;

    @Argument(required = true, index = 1, metaVar = "[FTP configuration key]", usage = "key used to look up the FTP server and credentials for performing submissions")
    public static String transportKey = null;

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

    public NihmsSubmissionCLI(){

    }

    public static void main(String[] args) {

        final NihmsSubmissionCLI application = new NihmsSubmissionCLI();
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
            NihmsSubmissionApp app = new NihmsSubmissionApp(propertiesFile, transportKey);
            app.run();
            System.exit((0));
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

}
