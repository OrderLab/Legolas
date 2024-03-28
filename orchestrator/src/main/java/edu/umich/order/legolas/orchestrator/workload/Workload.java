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
package edu.umich.order.legolas.orchestrator.workload;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class Workload {
    private static final Logger LOG = LoggerFactory.getLogger(Workload.class);

    protected final Map<Integer, ClientWorkload> clients = new TreeMap<>();

    public final void proceed(final int clientId) {
        clients.get(clientId).proceed();
    }

    public final String[] registerClient(final int clientId, final long pid) {
        final ClientWorkload client = clients.get(clientId);
        client.notifyPid(pid);
        return client.command;
    }

    public final void run(final long endTime) throws Exception {
//        LOG.info("A workload started");
        final Collection<ClientWorkload> clientSet = clients.values();
        for (final ClientWorkload client : clientSet) {
            client.start();
        }
        long timeoutMS = 10 + endTime - System.currentTimeMillis();
        for (final ClientWorkload client : clientSet) {
            client.waitForStartup(timeoutMS);
        }
        final CountDownLatch signal = new CountDownLatch(1);
        final Thread merge = new Thread(() -> {
//            LOG.info("Merge started");
            for (final ClientWorkload client : clientSet) {
                try {
//                    LOG.info("Client {} joining", client.clientId);
                    client.join();
                } catch (InterruptedException e) {
                    LOG.error("Client thread fails to join due to", e);
                }
            }
//            LOG.info("All clients ended");
            signal.countDown();
        });
        merge.start();
        long d = endTime - System.currentTimeMillis();
        if (d > 0) {
            signal.await(d, TimeUnit.MILLISECONDS);
        }
        for (final ClientWorkload client : clientSet) {
            client.shutdown();
        }
        merge.join();
    }

    public abstract void reportResult(final int phase);

    public final boolean isFinished() {
        for (final ClientWorkload client : clients.values()) {
            if (!client.isFinished()) {
                return false;
            }
        }
        return true;
    }
}
