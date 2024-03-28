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
package edu.umich.order.legolas.orchestrator.instance.flink.workload;

import edu.umich.order.legolas.orchestrator.instance.flink.FlinkOrchestrator;
import edu.umich.order.legolas.orchestrator.workload.ClientWorkload;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class BatchAndStreamingWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(BatchAndStreamingWorkload.class);
    private final FlinkOrchestrator orch;
    private final String kafkaHosts;

    public BatchAndStreamingWorkload(final FlinkOrchestrator orch, final String kafkaHosts,
            final String paragraph, final int words, final int numbers) {
        super();
        this.orch = orch;
        this.kafkaHosts = kafkaHosts;
        addFlinkBatch(orch.createClientId(), paragraph);
        addKafkaSink(orch.createClientId(), "words", words);
        addFlinkStreaming(orch.createClientId());
        addKafkaSource(orch.createClientId(), "src", numbers);
        addKafkaSink(orch.createClientId(), "sink", numbers * 2);
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> results = new LinkedList<>();
        for (final ClientWorkload client : clients.values()) {
            results.add(client.getResult());
        }
        LOG.info("Phase " + phase + ":" + results);
    }

    private void addFlinkBatch(final int clientId, final String paragraph) {
        clients.put(clientId, new ClientWorkload(orch, clientId,
                new String[]{"batch", kafkaHosts, paragraph, "words"}, 1));
    }

    private void addFlinkStreaming(final int clientId) {
        clients.put(clientId, new ClientWorkload(orch, clientId,
                new String[]{"streaming", kafkaHosts, "src", "sink"}, 1));
    }

    private void addKafkaSource(final int clientId, final String src, final int progress) {
        clients.put(clientId, new ClientWorkload(orch, clientId,
                new String[]{kafkaHosts, "produce", src, String.valueOf(progress)}, progress));
    }

    private void addKafkaSink(final int clientId, final String sink, final int progress) {
        clients.put(clientId, new ClientWorkload(orch, clientId,
                new String[]{kafkaHosts, "consume", sink, "flink-group", String.valueOf(progress)}, progress));
    }
}
