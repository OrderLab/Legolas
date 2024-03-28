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
package edu.umich.order.legolas.orchestrator.instance.hadoop;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.LogMonitor;
import edu.umich.order.legolas.orchestrator.system.ServerNode;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 */
public final class NameNodeServerNode extends ServerNode {
    protected static String logFileName = null;

    public NameNodeServerNode(final MegaServer megaServer, final Orchestrator orchestrator,
            final int trialId, final int serverId, final int instanceId) {
        super(megaServer, orchestrator, trialId, serverId, instanceId);
    }

    protected final String getLogFileName() {
        if (logFileName == null) {
            String userName = System.getProperty("user.name");
            String hostName = "";
            try {
              hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
              hostName = "unknown";
            }
            logFileName = "hadoop-" + userName + "-namenode-" + hostName + ".log";
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
