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
package edu.umich.order.legolas.orchestrator.instance.cassandra;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.instance.cassandra.workload.CreateWorkload;
import edu.umich.order.legolas.orchestrator.instance.cassandra.workload.ReadWriteWorkload;
import edu.umich.order.legolas.orchestrator.instance.cassandra.workload.RepairWorkload;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.ServerNode;

import java.util.Map;
import java.util.Properties;
import java.util.Collection;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class CassandraOrchestrator extends Orchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraOrchestrator.class);
    protected final Map<Integer, CassandraServerNode> nodes = new TreeMap<>();

    private CassandraOrchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        super(megaServer, properties);
    }

    @Override
    public synchronized void startEnsemble(long endTime) throws Exception {
        megaServer.setReady();
        for (int id = 1; id <= 3; id++) {
            final CassandraServerNode node = new CassandraServerNode(megaServer, this, trialId, id, id);
            node.purgePersistentData(); // TODO: prepare persistent data
            node.start();
            nodes.put(id, node);
        }
        if (waitActiveEnsemble) {
            waitForServersActive(null, endTime);
        } else {
            Thread.sleep(this.warmupMillis);
        }
    }

    @Override
    public Collection<Integer> getServerNodeIds() {
        return nodes.keySet();
    }

    @Override
    public ServerNode getServerNodeById(int id) {
        return nodes.get(id);
    }

//    @Override
//    public void reportResult() {
//        for (int id = 1; id <= 3; id++) {
//            final CassandraServerNode node = nodes.get(id);
//            if (node != null) {
//                System.out.println("Node " + id + ": {inject:" + node.isInjected() + ", status:" + node.getStatus() + "}");
//            }
//        }
//        super.reportResult();
//    }

    @Override
    public synchronized void close() throws Exception {
        for (int id = 1; id <= 3; id++) {
            final CassandraServerNode node = nodes.get(id);
            if (node != null) {
                node.shutdown();
                node.purgePersistentData();
            }
        }
    }

    public static CassandraOrchestrator build(final MegaServer megaServer, final Properties properties)
            throws Exception {
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("invalid workload name");
        }
        final CassandraOrchestrator orch = new CassandraOrchestrator(megaServer, properties);
        final int entryNum = Integer.parseInt(properties.getProperty("entryNum"));
        final int port = Integer.parseInt(properties.getProperty("clientPort"));
        switch (workloadName) {
            case "CreateReadWrite" : {
                orch.workloads.add(new CreateWorkload(orch, entryNum, port));
                orch.workloads.add(new ReadWriteWorkload(orch, entryNum, port));
                return orch;
            }
            case "CreateReadWriteRepair": {
                orch.workloads.add(new CreateWorkload(orch, entryNum, port));
                orch.workloads.add(new ReadWriteWorkload(orch, entryNum, port));
                orch.workloads.add(new RepairWorkload(orch));
                return orch;
            }
            default: throw new Exception("unknown workload");
        }
    }
}
