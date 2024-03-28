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
package edu.umich.order.legolas.orchestrator.instance.hbase.workload;

import edu.umich.order.legolas.orchestrator.instance.hbase.HBaseOrchestrator;
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
public final class ReadWriteWorkload extends Workload {
    private static final Logger LOG = LoggerFactory.getLogger(ReadWriteWorkload.class);

    private final HBaseOrchestrator orch;
    final Map<Integer, ClientWorkload> readers = new TreeMap<>();
    final Map<Integer, ClientWorkload> writers = new TreeMap<>();

    public ReadWriteWorkload(final HBaseOrchestrator orch, int tableNumber, int columnFamilyNumber,
            int quantifierNumber, int rowNumber, int requestNumber) {
        super();
        this.orch = orch;
        for (int tableId = 0; tableId < tableNumber; tableId++) {
            final int clientId = orch.createClientId();
            final ClientWorkload client = new ClientWorkload(orch, clientId,
                    new String[]{"read", String.valueOf(tableId), String.valueOf(columnFamilyNumber),
                            String.valueOf(quantifierNumber), String.valueOf(rowNumber), String.valueOf(requestNumber)},
                    requestNumber);
            clients.put(clientId, client);
            readers.put(tableId, client);
        }
        for (int tableId = 0; tableId < tableNumber; tableId++) {
            final int clientId = orch.createClientId();
            final ClientWorkload client = new ClientWorkload(orch, clientId,
                    new String[]{"write", String.valueOf(tableId), String.valueOf(columnFamilyNumber),
                            String.valueOf(quantifierNumber), String.valueOf(rowNumber), String.valueOf(requestNumber)},
                    requestNumber);
            clients.put(clientId, client);
            writers.put(tableId, client);
        }
    }

    @Override
    public void reportResult(final int phase) {
        final LinkedList<String> read_results = new LinkedList<>();
        for (final ClientWorkload client : readers.values()) {
            read_results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - read   :" + read_results);
        final LinkedList<String> write_results = new LinkedList<>();
        for (final ClientWorkload client : writers.values()) {
            write_results.add(client.getResult());
        }
        LOG.info("Phase " + phase + " - write  :" + write_results);
    }
}
