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
package edu.umich.order.legolas.orchestrator.system;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public abstract class ServerNode {
    private static final Logger LOG = LoggerFactory.getLogger(ServerNode.class);

    protected final MegaServer megaServer;
    protected final Orchestrator orchestrator;

    public final int trialId;
    public final int serverId;
    public final int instanceId;

    protected volatile String status = "halt";    // FIXME: change to enum
    protected volatile boolean started = false;
    protected volatile boolean injected = false;
    protected volatile boolean active = false;    // ready to accept client workloads

    protected volatile long pid = -1;
    protected volatile LogMonitor logMonitor = null;

    protected final CountDownLatch activeLatch = new CountDownLatch(1);

    public ServerNode(final MegaServer megaServer, final Orchestrator orchestrator,
            final int trialId, final int serverId, final int instanceId) {
        this.megaServer = megaServer;
        this.orchestrator = orchestrator;
        this.trialId = trialId;
        this.serverId = serverId;
        this.instanceId = instanceId;
    }

    protected abstract String getLogFileName();

    /*
     * TODO: keep track of multiple log files
     */
    public final String getLogFilePathName() {
        return getLogDirPathName() + "/" + getLogFileName();
    }

    public final String getLogDirPathName() {
        return orchestrator.getTrialDir() + "/logs-" + instanceId;
    }

    public String getPersistentDataPathName() {
        return orchestrator.workspacePathName + "/store-" + serverId;
    }

    public final String getConfDirPathName() {
        return orchestrator.workspacePathName + "/conf-" + serverId;
    }

    public String getInitPersistentDataPathName() {
        return orchestrator.initDataPathName + "/store-" + serverId;
    }

    public final synchronized void preparePersistentData() throws Exception {
        final File src = new File(getInitPersistentDataPathName());
        if (!src.exists()) {
            return;
        }
        final File dst = new File(getPersistentDataPathName());
        if (dst.exists()) {
            FileUtils.deleteDirectory(dst);
        }
        FileUtils.copyDirectory(src, dst);
    }

    protected abstract LogMonitor getLogMonitor() throws Exception;

    public final synchronized void purgePersistentData() throws IOException {
        final File file = new File(getPersistentDataPathName());
        if (file.exists()) {
            FileUtils.deleteDirectory(file);
        }
    }

    public synchronized void start() throws Exception {
        new File(getLogDirPathName()).mkdirs();
        final ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", orchestrator.workspacePathName + "/server.sh",
                String.valueOf(trialId), String.valueOf(serverId), String.valueOf(instanceId));
        pb.redirectErrorStream(true);
        megaServer.prepareNodeStart(serverId);
        pb.start();
        pid = megaServer.getRecentPid();
        if (pid == -1) {
            throw new Exception("Fail to receive PID from ServerNode "
                + serverId + ". This may happen because the target is not instrumented..");
        }
        LOG.info("Server node {} of instance id {} started with pid {}", serverId, instanceId, pid);
        started = true;
        setStatus("started");
        if (orchestrator.useLogMonitor) {
            logMonitor = getLogMonitor();
            logMonitor.start();
        }
        megaServer.setStart(serverId);
    }

    public final synchronized boolean isAlive() {
        if (!started) {
            return false;
        }
        return true; // FIXME: should check pid in system processes, by kill -0 PID?
    }

    public final synchronized boolean isInjected() {
        return injected;
    }

    public final synchronized boolean isActive() {
        return active;
    }

    public final boolean waitActive(long timeoutMS) {
        boolean ret;
        try {
            if (timeoutMS > 0) {
                ret = activeLatch.await(timeoutMS, TimeUnit.MILLISECONDS);
            }
            else {
                activeLatch.await();
                ret = true;
            }
        } catch (InterruptedException e) {
            ret = false;
        }
        return ret;
    }

    public final synchronized void setActive(boolean yes) {
        active = yes;
        if (yes) {
            activeLatch.countDown();
        }
    }

    public final synchronized String getStatus() {
        return status;
    }

    public final void setInjected() {
        if (!injected) {
            LOG.info("ServerNode {} instance {} is now injected",
                serverId, instanceId);
        }
        injected = true;
    }

    public final synchronized void setStatus(final String status) {
        if (isAlive()) {
            this.status = status;
        }
    }

    public synchronized void shutdown() throws Exception {
        // For HBase HMaster, this pid might not be the correct one,
        // but in that case it will be handled by the shutdown() of HMasterServerNode
        try {
            Runtime.getRuntime().exec("kill -9 " + pid).waitFor();
        } catch (final Exception e) {
            throw new Exception("possibly fail to kill the ServerNode");
        }
        megaServer.setShutdown(serverId);
        setStatus("halt");
        LOG.info("Server node {} of instance id {} terminated", serverId, instanceId);
        started = false;
        if (orchestrator.useLogMonitor) {
            logMonitor.shutdown();
            logMonitor.join();
        }
    }
}
