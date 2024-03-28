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

import edu.umich.order.legolas.common.api.OrchestratorRemote.ClientFeedback;
import java.rmi.RemoteException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ZooKeeperGrayWatcherMain {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperGrayWatcherMain.class);

    public static boolean recognizeCommand(final String command) {
        return command.equals("watcherZNodeCreate") ||
                command.equals("watcherZNodeReadWriteDelete");
    }

    public static void run(final String[] args) {
        final String command = args[0];
        try {
            switch (command) {
                case "watcherZNodeCreate"          : watcherZNodeCreate(args);          break;
                case "watcherZNodeReadWriteDelete" : watcherZNodeReadWriteDelete(args); break;
                default: LOG.error("undefined watcher command -- " + command);
            }
        } catch (final Exception e) {
            LOG.error("Exception in watcher:", e);
        }
    }

    private static final Object stubLock = new Object();

    private static void reply(final int clientId, final int progress,
            final long nano, final String result) {
        final long duration = System.nanoTime() - nano;
        final String msg = String.format("progress = %d, time = %d, result = %s, client = %d",
                progress, System.nanoTime() - ZooKeeperGrayClientMain.t0, result, clientId);
        LOG.info(msg);
        if (ZooKeeperGrayClientMain.stub != null) {
            synchronized (stubLock) {
                try {
                    ZooKeeperGrayClientMain.stub.send(
                            new ClientFeedback(clientId, result, duration));
                } catch (final RemoteException e) {
                    LOG.error("Fail to send the request result to orchestrator server", e);
                    System.exit(0);
                }
            }
        }
    }

    private static void watcherZNodeCreate(final String[] args) throws Exception {
        final String addr = "localhost:" + Integer.parseInt(args[1]);
        final int clientWatchId, existWatchId, childrenWatchId;
        if (ZooKeeperGrayClientMain.stub != null) {
            clientWatchId = Integer.parseInt(args[2]);
            existWatchId = Integer.parseInt(args[3]);
            childrenWatchId = Integer.parseInt(args[4]);
        } else {
            clientWatchId = existWatchId =  childrenWatchId = -1;
        }
        int retry = 3;
        ForwardingWatcher remoteWatcher, existWatcher, childrenWatcher;
        int progress = 0;
        String result;
        long nano;
        while (true) {
            remoteWatcher = new ForwardingWatcher(clientWatchId, "remote");
            remoteWatcher.updateStartingTime(System.nanoTime());
            try (final ZooKeeper client =
                    new ZooKeeper(addr, ZooKeeperGrayClientMain.timeout, remoteWatcher)) {
                remoteWatcher.awaitConnection();
                switch (progress) {
                    case 0:
                        nano = System.nanoTime();
                        result = client.exists("/foo", true) == null? "null" : "exist";
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    case 1:
                        nano = System.nanoTime();
                        result = client.create("/foo", "missing".getBytes(),
                                Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    case 2:
                        existWatcher = new ForwardingWatcher(existWatchId, "barExist");
                        nano = System.nanoTime();
                        existWatcher.updateStartingTime(nano);
                        result = client.exists("/foo/bar", existWatcher) == null? "null" : "exist";
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    case 3:
                        childrenWatcher = new ForwardingWatcher(childrenWatchId, "fooChildren");
                        nano = System.nanoTime();
                        childrenWatcher.updateStartingTime(nano);
                        result = client.getChildren("/foo", childrenWatcher).toString();
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    case 4:
                        nano = System.nanoTime();
                        result = client.create("/foo/bar", "missing".getBytes(),
                                Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    case 5:
                        nano = System.nanoTime();
                        result = client.getChildren("/foo", true).toString();
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    case 6:
                        nano = System.nanoTime();
                        result = client.create("/foo/car", "missing".getBytes(),
                                Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                        progress++;
                        reply(ZooKeeperGrayClientMain.clientId, progress, nano, result);
                    default:
                }
                break;
            } catch (final Exception e) {
                if (retry == 0) {
                    throw e;
                }
                retry--;
                LOG.error("receive exception", e);
                Thread.sleep(20);
            }
        }
    }

    private static final AtomicInteger watcherCounter = new AtomicInteger(0);

    private static class ForwardingWatcher implements Watcher {
        private final CountDownLatch connectedSignal = new CountDownLatch(1);
        private final int forwardingClientId;
        private final String name;
        private long nano = -1;
        private int progress = 0;

        public ForwardingWatcher(final int forwardingClientId, final String name) {
            this.forwardingClientId = forwardingClientId;
            this.name = name + "_" + watcherCounter.getAndIncrement();
        }

        public ForwardingWatcher(final String name) {
            this(-1, name);
        }

        public void updateStartingTime(final long nano) {
            // better than synchronized (this) { }
            synchronized (this.name) {
                this.nano = nano;
            }
        }

        @Override
        public void process(WatchedEvent watchedEvent) {
            LOG.info("watcher <{}> received event {}", name, watchedEvent.toString());
            if (watchedEvent.getType() != EventType.None) {
                if (this.forwardingClientId != -1) {
                    long nano;
                    // better than synchronized (this) { }
                    synchronized (this.name) {
                        if (this.nano == -1) {
                            LOG.error("starting time of the event is not set for watcher <{}>",
                                    this.name);
                        }
                        nano = this.nano;
                    }
                    this.progress++;
                    reply(this.forwardingClientId, this.progress, nano, watchedEvent.toString());
                }
            }
            if (watchedEvent.getState() == KeeperState.SyncConnected) {
                connectedSignal.countDown();
            }
        }

        public void awaitConnection() throws Exception {
            if (!connectedSignal.await(ZooKeeperGrayClientMain.timeout, TimeUnit.MILLISECONDS)) {
                throw new Exception("ZooKeeper client timeout in starting");
            }
        }
    }

    private static void watcherZNodeReadWriteDelete(final String[] args) throws Exception {
        final String addr1 = "localhost:" + Integer.parseInt(args[1]);
        final String addr2 = "localhost:" + Integer.parseInt(args[2]);
        final String addr3 = "localhost:" + Integer.parseInt(args[3]);
        final int id1 = ZooKeeperGrayClientMain.clientId;
        final int id2, id3;
        final int w1, w2, w3;
        final int barWriteWatchId, carDeleteWatchId;
        if (ZooKeeperGrayClientMain.stub != null) {
            id2 = Integer.parseInt(args[4]);
            id3 = Integer.parseInt(args[5]);
            w1 = Integer.parseInt(args[6]);
            w2 = Integer.parseInt(args[7]);
            w3 = Integer.parseInt(args[8]);
            barWriteWatchId = Integer.parseInt(args[9]);
            carDeleteWatchId = Integer.parseInt(args[10]);
        } else {
            id2 = id3 = -1;
            w1 = w2 = w3 = -1;
            barWriteWatchId = carDeleteWatchId = -1;
        }

        final CountDownLatch fooWriteLatch = new CountDownLatch(1);
        final CountDownLatch barWriteLatch = new CountDownLatch(1);
        final CountDownLatch carWriteLatch = new CountDownLatch(1);
        final CountDownLatch fooDeleteLatch = new CountDownLatch(1);
        final CountDownLatch barDeleteLatch = new CountDownLatch(1);
        final CountDownLatch carDeleteLatch = new CountDownLatch(1);
        final CountDownLatch emptyLatch = new CountDownLatch(2);

        final Thread client1 = new ReadWriteDeleteClientThread(id1, addr1, w1, "client1") {
            @Override
            protected String read1(final ZooKeeper client) throws Exception {
                final String result = new String(
                        client.getData("/foo", true, null));
                fooWriteLatch.countDown();
                return result;
            }
            @Override
            protected void write(final ZooKeeper client) throws Exception {
                if (barWriteLatch.getCount() > 0) {
                    barWriteLatch.await();
                }
                client.setData("/foo/bar", "bar".getBytes(), -1);
            }
            @Override
            protected String read2(final ZooKeeper client) throws Exception {
                final ForwardingWatcher watcher = new ForwardingWatcher(carDeleteWatchId, "car");
                final String result = new String(
                        client.getData("/foo/car", watcher, null));
                carDeleteLatch.countDown();
                return result;
            }
            @Override
            protected void delete(final ZooKeeper client) throws Exception {
                if (fooDeleteLatch.getCount() > 0) {
                    fooDeleteLatch.await();
                }
                if (emptyLatch.getCount() > 0) {
                    emptyLatch.await();
                }
                client.delete("/foo", -1);
            }
        };

        final Thread client2 = new ReadWriteDeleteClientThread(id2, addr2, w2, "client2") {
            @Override
            protected String read1(final ZooKeeper client) throws Exception {
                final ForwardingWatcher watcher = new ForwardingWatcher(barWriteWatchId, "bar");
                final String result = new String(
                        client.getData("/foo/bar", watcher, null));
                barWriteLatch.countDown();
                return result;
            }
            @Override
            protected void write(final ZooKeeper client) throws Exception {
                if (carWriteLatch.getCount() > 0) {
                    carWriteLatch.await();
                }
                client.setData("/foo/car", "car".getBytes(), -1);
            }
            @Override
            protected String read2(final ZooKeeper client) throws Exception {
                final String result = new String(
                        client.getData("/foo", true, null));
                fooDeleteLatch.countDown();
                return result;
            }
            @Override
            protected void delete(final ZooKeeper client) throws Exception {
                if (barDeleteLatch.getCount() > 0) {
                    barDeleteLatch.await();
                }
                client.delete("/foo/bar", -1);
                emptyLatch.countDown();
            }
        };

        final Thread client3 = new ReadWriteDeleteClientThread(id3, addr3, w3, "client3") {
            @Override
            protected String read1(final ZooKeeper client) throws Exception {
                final String result = new String(
                        client.getData("/foo/car", true, null));
                carWriteLatch.countDown();
                return result;
            }
            @Override
            protected void write(final ZooKeeper client) throws Exception {
                if (fooWriteLatch.getCount() > 0) {
                    fooWriteLatch.await();
                }
                client.setData("/foo", "foo".getBytes(), -1);
            }
            @Override
            protected String read2(final ZooKeeper client) throws Exception {
                final String result = new String(
                        client.getData("/foo/bar", true, null));
                barDeleteLatch.countDown();
                return result;
            }
            @Override
            protected void delete(final ZooKeeper client) throws Exception {
                if (carDeleteLatch.getCount() > 0) {
                    carDeleteLatch.await();
                }
                client.delete("/foo/car", -1);
                emptyLatch.countDown();
            }
        };

        client1.start();
        client2.start();
        client3.start();
        client1.join();
        client2.join();
        client3.join();
    }

    private static abstract class ReadWriteDeleteClientThread extends Thread {
        public final int id;
        public final int watcherId;
        public final String addr;
        public final String clientName;

        public ReadWriteDeleteClientThread(final int id, final String addr,
                final int watcherId, final String clientName) {
            this.id = id;
            this.addr = addr;
            this.watcherId = watcherId;
            this.clientName = clientName;
        }

        protected abstract String read1(final ZooKeeper client) throws Exception;
        protected abstract void write(final ZooKeeper client) throws Exception;
        protected abstract String read2(final ZooKeeper client) throws Exception;
        protected abstract void delete(final ZooKeeper client) throws Exception;

        @Override
        public void run() {
            ForwardingWatcher watcher;
            int progress = 0;
            int retry = 3;
            String result;
            long nano;
            while (true) {
                watcher = new ForwardingWatcher(watcherId, clientName);
                watcher.updateStartingTime(System.nanoTime());
                try (final ZooKeeper client = new ZooKeeper(addr, ZooKeeperGrayClientMain.timeout,
                        watcher)) {
                    watcher.awaitConnection();
                    switch (progress) {
                        case 0:
                            nano = System.nanoTime();
                            result = read1(client);
                            progress++;
                            reply(id, progress, nano, result);
                        case 1:
                            nano = System.nanoTime();
                            write(client);
                            result = "success";
                            progress++;
                            reply(id, progress, nano, result);
                        case 2:
                            nano = System.nanoTime();
                            result = read2(client);
                            progress++;
                            reply(id, progress, nano, result);
                        case 3:
                            nano = System.nanoTime();
                            delete(client);
                            result = "success";
                            progress++;
                            reply(id, progress, nano, result);
                        default:
                    }
                    break;
                } catch (final Exception e) {
                    if (retry == 0) {
                        LOG.error(clientName + " has fatal error", e);
                        return;
                    }
                    retry--;
                    LOG.error("receive exception", e);
                    try {
                        Thread.sleep(20);
                    } catch (final Exception ignored) { }
                }
            }
        }
    }
}
