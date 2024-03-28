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
package edu.umich.order.legolas.datastax_3_1_4;

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
public class CassandraClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraClientMain.class);

    private static final String keyspaceName = "gray_space";
    private static final String tableName = "gray_table";
    private static final String primaryKeyName = "gray_id";
    private static final String[] fieldNames = {
            "field0", "field1", "field2", "field3", "field4",
            "field5", "field6", "field7", "field8", "field9",
    };
    private static final String replicationStrategy = "SimpleStrategy";
    private static final int replicationFactor = 3;
    private static final int fieldLen = 100;
    private static final byte[] fieldSeeds = new byte[fieldLen*2];

    private final static Random rand;

    static {
        rand = new Random(System.nanoTime() + 1);
        for (int i = 0; i < fieldLen*2; i++) {
            fieldSeeds[i] = (byte)(((byte)'0') + ((byte)(rand.nextInt(10))));
        }
    }

    protected static String host;
    protected static int port;
    protected static int timeout;
    protected static int entryNum;

    private static OrchestratorRemote stub = null;
    private static int clientId = -1;

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

    private static void create() throws Exception {
        final StringBuilder createBuilder = new StringBuilder("CREATE TABLE ");
        createBuilder.append(tableName).append("(");
        createBuilder.append(primaryKeyName).append(" int primary key");
        for (final String fieldName : fieldNames) {
            createBuilder.append(",").append(fieldName).append(" ascii");
        }
        createBuilder.append(");");
        final String createCmd = createBuilder.toString();
        final StringBuilder insertFormatBuilder = new StringBuilder("INSERT INTO ");
        insertFormatBuilder.append(tableName).append("(").append(primaryKeyName);
        for (final String fieldName : fieldNames) {
            insertFormatBuilder.append(",").append(fieldName);
        }
        insertFormatBuilder.append(") VALUES(%d");
        for (final String fieldName : fieldNames) {
            insertFormatBuilder.append(",'");
            for (int i = 0; i < fieldLen; i++)
                insertFormatBuilder.append('0');
            insertFormatBuilder.append("'");
        }
        insertFormatBuilder.append(");");
        final String insertFormat = insertFormatBuilder.toString();
        int progress = 0;
        int failure = 0;
        while (progress - 2 < entryNum) {
            try (final CassandraClient client = new CassandraClient(host, port, timeout)) {
                if (progress == 0) {
                    final long nano = System.nanoTime();
                    client.createKeyspace(keyspaceName, replicationStrategy, replicationFactor);
                    progress++;
                    reply("success", nano);
                }
                boolean usingKeyspace = false;
                if (progress == 1) {
                    final long nano = System.nanoTime();
                    client.useKeyspace(keyspaceName);
                    usingKeyspace = true;
                    client.execute(createCmd);
                    progress++;
                    reply("success", nano);
                }
                if (progress >= 2) {
                    if (!usingKeyspace) {
                        client.useKeyspace(keyspaceName);
                        usingKeyspace = true;
                    }
                    for (int i = progress - 2; i < entryNum; i++) {
                        final long nano = System.nanoTime();
                        client.execute(String.format(insertFormat, i));
                        progress++;
                        reply("success", nano);
                    }
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("Cassandra client exception -- " + e);
                if (failure > 3) {
                    return;
                }
                Thread.sleep(100);
            }
        }
    }

    private static void read() throws Exception {
        int progress = 0;
        int failure = 0;
        while (progress < entryNum * 20) {
            try (final CassandraClient client = new CassandraClient(host, port, timeout, keyspaceName)) {
                for (int i = progress; i < entryNum * 20; i++) {
                    final long nano = System.nanoTime();
                    client.read(fieldNames[rand.nextInt(fieldNames.length)], tableName,
                            primaryKeyName, rand.nextInt(entryNum));
                    progress++;
                    reply("success", nano);
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("Cassandra client exception -- " + e);
                if (failure > 3) {
                    return;
                }
                Thread.sleep(100);
            }
        }
    }

    private static void write() throws Exception {
        int progress = 0;
        int failure = 0;
        while (progress < entryNum * 20) {
            try (final CassandraClient client = new CassandraClient(host, port, timeout, keyspaceName)) {
                for (int i = progress; i < entryNum * 20; i++) {
                    final long nano = System.nanoTime();
                    client.update(tableName, fieldNames[rand.nextInt(fieldNames.length)],
                            new String(fieldSeeds, rand.nextInt(fieldLen), fieldLen),
                            primaryKeyName, rand.nextInt(entryNum));
                    reply("success", nano);
                    progress++;
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("Cassandra client exception -- " + e);
                if (failure > 3) {
                    return;
                }
                Thread.sleep(100);
            }
        }
    }

    public static void run(final String[] args) {
        LOG.info("running command " + Arrays.toString(args));
        final String command = args[0];
        entryNum = Integer.parseInt(args[1]);
        host = args[2];
        port = Integer.parseInt(args[3]);
        if (args.length > 4) {
            timeout = Integer.parseInt(args[4]);
        } else {
            timeout = 5000;
        }
        try {
            switch (command) {
                case "create" : create(); break;
                case "read"   : read();   break;
                case "write"  : write();  break;
                default: LOG.error("undefined command -- " + command);
            }
        } catch (final Exception e) {}
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
