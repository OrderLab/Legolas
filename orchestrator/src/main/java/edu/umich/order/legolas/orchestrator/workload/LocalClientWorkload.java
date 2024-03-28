/*
 *  @author Ryan Huang <ryanph@umich.edu>
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

import edu.umich.order.legolas.orchestrator.Orchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run a client workload without going through the RPC interfaces to communicate
 * with the orchestrator server. This is useful for scenarios that directly 
 * execute a standalone workload command such as nodetool in Cassandra.
 */
public class LocalClientWorkload extends ClientWorkload {
    private static final Logger LOG = LoggerFactory.getLogger(LocalClientWorkload.class);

    public String commandName;
    public String commandArgs;

    public LocalClientWorkload(final Orchestrator orch, final int clientId,
        String commandName, String commandArgs) {
        super(orch, clientId, null, 1);
        this.commandName = commandName;
        this.commandArgs = commandArgs;
    }

    @Override
    public String clientScriptName() {
        return commandName;
    }

    @Override
    public String clientScriptArgs() {
        return commandArgs;
    }

    @Override
    public void onClientStarted(Process process) {
        // pid() method is only available on Java 9+
        // long pid = process.pid();
        notifyPid(-1);
    }

    @Override
    public void onClientFinish() {
        proceed();
    }
}
