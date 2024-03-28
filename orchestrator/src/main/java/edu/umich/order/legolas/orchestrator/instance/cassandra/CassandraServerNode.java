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
package edu.umich.order.legolas.orchestrator.instance.cassandra;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import edu.umich.order.legolas.orchestrator.server.MegaServer;
import edu.umich.order.legolas.orchestrator.system.LogMonitor;
import edu.umich.order.legolas.orchestrator.system.ServerNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class CassandraServerNode extends ServerNode {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraServerNode.class);

    protected final boolean isNewVersion;
    protected final boolean isOldVersion;

    public CassandraServerNode(final MegaServer megaServer, 
            final Orchestrator orchestrator, final int trialId,
            final int serverId, final int instanceId) {
        super(megaServer, orchestrator, trialId, serverId, instanceId);
        // we consider new version to be 3.11.10+ 
        isNewVersion = orchestrator.targetSystemMajorVersion > 3 ||
                (orchestrator.targetSystemMajorVersion == 3 &&
                        orchestrator.targetSystemMinorVersion >= 11 &&
                        orchestrator.targetSystemPatchVersion >= 10);
        // we consider old version to be 2.0.x
        isOldVersion = orchestrator.targetSystemMajorVersion <= 2 &&
                orchestrator.targetSystemMinorVersion <= 0;
        LOG.debug("Target Cassandra version {}, isNew {}",
            orchestrator.targetSystemVersion, isNewVersion);
    }

    protected final String getLogFileName() {
        return "system.log";
    }

    protected LogMonitor getLogMonitor() throws Exception {
        return new LogMonitor(this) {
            @Override
            protected void handle(String line) {
                boolean active = false;
                if (isNewVersion) {
                    if (line.contains(" - Startup complete")) {
                        active = true;
                    }
                } else if (isOldVersion)  {
                    if (line.contains("Startup completed!")) {
                        active = true;
                    }
                } else {
                    if (line.contains("Not starting RPC server as requested.")) {
                        active = true;
                    }
                }
                if (active) {
                    serverNode.setStatus("active");
                    serverNode.setActive(true);
                }
            }
        };
    }
}
