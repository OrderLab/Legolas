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

package edu.umich.order.legolas.orchestrator.instance.kafka;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.instance.kafka.workload.ConsumeProduceWorkload;
import edu.umich.order.legolas.orchestrator.instance.kafka.workload.CreateWorkload;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.ServerNode;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class KafkaOrchestrator extends Orchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaOrchestrator.class);

    protected final Map<Integer, KafkaBrokerServerNode> brokers = new TreeMap<>();

    private KafkaOrchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        super(megaServer, properties);
    }

    @Override
    public synchronized void startEnsemble(long endTime) throws Exception {
        LOG.info("Cleaning the Kafka data in ZooKeeper");
        final ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", workspacePathName + "/clean.sh");
        pb.redirectErrorStream(true);
        pb.start().waitFor();
        megaServer.setReady();
        for (int id = 1; id <= 3; id++) {
            final KafkaBrokerServerNode broker = new KafkaBrokerServerNode(megaServer, this, trialId, id, id);
            broker.preparePersistentData();
            broker.start();
            brokers.put(id, broker);
        }
    }

    @Override
    public Collection<Integer> getServerNodeIds() {
        return brokers.keySet();
    }

    @Override
    public ServerNode getServerNodeById(int id) {
        return brokers.get(id);
    }

    @Override
    public synchronized void close() throws Exception {
        for (int id = 1; id <= 3; id++) {
            final KafkaBrokerServerNode broker = brokers.get(id);
            if (broker != null) {
                broker.shutdown();
                // persistent data is removed in the clean.sh script
            }
        }
    }

    public static KafkaOrchestrator build(final MegaServer megaServer, final Properties properties)
            throws Exception {
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("invalid workload name");
        }
        final Map<Integer, String> hosts = new TreeMap<>();
        for (int i = 1; i <= 3; i++) {
            hosts.put(i, properties.getProperty("listeners." + i));
        }
        switch (workloadName) {
            case "CreateConsumeProduce": {
                int creatorRequestNumber = Integer.parseInt(
                        properties.getProperty("creatorRequestNumber"));
                int groupNumber = Integer.parseInt(properties.getProperty("groupNumber"));
                int requestNumber = Integer.parseInt(properties.getProperty("requestNumber"));
                final KafkaOrchestrator orch = new KafkaOrchestrator(megaServer, properties);
                orch.workloads.add(new CreateWorkload(orch, hosts, creatorRequestNumber,
                        groupNumber, requestNumber));
                orch.workloads.add(new ConsumeProduceWorkload(orch, hosts, creatorRequestNumber,
                        groupNumber, requestNumber));
                return orch;
            }
            default: throw new Exception("unknown workload");
        }
    }
}
