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
package edu.umich.order.legolas.orchestrator.instance.hadoop.workload;

import edu.umich.order.legolas.orchestrator.instance.hadoop.HadoopOrchestrator;
import edu.umich.order.legolas.orchestrator.workload.ClientWorkload;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class WriteWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(WriteWorkload.class);

    private final HadoopOrchestrator orch;
    public WriteWorkload(final HadoopOrchestrator orch, final int filenum, final int repeat) {
        super();
        this.orch = orch;
        for (int i = 0; i < filenum; i++) {
            final int clientId = orch.createClientId();
            clients.put(clientId, new ClientWorkload(orch, clientId,
                    new String[]{orch.workspacePathName + "/conf-1", "write", "/" + i, String.valueOf(repeat)}, repeat));
        }
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> results = new LinkedList<>();
        for (final ClientWorkload client : clients.values()) {
            results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - write  :" + results);
    }
}
