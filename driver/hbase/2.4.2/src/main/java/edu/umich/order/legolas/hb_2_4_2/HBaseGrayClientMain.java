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
package edu.umich.order.legolas.hb_2_4_2;

import edu.umich.order.legolas.common.api.ClientStubFactory;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote.ClientFeedback;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class HBaseGrayClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseGrayClientMain.class);

    private static OrchestratorRemote stub = null;
    private static int clientId = -1;
    private static int tableId, columnFamilyNumber, quantifierNumber, rowNumber;
    private static int request;

    private final static String data = "asdf";
    private final static Random rand = new Random();

    private static int progress = 0;
    private static long t0 = System.nanoTime();

    private static void reply(final String result, final long nano) {
        LOG.info("progress = {}, time = {}", ++progress, System.nanoTime() - t0);
        if (stub != null) {
            try {
                stub.send(new ClientFeedback(clientId, result, System.nanoTime() - nano));
            } catch (final RemoteException e) {
                LOG.error("Fail to send the request result to orchestrator server", e);
                System.exit(0);
            }
        }
    }

    private static void create() {
        int progress = 0;
        int failure = 0;
        while (progress < columnFamilyNumber * quantifierNumber * rowNumber + 1) {
            try (final HBaseGrayClient client = new HBaseGrayClient()) {
                if (progress == 0) {
                    final long nano = System.nanoTime();
                    client.createTable(tableId, columnFamilyNumber);
                    reply("success", nano);
                    progress++;
                }
                while (progress < columnFamilyNumber * quantifierNumber * rowNumber + 1) {
                    final int r = (progress - 1) % rowNumber;
                    final int q = (progress - 1 - r) / rowNumber % quantifierNumber;
                    final int cf = (progress - 1 - r - q * rowNumber) / quantifierNumber / rowNumber;
                    final long nano = System.nanoTime();
                    client.put(tableId, cf, q, r, data);
                    reply("success", nano);
                    progress++;
                }
            } catch (Exception e) {
                failure++;
                LOG.warn("HBase client exception", e);
                if (failure > 3) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void read() {
        int progress = 0;
        int failure = 0;
        while (progress < request) {
            try (final HBaseGrayClient client = new HBaseGrayClient()) {
                while (progress < request) {
                    final int r = rand.nextInt(rowNumber);
                    final long nano = System.nanoTime();
                    client.get(tableId, r);
                    reply("success", nano);
                    progress++;
                }
            } catch (Exception e) {
                failure++;
                LOG.warn("HBase client exception", e);
                if (failure > 3) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void write() {
        int progress = 0;
        int failure = 0;
        while (progress < request) {
            try (final HBaseGrayClient client = new HBaseGrayClient()) {
                while (progress < request) {
                    final int r = rand.nextInt(rowNumber);
                    final int q = rand.nextInt(quantifierNumber);
                    final int cf = rand.nextInt(columnFamilyNumber);
                    final long nano = System.nanoTime();
                    client.put(tableId, cf, q, r, data);
                    reply("success", nano);
                    progress++;
                }
            } catch (Exception e) {
                failure++;
                LOG.warn("HBase client exception", e);
                if (failure > 3) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void run(final String[] args) {
        LOG.info("running command " + Arrays.toString(args));
        final String command = args[0];
        tableId = Integer.parseInt(args[1]);
        columnFamilyNumber = Integer.parseInt(args[2]);
        quantifierNumber = Integer.parseInt(args[3]);
        rowNumber = Integer.parseInt(args[4]);
        request = Integer.parseInt(args[5]);
        switch (command) {
            case "create" : create(); break;
            case "read"   : read();   break;
            case "write"  : write();  break;
            default: LOG.error("undefined command -- " + command);
        }
    }

    public static void main(final String[] args) {
        final String name = ManagementFactory.getRuntimeMXBean().getName();
        final long pid = Long.parseLong(name.substring(0, name.indexOf('@')));
        LOG.info("My process's pid is {}", pid);
        if (args.length == 1) {
            clientId = Integer.parseInt(args[0]);
            LOG.info("My client id is {}", clientId);
            stub = ClientStubFactory.getOrchestratorStub(1099);
            if (stub == null) {
                LOG.error("Failed to get a client for orchestrator server");
            } else {
                try {
                    final String[] command = stub.registerClient(clientId, pid);
                    run(command);
                } catch (final RemoteException e) {
                    LOG.error("Failed to register with the orchestrator server");
                }
            }
        } else {
            run(args);
        }
    }
}
