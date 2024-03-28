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
package edu.umich.order.legolas.orchestrator.instance.hbase;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.LogMonitor;
import edu.umich.order.legolas.orchestrator.system.ServerNode;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class HMasterServerNode extends ServerNode {
    private static final Logger LOG = LoggerFactory.getLogger(HMasterServerNode.class);

    private final String logFileName;

    public HMasterServerNode(final MegaServer megaServer, final Orchestrator orchestrator,
            final int trialId, final int serverId, final int instanceId) {
        super(megaServer, orchestrator, trialId, serverId, instanceId);
        String userName = System.getProperty("user.name");
        String hostName = "";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "unknown";
        }
        logFileName = "hbase-" + userName +"-" + serverId + "-master-" + hostName + ".log";
    }

    private final CountDownLatch latch = new CountDownLatch(1);

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
        LOG.info("Server node {} of instance id {} started with pid {}", serverId, instanceId, pid);
        started = true;
        setStatus("started");
        megaServer.setStart(serverId);
    }

    @Override
    public final synchronized void shutdown() throws Exception {
        final String pidFileName = "hbase-" + System.getProperty("user.name") +"-" + serverId + "-master.pid";
        final String pidFilePath = getLogDirPathName() + "/" + pidFileName;
        int pid = -1;
        try (final Scanner scanner = new Scanner(new File(pidFilePath))) {
            pid = scanner.nextInt();
        } catch (final IOException ignored) {}
        if (pid != -1) {
            try {
                Runtime.getRuntime().exec("kill -9 " + pid).waitFor();
            } catch (final Exception e) {
                throw new Exception("possibly fail to kill the ServerNode");
            }
        }
        super.shutdown();
    }

    @Override
    protected final String getLogFileName() {
        return logFileName;
    }

    @Override
    protected LogMonitor getLogMonitor() throws Exception {
        return new LogMonitor(this) {
            @Override
            protected void handle(String line) {
                if (line.contains("STARTING service HMaster")) {
                    latch.countDown();
                }
            }
        };
    }
}
