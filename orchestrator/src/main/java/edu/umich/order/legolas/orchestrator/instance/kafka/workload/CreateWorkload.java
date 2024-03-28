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
package edu.umich.order.legolas.orchestrator.instance.kafka.workload;

import edu.umich.order.legolas.orchestrator.instance.kafka.KafkaOrchestrator;
import edu.umich.order.legolas.orchestrator.workload.ClientWorkload;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.util.LinkedList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class CreateWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(CreateWorkload.class);

    private final KafkaOrchestrator orch;

    public CreateWorkload(final KafkaOrchestrator orch, final Map<Integer, String> hosts,
            final int creatorRequestNumber, final int groupNumber, final int requestNumber) {
        super();
        this.orch = orch;
        for (int id = 1; id <= 3; id++) {
            final int clientId = orch.createClientId();
            clients.put(clientId, new ClientWorkload(orch, clientId,
                    new String[]{hosts.get(id), "create", String.valueOf(id),
                            String.valueOf(creatorRequestNumber)}, creatorRequestNumber));
        }
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> results = new LinkedList<>();
        for (final ClientWorkload client : clients.values()) {
            results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - create  :" + results);
    }
}
