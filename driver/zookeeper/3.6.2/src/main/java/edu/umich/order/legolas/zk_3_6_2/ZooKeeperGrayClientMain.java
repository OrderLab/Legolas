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

package edu.umich.order.legolas.zk_3_6_2;

import edu.umich.order.legolas.common.api.ClientStubFactory;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote.ClientFeedback;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperGrayClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperGrayClientMain.class);

    public static OrchestratorRemote stub = null;
    public static int clientId = -1;
    public static int timeout = 7000;
    public static final long t0 = System.nanoTime();

    private static int entryNum = -1;
    private static int iteration = -1;
    private static String addr = "";
    private static int serverId = -1;
    private static Random rand = new Random(System.nanoTime() + 1);

    public static void run(final String [] args) {
        LOG.info("running command " + Arrays.toString(args));
        final String command = args[0];
        if (ZooKeeperGrayWatcherMain.recognizeCommand(command)) {
            ZooKeeperGrayWatcherMain.run(args);
            return;
        }
        addr = "localhost:" + Integer.parseInt(args[1]);
        serverId = Integer.parseInt(args[2]);
        entryNum = Integer.parseInt(args[3]);
        iteration = Integer.parseInt(args[4]);
        if (args.length > 5) {
            timeout = Integer.parseInt(args[5]);
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

    private static int progress = 0;

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
        int progress = 0;
        int failure = 0;
        while (progress < entryNum) {
            try (final ZooKeeperGrayClient client = new ZooKeeperGrayClient(addr, timeout)) {
                while (progress < entryNum) {
                    if ((progress + serverId) % 3 != 0) {
                        progress++;
                        continue;
                    }
                    final long nano = System.nanoTime();
                    client.create("/zookeeper/" + progress, "0000".getBytes());
                    reply("success", nano);
                    progress++;
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("ZooKeeper client exception -- " + e);
                e.printStackTrace(System.out);
                if (failure > 3) {
                    return;
                }
                Thread.sleep(20);
            }
        }
    }

    private static void read() throws Exception {
        int progress = 0;
        int failure = 0;
        while (progress < iteration) {
            try (final ZooKeeperGrayClient client = new ZooKeeperGrayClient(addr, timeout)) {
                for (int i = progress; i < iteration; i++) {
                    final long nano = System.nanoTime();
                    client.getData("/zookeeper/" + rand.nextInt(entryNum));
                    reply("success", nano);
                    progress++;
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("ZooKeeper client exception -- " + e);
                if (failure > 3) {
                    return;
                }
                Thread.sleep(20);
            }
        }
    }

    private static final byte[][] data = {"1234".getBytes(), "asdf".getBytes(), "qwer".getBytes()};

    private static void write() throws Exception {
        int progress = 0;
        int failure = 0;
        while (progress < iteration) {
            try (final ZooKeeperGrayClient client = new ZooKeeperGrayClient(addr, timeout)) {
                for (int i = progress; i < iteration; i++) {
                    final long nano = System.nanoTime();
                    client.setData("/zookeeper/" + rand.nextInt(entryNum),
                            data[rand.nextInt(data.length)]);
                    reply("success", nano);
                    progress++;
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("ZooKeeper client exception -- " + e);
                if (failure > 3) {
                    return;
                }
                Thread.sleep(20);
            }
        }
    }

    public static void main(final String[] args) {
        if (args.length == 1) {
            clientId = Integer.parseInt(args[0]);
            LOG.info("My client id is {}", clientId);
            final String name = ManagementFactory.getRuntimeMXBean().getName();
            final long pid = Long.parseLong(name.substring(0, name.indexOf('@')));
            LOG.info("My process's pid is {}", pid);
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
