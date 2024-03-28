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
import edu.umich.order.legolas.orchestrator.instance.hbase.workload.CreateWorkload;
import edu.umich.order.legolas.orchestrator.instance.hbase.workload.ReadWriteWorkload;
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
public final class HBaseOrchestrator extends Orchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseOrchestrator.class);

    protected HMasterServerNode master = null;
    protected final Map<Integer, HRSServerNode> regionServers = new TreeMap<>();
    protected final List<Integer> nodeIds = new ArrayList();

    private HBaseOrchestrator(final MegaServer megaServer, final Properties properties) throws Exception {
        super(megaServer, properties);
    }

    @Override
    public synchronized void startEnsemble(long endTime) throws Exception {
        LOG.info("Cleaning the HBase data in HDFS and ZooKeeper");
        final ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", workspacePathName + "/clean.sh");
        pb.redirectErrorStream(true);
        pb.start().waitFor();
        megaServer.setReady();
        final HMasterServerNode master = new HMasterServerNode(megaServer, this, trialId, 1, 1);
        master.preparePersistentData();
        master.start();
        this.master = master;
        nodeIds.add(1);
        Thread.sleep(this.warmupMillis);
        for (int id = 2; id <= 3; id++) {
            final HRSServerNode regionServer = new HRSServerNode(megaServer, this, trialId, id, id);
            regionServer.preparePersistentData();
            regionServer.start();
            regionServers.put(id, regionServer);
            nodeIds.add(id);
        }
    }

    @Override
    public Collection<Integer> getServerNodeIds() {
        return nodeIds;
    }

    @Override
    public ServerNode getServerNodeById(int id) {
        if (id == 1)
            return master;
        return regionServers.get(id);
    }

    @Override
    public synchronized void close() throws Exception {
        for (int id = 2; id <= 3; id++) {
            final HRSServerNode regionServer = regionServers.get(id);
            if (regionServer != null) {
                regionServer.shutdown();
                regionServer.purgePersistentData();
            }
        }
        if (master != null) {
            master.shutdown();
            master.purgePersistentData();
        }
    }

    public static HBaseOrchestrator build(final MegaServer megaServer, final Properties properties)
            throws Exception {
        final String workloadName = properties.getProperty("workload");
        if (workloadName == null) {
            throw new Exception("invalid workload name");
        }
        switch (workloadName) {
            case "CreateReadWrite" : {
                int tableNumber = Integer.parseInt(properties.getProperty("tableNumber"));
                int columnFamilyNumber = Integer.parseInt(properties.getProperty("columnFamilyNumber"));
                int quantifierNumber = Integer.parseInt(properties.getProperty("quantifierNumber"));
                int rowNumber = Integer.parseInt(properties.getProperty("rowNumber"));
                int requestNumber = Integer.parseInt(properties.getProperty("requestNumber"));
                final HBaseOrchestrator orch = new HBaseOrchestrator(megaServer, properties);
                orch.workloads.add(new CreateWorkload(orch, tableNumber, columnFamilyNumber,
                        quantifierNumber, rowNumber));
                orch.workloads.add(new ReadWriteWorkload(orch, tableNumber, columnFamilyNumber,
                        quantifierNumber, rowNumber, requestNumber));
                return orch;
            }
            default: throw new Exception("unknown workload");
        }
    }
}
