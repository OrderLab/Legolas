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

package edu.umich.order.legolas.zk_3_4_6;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperGrayClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperGrayClientMain.class);

    private final ZooKeeper zk;

    public ZooKeeperGrayClient(final String addr, final int timeout) throws Exception {
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        this.zk = new ZooKeeper(addr, timeout, watchedEvent -> {
            if (watchedEvent.getState() == KeeperState.SyncConnected) {
                connectedSignal.countDown();
            }
        });
        if (!connectedSignal.await(timeout, TimeUnit.MILLISECONDS)) {
            throw new Exception("ZooKeeper client timeout in starting");
        }
    }

    public final synchronized void create(final String path, final byte[] data) throws Exception {
        zk.create(path, data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    public final synchronized boolean exists(final String path) throws Exception {
        return zk.exists(path, false) != null;
    }

    public final synchronized byte[] getData(final String path) throws Exception {
        return zk.getData(path, false, null);
    }

    public final synchronized void setData(final String path, final byte[] data) throws Exception {
        zk.setData(path, data, -1);
    }

    public final synchronized void delete(final String path) throws Exception {
        zk.delete(path, -1);
    }

    public final synchronized List<String> getChildren(final String path) throws Exception {
        return zk.getChildren(path, false);
    }

    @Override
    public final synchronized void close() throws Exception {
        zk.close();
    }
}
