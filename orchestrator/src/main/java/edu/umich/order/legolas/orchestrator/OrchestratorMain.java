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
package edu.umich.order.legolas.orchestrator;

import edu.umich.order.legolas.orchestrator.server.MegaServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Entry of the orchestrator server
 */
public class OrchestratorMain {
    private static final Logger LOG = LoggerFactory.getLogger(OrchestratorMain.class);

    public static void main(String[] args) {
        String configFile = args[0];
        final Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(Paths.get(configFile)));
        } catch (IOException e) {
            LOG.error("Failed to load configuration file {}", configFile);
            System.exit(1);
        }
        LOG.info("Bootstrapping Legolas orchestrator server");
        boolean stopOnFail = Boolean.parseBoolean(properties.getProperty("stopOnFail", "false"));
        // 0 means no retry, -1 means endless retries...
        int failTrialRetries = Integer.parseInt(properties.getProperty("failTrialRetries", "3"));
        // if there are too many retries in total, we may want to terminate the experiment and investigate
        int maxTotalRetries = Integer.parseInt(properties.getProperty("maxTotalRetries", "30"));
        int totalRetries = 0;
        int trialTimeout = Integer.parseInt(
                properties.getProperty("trialTimeout", "60000"));
        try (final MegaServer megaServer = new MegaServer(properties)) {
            megaServer.start();
            boolean stop = false;
            while (!stop && megaServer.hasNextTrial()) {
                int retries = 0;
                while (true) {
                    megaServer.setupNewTrial(retries == 0); // only increment id for the first try
                    int trialId = megaServer.getTrialId();
                    String retryStr;
                    if (retries == 0) {
                        MDC.put("trialId", Integer.toString(trialId));
                        retryStr = "";
                    } else {
                        retryStr = " - retry " + retries + ", experiment retry " + totalRetries;
                    }
                    LOG.info("starting trial {}{}", trialId, retryStr);
                    final long endTime = System.currentTimeMillis() + trialTimeout;
                    megaServer.initStats();
                    try (final Orchestrator orch = Orchestrator.buildOrchestrator(megaServer,
                            properties)) {
                        orch.startEnsemble(endTime);
                        while (orch.hasNextWorkload() && System.currentTimeMillis() < endTime) {
                            if (!orch.runNextWorkload(endTime)) {
                                // not finish this workload
                                break;
                            }
                        }
                        orch.reportResult();
                    } catch (Exception e) {
                        LOG.error("Exception start orchestrator server in trial {}", trialId, e);
                        megaServer.onTrialStopped(); // still call on trial stopped to clean up file resources
                        if (stopOnFail) {
                            stop = true;
                            break;
                        }
                        retries++;
                        if (failTrialRetries == 0 || (failTrialRetries > 0 && retries > failTrialRetries)) {
                            LOG.error("failed trial {} after {} retries", trialId, retries - 1);
                            break;
                        }
                        totalRetries++;
                        if (maxTotalRetries == 0 || (maxTotalRetries > 0 && totalRetries > maxTotalRetries)) {
                            LOG.error("failed trial {} after {} retries", trialId, retries - 1);
                            LOG.error("Too many ({}) experiment retries. Stopping experiment..",
                                    totalRetries - 1);
                            stop = true;
                            break;
                        }
                        continue;
                    }
                    megaServer.dumpStats(properties);
                    megaServer.onTrialStopped();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                    }
                    break;
                }
            }
        } catch (RemoteException ex) {
            LOG.error("Failed to initialize servers", ex);
        }
        LOG.info("Legolas orchestrator server ends");
    }
}
