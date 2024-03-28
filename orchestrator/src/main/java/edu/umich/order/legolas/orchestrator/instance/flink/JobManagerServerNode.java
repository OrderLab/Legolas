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
package edu.umich.order.legolas.orchestrator.instance.flink;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.LogMonitor;
import edu.umich.order.legolas.orchestrator.system.ServerNode;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class JobManagerServerNode extends ServerNode {
    private static final Logger LOG = LoggerFactory.getLogger(JobManagerServerNode.class);

    protected static String logFileName = null;
    protected static String pidFileName = null;

    public JobManagerServerNode(final MegaServer megaServer, final Orchestrator orchestrator,
            final int trialId, final int serverId, final int instanceId) {
        super(megaServer, orchestrator, trialId, serverId, instanceId);
    }

    private final CountDownLatch latch = new CountDownLatch(1);

    protected final String getLogFileName() {
        if (logFileName == null) {
            String userName = System.getProperty("user.name");
            String hostName = "";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                hostName = "unknown";
            }
            logFileName = "flink-" + userName + "-standalonesession-0-" + hostName + ".log";
        }
        return logFileName;
    }

    private String getPidFileName() {
        if (pidFileName == null) {
            String userName = System.getProperty("user.name");
            pidFileName = "flink-" + userName + "-standalonesession.pid";
        }
        return pidFileName;
    }

    @Override
    public final synchronized void start() throws Exception {
        new File(getLogDirPathName()).mkdirs();
        final ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", orchestrator.workspacePathName + "/server.sh",
                String.valueOf(trialId), String.valueOf(serverId), String.valueOf(instanceId));
        pb.redirectErrorStream(true);
        megaServer.prepareNodeStart(serverId);
        pb.start();
        logMonitor = getLogMonitor();
        logMonitor.start();
        latch.await(5, TimeUnit.SECONDS);
        pid = megaServer.getRecentPid();
        if (pid == -1) {
            throw new Exception("Fail to receive PID from ServerNode "
                + serverId + ". This may happen because the target is not instrumented..");
        }
        LOG.info("Server node {} of instance id {} started with pid {}", serverId, instanceId, pid);
        started = true;
        setStatus("started");
        megaServer.setStart(serverId);
    }

    @Override
    public final synchronized void shutdown() throws Exception {
        final String pidFilePath = getLogDirPathName() + "/" + getPidFileName();
        try (final Scanner scanner = new Scanner(new File(pidFilePath))) {
            // Flink job manager and task manager has multiple PIDs in 
            // pidfile, need to kill all of them
            int pid = -1;
            while (scanner.hasNextInt()) {
                pid = scanner.nextInt();
                if (pid != -1) {
                    try {
                        Runtime.getRuntime().exec("kill -9 " + pid).waitFor();
                    } catch (final Exception e) {
                        throw new Exception("possibly fail to kill the ServerNode");
                    }
                    LOG.info("JobManager with PID {} terminated", pid);
                }
            }
        } catch (NoSuchElementException e) {
          // ignore
        } catch (Exception e) {
            LOG.warn("Encounter error when reading the pid file", e);
        }
        super.shutdown();
    }

    protected LogMonitor getLogMonitor() throws Exception {
        return new LogMonitor(this) {
            @Override
            protected void handle(String line) {
                if (line.contains("----------")) {
                    latch.countDown();
                }
            }
        };
    }
}
