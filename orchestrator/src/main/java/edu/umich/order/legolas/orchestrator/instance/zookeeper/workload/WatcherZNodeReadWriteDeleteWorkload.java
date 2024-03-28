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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class WatcherZNodeReadWriteDeleteWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(WatcherZNodeReadWriteDeleteWorkload.class);

    private final ZooKeeperOrchestrator orch;
    private final int client1;
    private final int client2;
    private final int client3;
    private final int watcher1;
    private final int watcher2;
    private final int watcher3;
    private final int writeId;
    private final int deleteId;

    public WatcherZNodeReadWriteDeleteWorkload(final ZooKeeperOrchestrator orch,
            final Map<Integer, Integer> ports) {
        super();
        this.orch = orch;
        this.client1 = orch.createClientId();
        this.client2 = orch.createClientId();
        this.client3 = orch.createClientId();
        this.watcher1 = orch.createClientId();
        this.watcher2 = orch.createClientId();
        this.watcher3 = orch.createClientId();
        this.writeId = orch.createClientId();
        this.deleteId = orch.createClientId();
        final ArrayList<String> clientPorts = new ArrayList<>();
        for (final Integer port : ports.values()) {
            clientPorts.add(String.valueOf(port));
        }
        Collections.shuffle(clientPorts);
        clients.put(client1, new ClientWorkload(orch, client1,
                new String[]{"watcherZNodeReadWriteDelete",
                clientPorts.get(0), clientPorts.get(1), clientPorts.get(2),
                        String.valueOf(client2), String.valueOf(client3),
                        String.valueOf(watcher1), String.valueOf(watcher2), String.valueOf(watcher3),
                        String.valueOf(writeId), String.valueOf(deleteId)}, 4));
        clients.put(client2, new AttachedClientWorkload(orch, client2, 4));
        clients.put(client3, new AttachedClientWorkload(orch, client3, 4));
        clients.put(watcher1, new AttachedClientWorkload(orch, watcher1, 1));
        clients.put(watcher2, new AttachedClientWorkload(orch, watcher2, 1));
        clients.put(watcher3, new AttachedClientWorkload(orch, watcher3, 2));
        clients.put(writeId, new AttachedClientWorkload(orch, writeId, 1));
        clients.put(deleteId, new AttachedClientWorkload(orch, deleteId, 1));
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> results = new LinkedList<>();
        results.add(clients.get(client1).getResult());
        results.add(clients.get(client2).getResult());
        results.add(clients.get(client3).getResult());
        results.add(clients.get(watcher1).getResult());
        results.add(clients.get(watcher2).getResult());
        results.add(clients.get(watcher3).getResult());
        results.add(clients.get(writeId).getResult());
        results.add(clients.get(deleteId).getResult());
        LOG.info("Phase " + phase + " :" + results);
    }
}
