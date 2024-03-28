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
package edu.umich.order.legolas.injector.server;

import edu.umich.order.legolas.common.api.FaultInjectorRemote;
import edu.umich.order.legolas.common.asm.AbstractState;
import edu.umich.order.legolas.common.asm.AbstractStateMachine;
import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import edu.umich.order.legolas.common.event.ThreadInjectionEvent;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;
import edu.umich.order.legolas.common.record.OrchestratorStats;
import edu.umich.order.legolas.common.server.AbstractStateServer;
import edu.umich.order.legolas.common.server.RmiServerBase;
import edu.umich.order.legolas.common.util.Mutex;
import edu.umich.order.legolas.injector.controller.ControllerFactory;
import edu.umich.order.legolas.injector.controller.InjectionController;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fault injection server
 * TODO: refactor: by default it's aware of state, but in fact it's supposed not to be in some cases
 */
public final class FaultInjectorServer extends RmiServerBase implements FaultInjectorRemote {
    private static final Logger LOG = LoggerFactory.getLogger(FaultInjectorServer.class);

    private final Mutex mutex;
    private final OrchestratorStats stats;
    private final AbstractStateServer asServer;
    private final InjectionController controller;

    private boolean META_INFO_MODE = false; // approximate meta-info (SOSP '19)
    private boolean FATE_MODE = false; // approximate FATE (NSDI '11)

    private final AbstractState dummyAS = new AbstractState("dummy", 1);

    protected boolean TRACE;
    protected final String workspacePath;
    protected BufferedWriter traceWriter;

    private static final InjectionRemoteCommand empty_command = new InjectionRemoteCommand(0, -1, -1);

    public FaultInjectorServer(OrchestratorStats stats, AbstractStateServer asServer, Properties properties, Mutex mutex,
            int port, Registry registry, boolean tryCreateReg) throws RemoteException {
        super(FaultInjectorRemote.REMOTE_NAME, port, "FaultInjectorServer", registry, tryCreateReg);
        this.mutex = mutex;
        this.stats = stats;
        this.asServer = asServer;
        controller = ControllerFactory.createController(properties);
        workspacePath = properties.getProperty("workspacePathName") + "/trials";
        META_INFO_MODE = Boolean.parseBoolean(properties.getProperty("metainfo_mode", "false"));
        FATE_MODE = Boolean.parseBoolean(properties.getProperty("fate_mode", "false"));
        TRACE = Boolean.parseBoolean(properties.getProperty("traceDecision", "false"));
    }

    public int getTrialId() {
        return controller.getTrialId();
    }

    public boolean hasNextTrial() {
        return controller.hasNextTrial();
    }

    public void setupNewTrial(boolean incrementId) {
        controller.setupNewTrial(incrementId);
        if (TRACE) {
            try {
                String trialPath = workspacePath + "/" + getTrialId();
                traceWriter = new BufferedWriter(new FileWriter(trialPath + "/decision_time.txt"));
                traceWriter.write("time\n");
            } catch (IOException e) {
                LOG.error("Failed to create decision time trace file", e);
                traceWriter = null;
            }
        }
    }

    public void onTrialStopped() {
        if (TRACE && traceWriter != null) {
            try {
                traceWriter.close();
            } catch (IOException e) {
                LOG.error("Failed to close decision time trace file", e);
            }
            // reset traceWriter regardless
            traceWriter = null;
        }
    }

    public void setReady() {
        controller.setReady();
    }

    @Override
    public InjectionRemoteCommand inject(final InjectionRemoteQuery query) throws RemoteException {
        synchronized (mutex) {
            ThreadInjectionRequest request;
            if (FATE_MODE || META_INFO_MODE) {
                request = new ThreadInjectionRequest(query);
                request.state = dummyAS;
                request.stateMachineName = "DummyASM";
                if (META_INFO_MODE) {
                  request.lastMetaInfoAccess = asServer.getLastMetaInfoAccess();
                }
            } else {
                final AbstractStateMachineManager asmm = asServer.getAsmManagerByServer(
                        query.serverId);
                if (asmm == null) {
                    LOG.warn("Missing asmm -- server " + query.serverId);
                    return empty_command;
                }
                int instanceId = asmm.getInstanceIdByThreadId(query.threadId);
//            if (instanceId == -1) {
//                LOG.debug("No ASMM found for thread id {}, skip injection", query.threadId);
//                return empty_command;
//            }
                final AbstractStateMachine asm = asmm.getAsmByInstanceId(instanceId);
                if (asm == null) {
                    LOG.warn("No ASM found for instance id {}, skip injection", instanceId);
                    return empty_command;
                }
                request = asm.createInjectionRequest(query);
            }
            InjectionRemoteCommand command;
            if (TRACE && traceWriter != null) {
                long startTime = System.nanoTime();
                command = controller.inject(request);
                long elapsedTime = System.nanoTime() - startTime;
                try {
                    traceWriter.write(String.valueOf(elapsedTime) + "\n");
                } catch (IOException e) {
                    LOG.error("Failed to write trace time", e);
                }
            } else {
                command = controller.inject(request);
            }
            if (command.id == -1) {
                stats.record(request);
            } else {
                stats.record(new ThreadInjectionEvent(request, command.delay==1,
                        command.eid, command.id));
            }
            return command;
        }
    }
}
