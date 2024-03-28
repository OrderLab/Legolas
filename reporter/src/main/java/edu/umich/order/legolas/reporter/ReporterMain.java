/*
 *  @author Haoze Wu <haoze@jhu.edu>
 *
 *  The Legolas Project
 *
 *  Copyright (c) 2024, University of Michigan, EECS, OrderLab.
 *      All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.umich.order.legolas.reporter;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReporterMain {
    private static final Logger LOG = LoggerFactory.getLogger(ReporterMain.class);

    public static void main(final String[] args) {
        final CommandLine cmd = parse(args);

        final String experimentDir = cmd.getOptionValue("experiment");
        final String trialsDir = experimentDir + "/trials";

        File trialsDirFile = new File(trialsDir);
        if (!trialsDirFile.isDirectory()) {
            System.err.println("Trials directory " + trialsDir + " does not exist");
            System.exit(1);
        }

        int trialNumber = -1;
        if (cmd.hasOption("number")) {
            trialNumber = Integer.parseInt(cmd.getOptionValue("number"));
        }

        int startTrialId = 0;
        int endTrialId = trialNumber - 1;
        if (cmd.hasOption("stid")) {
            startTrialId = Integer.parseInt(cmd.getOptionValue("stid"));
        }
        if (cmd.hasOption("etid")) {
            endTrialId = Integer.parseInt(cmd.getOptionValue("etid"));
        }
        if (startTrialId >= 0 && endTrialId >= 0) {
            if (trialNumber <= 0) {
                trialNumber = endTrialId - startTrialId + 1;
            } else if ((endTrialId - startTrialId + 1) != trialNumber) {
                System.err.println("Mismatching start, end trial Id and trial number");
                System.exit(1);
            }
        }

        Experiment experiment = new Experiment(experimentDir, trialsDir,
                trialNumber, startTrialId, endTrialId);

        try {
            if (cmd.hasOption("bugs")) {
                experiment.loadBugs(cmd.getOptionValue("bugs"));
            }

            experiment.parseSpec(cmd.getOptionValue("specification"),
                    cmd.hasOption("benign"), cmd.hasOption("falsepositive"));

            String logFilePath = experimentDir + "/" + (cmd.hasOption("logfile") ?
                    cmd.getOptionValue("logfile") : "result.txt");
            experiment.parseExperimentLog(logFilePath);
            experiment.parseTrials();
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
        experiment.matchBugs();

        final String outputDir = cmd.getOptionValue("output");
        if (outputDir != null) {
            final File dir = new File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        final Result result = new Result(experiment.spec);
        for (int i = experiment.startTrialId; i <= experiment.endTrialId; i++) {
            if (i >= experiment.trials.size()) {
                LOG.error("Trial id {} larger than total trials {}", i, experiment.trials.size());
                break;
            } else {
                result.add(experiment.trials.get(i));
            }
        }
        result.summarize();
        try {
            result.dump(outputDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        result.printStats();
        if (cmd.hasOption("enumerate")) {
            result.enumerateTrials(outputDir);
        }
    }


    private static CommandLine parse(final String[] args) {
        final Options options = new Options();

        options.addRequiredOption("s", "specification", true, "system and workload specification json");
        options.addRequiredOption("e", "experiment", true, "the directory of the experiment data");

        options.addOption(null,"enumerate", false, "parse and enumerate each trial's result");
        options.addOption("o", "output", true, "the directory for the reporter output");

        options.addOption(null, "logfile", true, "the experiment log file name (default result.txt)");
        options.addOption("n", "number", true, "number of trials");
        options.addOption(null, "stid", true, "start trial id to analyze");
        options.addOption(null, "etid", true, "end trial id to analyze");
        options.addOption("b", "bugs", true, "the json specification for the bugs");
        options.addOption("l", "benign", false, "enable benign level bug counting");
        options.addOption("f", "falsepositive", false, "filter and report false positives");
        options.addOption("h", "help", false, "print help message");

        HelpFormatter help = new HelpFormatter();
        CommandLine cmd = null;
        try {
            cmd = new DefaultParser().parse(options, args);
            if (cmd.hasOption("help")) {
                help.printHelp("ReporterMain", options);
                System.exit(0);
            }
            return cmd;
        } catch (org.apache.commons.cli.ParseException e) {
            help.printHelp("ReporterMain", options);
            throw new RuntimeException("Fail to parse the args:", e);
        }
    }
}
