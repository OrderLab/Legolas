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
package edu.umich.order.legolas.orchestrator.instance.zookeeper.workload;

import edu.umich.order.legolas.orchestrator.instance.zookeeper.ZooKeeperOrchestrator;
import edu.umich.order.legolas.orchestrator.workload.AttachedClientWorkload;
import edu.umich.order.legolas.orchestrator.workload.ClientWorkload;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class WatcherZNodeCreateWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(WatcherZNodeCreateWorkload.class);

    private final ZooKeeperOrchestrator orch;
    private final int clientId;
    private final int remoteId;
    private final int existId;
    private final int childrenId;

    public WatcherZNodeCreateWorkload(final ZooKeeperOrchestrator orch,
            final Map<Integer, Integer> ports) {
        super();
        this.orch = orch;
        this.clientId = orch.createClientId();
        this.remoteId = orch.createClientId();
        this.existId = orch.createClientId();
        this.childrenId = orch.createClientId();
        final Random rand = new Random();
        clients.put(clientId, new ClientWorkload(orch, clientId, new String[]{"watcherZNodeCreate",
                String.valueOf(ports.get(rand.nextInt(3) + 1)), String.valueOf(remoteId),
                String.valueOf(existId), String.valueOf(childrenId)}, 7));
        clients.put(remoteId, new AttachedClientWorkload(orch, remoteId, 2));
        clients.put(existId, new AttachedClientWorkload(orch, existId, 1));
        clients.put(childrenId, new AttachedClientWorkload(orch, childrenId, 1));
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> results = new LinkedList<>();
        results.add(clients.get(clientId).getResult());
        results.add(clients.get(remoteId).getResult());
        results.add(clients.get(existId).getResult());
        results.add(clients.get(childrenId).getResult());
        LOG.info("Phase " + phase + " :" + results);
    }
}
