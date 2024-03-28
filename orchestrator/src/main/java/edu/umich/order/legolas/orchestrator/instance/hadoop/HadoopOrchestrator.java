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
package edu.umich.order.legolas.orchestrator.instance.hadoop;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.instance.hadoop.workload.WriteWorkload;
import edu.umich.order.legolas.orchestrator.instance.hadoop.workload.ReadWorkload;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.ServerNode;

import java.util.Map;
import java.util.Properties;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 *
 */
public final class HadoopOrchestrator extends Orchestrator {
    protected NameNodeServerNode namenode;
    protected final Map<Integer, DataNodeServerNode> datanodes = new TreeMap<>();
    protected final List<Integer> nodeIds = new ArrayList();

    private HadoopOrchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        super(megaServer, properties);
    }

    @Override
    public synchronized void startEnsemble(long endTime) throws Exception {
        final NameNodeServerNode namenode = new NameNodeServerNode(megaServer, this, trialId, 1, 1);
        namenode.preparePersistentData();
        namenode.start();
        nodeIds.add(1);
        this.namenode = namenode;
        for (int id = 2; id <= 3; id++) {
            final DataNodeServerNode datanode = new DataNodeServerNode(megaServer, this, trialId, id, id);
            datanode.preparePersistentData();
            datanode.start();
            datanodes.put(id, datanode);
            nodeIds.add(id);
        }
        Thread.sleep(this.warmupMillis);
        megaServer.setReady();
    }

    @Override
    public Collection<Integer> getServerNodeIds() {
        return nodeIds;
    }

    @Override
    public ServerNode getServerNodeById(int id) {
        if (id == 1)
            return namenode;
        return datanodes.get(id);
    }


    @Override
    public synchronized void close() throws Exception {
        if (namenode != null) {
            namenode.shutdown();
            namenode.purgePersistentData();
        }
        for (int id = 2; id <= 3; id++) {
            final DataNodeServerNode datanode = datanodes.get(id);
            if (datanode != null) {
                datanode.shutdown();
                datanode.purgePersistentData();
            }
        }
    }

    public static HadoopOrchestrator build(final MegaServer megaServer, final Properties properties)
            throws Exception {
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("invalid workload name");
        }
        switch (workloadName) {
            case "WriteRead" : {
                final int filenum = Integer.parseInt(properties.getProperty("filenum"));
                final int repeat = Integer.parseInt(properties.getProperty("repeat"));
                final int parallel = Integer.parseInt(properties.getProperty("parallel"));
                final HadoopOrchestrator orch = new HadoopOrchestrator(megaServer, properties);
                orch.workloads.add(new WriteWorkload(orch, filenum, repeat));
                orch.workloads.add(new ReadWorkload(orch, filenum, parallel, repeat));
                return orch;
            }
            default: throw new Exception("unknown workload");
        }
    }
}
