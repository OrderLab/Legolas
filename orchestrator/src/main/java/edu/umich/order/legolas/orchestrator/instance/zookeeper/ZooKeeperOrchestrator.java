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
package edu.umich.order.legolas.orchestrator.instance.zookeeper;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.ServerNode;
import edu.umich.order.legolas.orchestrator.instance.zookeeper.workload.CreateWorkload;
import edu.umich.order.legolas.orchestrator.instance.zookeeper.workload.ReadWriteWorkload;
import edu.umich.order.legolas.orchestrator.instance.zookeeper.workload.WatcherZNodeCreateWorkload;
import edu.umich.order.legolas.orchestrator.instance.zookeeper.workload.WatcherZNodeReadWriteDeleteWorkload;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Collection;

/**
 *
 */
public final class ZooKeeperOrchestrator extends Orchestrator {
    protected final Map<Integer, ZooKeeperServerNode> nodes = new TreeMap<>();

    private ZooKeeperOrchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        super(megaServer, properties);
    }

    @Override
    public synchronized void startEnsemble(long endTime) throws Exception {
        megaServer.setReady();
        for (int id = 1; id <= 3; id++) {
            final ZooKeeperServerNode node = new ZooKeeperServerNode(megaServer, this, trialId, id, id);
            node.preparePersistentData();
            node.start();
            nodes.put(id, node);
        }
        Thread.sleep(this.warmupMillis);
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
//            final ZooKeeperServerNode node = nodes.get(id);
//            if (node != null) {
//                System.out.println("Node " + id + ": {inject:" + node.isInjected() + ", status:" + node.getStatus() + "}");
//            }
//        }
//        super.reportResult();
//    }

    @Override
    public synchronized void close() throws Exception {
        for (int id = 1; id <= 3; id++) {
            final ZooKeeperServerNode node = nodes.get(id);
            if (node != null) {
                node.shutdown();
                node.purgePersistentData();
            }
        }
    }

    public static ZooKeeperOrchestrator build(final MegaServer megaServer, final Properties properties)
            throws Exception {
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("invalid workload name");
        }
        switch (workloadName) {
            case "CreateReadWrite" : {
                final int snapCount = Integer.parseInt(properties.getProperty("snapCount"));
                final int entryNum = snapCount / 10;
                final int iteration = entryNum * 4;
                final Map<Integer, Integer> ports = new TreeMap<>();
                for (int i = 1; i <= 3; i++) {
                    final int port = Integer.parseInt(properties.getProperty("clientPort." + i));
                    ports.put(i, port);
                }
                final ZooKeeperOrchestrator orch = new ZooKeeperOrchestrator(megaServer, properties);
                orch.workloads.add(new CreateWorkload(orch, ports, entryNum));
                orch.workloads.add(new ReadWriteWorkload(orch, ports, entryNum, iteration));
                return orch;
            }
            case "Watcher" : {
                final Map<Integer, Integer> ports = new TreeMap<>();
                for (int i = 1; i <= 3; i++) {
                    final int port = Integer.parseInt(properties.getProperty("clientPort." + i));
                    ports.put(i, port);
                }
                final ZooKeeperOrchestrator orch = new ZooKeeperOrchestrator(megaServer, properties);
                orch.workloads.add(new WatcherZNodeCreateWorkload(orch, ports));
                orch.workloads.add(new WatcherZNodeReadWriteDeleteWorkload(orch, ports));
                return orch;
            }
            default: throw new Exception("unknown workload");
        }
    }
}
