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
package edu.umich.order.legolas.orchestrator.instance.zookeeper;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.LogMonitor;
import edu.umich.order.legolas.orchestrator.system.ServerNode;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class ZooKeeperServerNode extends ServerNode {
    private static final Logger LOG = LoggerFactory.getLogger(ServerNode.class);

    protected static String logFileName = null;

    protected final boolean isNewVersion;

    public ZooKeeperServerNode(final MegaServer megaServer, final Orchestrator orchestrator,
            final int trialId, final int serverId, final int instanceId) {
        super(megaServer, orchestrator, trialId, serverId, instanceId);
        // We consider ZK 3.5+ to be new versions
        isNewVersion = orchestrator.targetSystemMajorVersion > 3 ||
                (orchestrator.targetSystemMajorVersion == 3 &&
                        orchestrator.targetSystemMinorVersion >= 5);
        LOG.debug("Target ZK version {}, isNew {}",
            orchestrator.targetSystemVersion, isNewVersion);
    }

    @Override
    public String getPersistentDataPathName() {
        // FIXME: if there are prepared data, it should be moved to store-x/version-2 instead of store-x
        return super.getPersistentDataPathName() + "/version-2";
    }

    @Override
    public String getInitPersistentDataPathName() {
        return super.getInitPersistentDataPathName() + "/version-2";
    }

    protected final String getLogFileName() {
        if (logFileName == null) {
            if (isNewVersion) {
                String userName = System.getProperty("user.name");
                String hostName = "";
                try {
                    hostName = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
                    hostName = "unknown";
                }
                logFileName = "zookeeper-" + userName + "-server-" + hostName + ".out";
            } else {
                // In old ZK versions, the log file name is simple.
                logFileName = "zookeeper.out";
            }
        }
        return logFileName;
    }

    protected LogMonitor getLogMonitor() throws Exception {
        return new LogMonitor(this) {
            @Override
            protected void handle(String line) {
                // TODO
            }
        };
    }
}
