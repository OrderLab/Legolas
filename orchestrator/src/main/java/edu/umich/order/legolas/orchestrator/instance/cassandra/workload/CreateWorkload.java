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
package edu.umich.order.legolas.orchestrator.instance.cassandra.workload;

import edu.umich.order.legolas.orchestrator.instance.cassandra.CassandraOrchestrator;
import edu.umich.order.legolas.orchestrator.workload.ClientWorkload;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class CreateWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(CreateWorkload.class);

    private final CassandraOrchestrator orch;

    public CreateWorkload(final CassandraOrchestrator orch, final int entryNum, final int port) {
        super();
        this.orch = orch;
        final int clientId = orch.createClientId();
        clients.put(clientId, new ClientWorkload(orch, clientId,
                new String[]{"create", String.valueOf(entryNum), "127.0.0.1", String.valueOf(port)},
                entryNum + 2));
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> results = new LinkedList<>();
        for (final ClientWorkload client : clients.values()) {
            results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - create :" + results);
    }
}
