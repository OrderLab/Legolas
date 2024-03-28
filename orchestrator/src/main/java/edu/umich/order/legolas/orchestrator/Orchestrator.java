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

import edu.umich.order.legolas.orchestrator.instance.cassandra.CassandraOrchestrator;
import edu.umich.order.legolas.orchestrator.instance.flink.FlinkOrchestrator;
import edu.umich.order.legolas.orchestrator.instance.hadoop.HadoopOrchestrator;
import edu.umich.order.legolas.orchestrator.instance.hbase.HBaseOrchestrator;
import edu.umich.order.legolas.orchestrator.instance.kafka.KafkaOrchestrator;
import edu.umich.order.legolas.orchestrator.system.ServerNode;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import edu.umich.order.legolas.orchestrator.instance.zookeeper.ZooKeeperOrchestrator;

import java.io.File;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public abstract class Orchestrator implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

    protected final MegaServer megaServer;

    protected int progress = 0;
    protected final ArrayList<Workload> workloads = new ArrayList<>();

    private int clientCounter = 0;

    public final int createClientId() {
        return clientCounter++;
    }

    public final boolean hasNextWorkload() {
        return progress < workloads.size();
    }

    public final boolean runNextWorkload(final long endTime) throws Exception {
        final Workload workload = workloads.get(progress);
        megaServer.setWorkload(workload);
        workload.run(endTime);
        progress++;
        return workload.isFinished();
    }

    public void reportResult() {
        for (int i = 0; i < progress; i++) {
            workloads.get(i).reportResult(i);
        }
    }

    public final int trialId;
    public final String workspacePathName;
    public final String targetSystemPathName;
    public final String targetSystemVersion;
    public final int targetSystemMajorVersion;
    public final int targetSystemMinorVersion;
    public final int targetSystemPatchVersion;
    public final String initDataPathName;
    public final int warmupMillis;
    public final boolean useLogMonitor;
    public final boolean waitActiveEnsemble;

    public Orchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        this.megaServer = megaServer;
        trialId = megaServer.getTrialId();
        workspacePathName = properties.getProperty("workspacePathName");
        if (workspacePathName == null || !new File(workspacePathName).exists()) {
            throw new Exception("invalid workspacePathName");
        }
        targetSystemPathName = properties.getProperty("targetSystemPathName");
        if (targetSystemPathName == null || !new File(targetSystemPathName).exists()) {
            throw new Exception("invalid targetSystemPathName");
        }
        targetSystemVersion = properties.getProperty("version");
        if (targetSystemVersion == null || targetSystemVersion.isEmpty()) {
            throw new Exception("empty version for target system");
        }
        String [] versionParts = targetSystemVersion.split("\\.");
        targetSystemMajorVersion = Integer.parseInt(versionParts[0]);
        if (versionParts.length > 1) {
            targetSystemMinorVersion = Integer.parseInt(versionParts[1]);
            if (versionParts.length > 2)
                targetSystemPatchVersion = Integer.parseInt(versionParts[2]);
            else
                targetSystemPatchVersion = 0;
        } else {
            targetSystemMinorVersion = 0;
            targetSystemPatchVersion = 0;
        }

        initDataPathName = properties.getProperty("initDataPathName");
        if (initDataPathName == null || !new File(initDataPathName).exists()) {
            throw new Exception("invalid initDataPathName");
        }
        warmupMillis = Integer.parseInt(properties.getProperty("warmupMillis"));
        useLogMonitor = Boolean.parseBoolean(
            properties.getProperty("useLogMonitor", "true"));
        waitActiveEnsemble = Boolean.parseBoolean(
            properties.getProperty("waitActiveEnsemble", "false"));
    }

    public abstract void startEnsemble(long endTime) throws Exception;
    public abstract Collection<Integer> getServerNodeIds();
    public abstract ServerNode getServerNodeById(int id);

    public boolean waitForServersActive(Collection<Integer> waitIds, long endTime) {
        final long now = System.currentTimeMillis();
        if (now >= endTime) {
            LOG.warn("Exceeding time limit in starting ensemble");
            // already exceeded the timeout
            return false;
        }
        final long waitTime = endTime - now;
        final CountDownLatch allActive = new CountDownLatch(1);
        if (waitIds == null) {
            waitIds = getServerNodeIds(); // wait for all servers if none is specified
            if (waitIds == null)
                return false;
        }
        final boolean active[] = new boolean[waitIds.size()];
        final int ids[] = new int[waitIds.size()];
        int index = 0;
        for (int id : waitIds) {
            active[index] = false;
            ids[index] = id;
            index++;
        }
        final Thread ensembleWaiter = new Thread(() -> {
            int cnt = active.length;
            while (System.currentTimeMillis() < endTime && cnt > 0) {
                int i = 0;
                for (int id : ids) {
                    if (!active[i]) {
                        final ServerNode node = getServerNodeById(id);
                        if (node == null) {
                            active[i] = true; // forget about this node
                            continue;
                        }
                        active[i] = node.waitActive(10);
                        if (active[i]) {
                            LOG.info("Node {} is now active", id);
                            cnt--;
                        }
                    }
                    i++;
                }
            }
            if (cnt > 0) {
                LOG.warn("Not all nodes are active");
            }
            allActive.countDown();
        });
        ensembleWaiter.start();
        try {
            allActive.await(waitTime, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            LOG.warn("Interrupted waiting for ensemble to become active", e);
            return false;
        }
    }





    public final String getTrialDir() {
        return workspacePathName + "/trials/" + trialId;
    }

    /*
     * select target system
     */
    static Orchestrator buildOrchestrator(final MegaServer megaServer, final Properties properties)
            throws Exception {
        // TODO: refactor this line
        final String targetSystem = properties.getProperty("targetSystem");
        if (targetSystem == null) {
            throw new Exception("target system not found");
        }
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("no workload configuration");
        }
        switch (targetSystem) {
            case "hadoop" : return HadoopOrchestrator.build(megaServer, properties);
            case "cassandra": return CassandraOrchestrator.build(megaServer, properties);
            case "zookeeper": return ZooKeeperOrchestrator.build(megaServer, properties);
            case "hbase": return HBaseOrchestrator.build(megaServer, properties);
            case "kafka" : return KafkaOrchestrator.build(megaServer, properties);
            case "flink" : return FlinkOrchestrator.build(megaServer, properties);
            default: throw new Exception("Unrecognized target system " + targetSystem);
        }
    }
}
