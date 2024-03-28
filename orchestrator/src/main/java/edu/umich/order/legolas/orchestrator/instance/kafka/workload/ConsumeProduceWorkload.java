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
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ConsumeProduceWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumeProduceWorkload.class);

    private final KafkaOrchestrator orch;

    final Map<Integer, ClientWorkload> readers = new TreeMap<>();
    final Map<Integer, ClientWorkload> writers = new TreeMap<>();

    public ConsumeProduceWorkload(final KafkaOrchestrator orch, final Map<Integer, String> hosts,
            final int creatorRequestNumber, final int groupNumber, final int requestNumber) {
        super();
        this.orch = orch;
        for (int id = 1; id <= 3; id++) {
            for (int topicId = 0; topicId < creatorRequestNumber; topicId++) {
                final int clientId = orch.createClientId();
                final String topicName = "gray-" + id + "-" + topicId;
                final int writerId = id * creatorRequestNumber + topicId;
                final int hostId = writerId % 3 + 1;
                final ClientWorkload client = new ClientWorkload(orch, clientId,
                        new String[]{hosts.get(hostId), "produce", topicName,
                                String.valueOf(requestNumber)}, requestNumber);
                clients.put(clientId, client);
                writers.put(writerId, client);
            }
        }
        for (int id = 1; id <= 3; id++) {
            for (int topicId = 0; topicId < creatorRequestNumber; topicId++) {
                for (int groupId = 0; groupId < groupNumber; groupId++) {
                    final int clientId = orch.createClientId();
                    final String topicName = "gray-" + id + "-" + topicId;
                    final int readerId = (id * creatorRequestNumber + topicId) * groupNumber + groupId;
                    final int hostId = readerId % 3 + 1;
                    final ClientWorkload client = new ClientWorkload(orch, clientId,
                            new String[]{hosts.get(hostId), "consume", topicName,
                                    String.valueOf(groupId), String.valueOf(requestNumber)}, requestNumber);
                    clients.put(clientId, client);
                    readers.put(readerId, client);
                }
            }
        }
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> read_results = new LinkedList<>();
        for (final ClientWorkload client : readers.values()) {
            read_results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - consume :" + read_results);
        final LinkedList<String> write_results = new LinkedList<>();
        for (final ClientWorkload client : writers.values()) {
            write_results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - produce :" + write_results);
    }
}
