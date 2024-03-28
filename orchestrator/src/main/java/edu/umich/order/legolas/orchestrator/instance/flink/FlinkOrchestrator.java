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
import edu.umich.order.legolas.orchestrator.instance.flink.workload.BatchAndStreamingWorkload;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.ServerNode;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class FlinkOrchestrator extends Orchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkOrchestrator.class);

    protected JobManagerServerNode jm = null;
    protected final Map<Integer, TaskManagerServerNode> tms = new TreeMap<>();
    protected final List<Integer> nodeIds = new ArrayList();

    private FlinkOrchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        super(megaServer, properties);
    }

    @Override
    public synchronized void startEnsemble(long endTime) throws Exception {
        LOG.info("Cleaning the Flink data in ZooKeeper and Kafka and checkpoint");
        final ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", workspacePathName + "/clean.sh");
        pb.redirectErrorStream(true);
        pb.start().waitFor();
        megaServer.setReady();
        final JobManagerServerNode jm = new JobManagerServerNode(megaServer, this, trialId, 1, 1);
        jm.preparePersistentData();
        jm.start();
        this.jm = jm;
        nodeIds.add(1);
        for (int id = 2; id <= 3; id++) {
            final TaskManagerServerNode tm = new TaskManagerServerNode(megaServer, this, trialId, id, id);
            tm.preparePersistentData();
            tm.start();
            tms.put(id, tm);
            nodeIds.add(id);
        }
        Thread.sleep(this.warmupMillis);
    }

    @Override
    public Collection<Integer> getServerNodeIds() {
        return nodeIds;
    }

    @Override
    public ServerNode getServerNodeById(int id) {
        if (id == 1)
            return jm;
        return tms.get(id);
    }

    @Override
    public synchronized void close() throws Exception {
        if (jm != null) {
            jm.shutdown();
        }
        for (int id = 2; id <= 3; id++) {
            final TaskManagerServerNode tm = tms.get(id);
            if (tm != null) {
                tm.shutdown();
            }
        }
    }

    public static FlinkOrchestrator build(final MegaServer megaServer, final Properties properties)
            throws Exception {
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("invalid workload name");
        }
        switch (workloadName) {
            case "BatchAndStreaming" : {
                final int words = Integer.parseInt(properties.getProperty("words"));
                final int numbers = Integer.parseInt(properties.getProperty("numbers"));
                final String kafkaHosts = properties.getProperty("kafkaHosts");
                final String paragraph = properties.getProperty("paragraph");
                final FlinkOrchestrator orch = new FlinkOrchestrator(megaServer, properties);
                orch.workloads.add(new BatchAndStreamingWorkload(orch, kafkaHosts, paragraph, words, numbers));
                return orch;
            }
            default: throw new Exception("unknown workload");
        }
    }
}
