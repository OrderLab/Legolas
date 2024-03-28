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
package edu.umich.order.legolas.orchestrator.server;

import edu.umich.order.legolas.common.api.AbstractStateServerRemote;
import edu.umich.order.legolas.common.api.FaultInjectorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.RegistryFactory;
import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import edu.umich.order.legolas.common.event.ReadyEvent;
import edu.umich.order.legolas.common.event.ShutdownEvent;
import edu.umich.order.legolas.common.event.StartEvent;
import edu.umich.order.legolas.common.record.OrchestratorStats;
import edu.umich.order.legolas.common.server.AbstractStateServer;
import edu.umich.order.legolas.common.util.Mutex;
import edu.umich.order.legolas.injector.server.FaultInjectorServer;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One server to bind them, one server to rule them all...
 */
public final class MegaServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MegaServer.class);

    private final OrchestratorServer orchServer;
    private final FaultInjectorServer fiServer;
    private final AbstractStateServer asServer;
    private final OrchestratorStats stats = new OrchestratorStats();
    private final Mutex mutex = new Mutex();

    private final boolean recordStates;

    // Maps from ports to registry
    private final Map<Integer, Registry> registryMap = new HashMap<>();

    public MegaServer(final Properties properties,
            final int orch_port, final int fi_port, final int as_port) throws RemoteException {
        final String recordStates = properties.getProperty("recordStates");
        this.recordStates = recordStates != null && recordStates.equals("true");
        this.stats.recordStates = this.recordStates;
        if (!registryMap.containsKey(orch_port)) {
            registryMap.put(orch_port, RegistryFactory.getRegistry(orch_port, true));
        }
        orchServer = new OrchestratorServer(properties, mutex, orch_port, registryMap.get(orch_port), false);
        if (!registryMap.containsKey(as_port)) {
            registryMap.put(as_port, RegistryFactory.getRegistry(as_port, true));
        }
        asServer = new AbstractStateServer(stats, mutex, properties, 
            as_port, registryMap.get(as_port), false);
        if (!registryMap.containsKey(fi_port)) {
            registryMap.put(fi_port, RegistryFactory.getRegistry(fi_port, true));
        }
        fiServer = new FaultInjectorServer(stats, asServer, properties, mutex,
                fi_port, registryMap.get(fi_port), false);
    }

    public void setWorkload(final Workload workload) {
        orchServer.setWorkload(workload);
    }

    public boolean hasNextTrial() {
        return fiServer.hasNextTrial();
    }

    public void setupNewTrial(boolean incrementId) {
        fiServer.setupNewTrial(incrementId);
    }

    /**
     * Invoked when a trial ends
     */
    public void onTrialStopped() {
        fiServer.onTrialStopped();
    }

    public int getTrialId() {
        return fiServer.getTrialId();
    }

    public void setReady() {
        LOG.info("ready for injection");
        stats.record(new ReadyEvent(System.nanoTime()));
        fiServer.setReady();
    }

    public void setStart(final int serverId) {
        stats.record(new StartEvent(System.nanoTime(), serverId));
    }

    public void setShutdown(final int serverId) {
        stats.record(new ShutdownEvent(System.nanoTime(), serverId));
    }

    public void initStats() {
        orchServer.initStats(stats);
    }

    public void dumpStats(final Properties properties) {
        if (!recordStates) {
            return;
        }
        final JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        jsonBuilder.add("target_system", properties.getProperty("targetSystem"));
        jsonBuilder.add("trial_id", getTrialId());
        final String path = properties.getProperty("workspacePathName") + "/trials/" + getTrialId();
        try (final BufferedWriter csv = new BufferedWriter(new FileWriter(
                new File(path + "/orch.csv")))) {
            try (final BufferedWriter json = new BufferedWriter(new FileWriter(
                    new File(path + "/orch.json")))) {
                stats.dump(csv, json, jsonBuilder);
            }
        } catch (final IOException e) {
            LOG.warn("exception when dumping the stats", e);
        }
    }

    public MegaServer(final Properties properties) throws RemoteException {
        this(properties, OrchestratorRemote.REMOTE_PORT, FaultInjectorRemote.REMOTE_PORT,
                AbstractStateServerRemote.REMOTE_PORT);
    }

    public void start() throws RemoteException {
        try {
            asServer.start();
            fiServer.start();
            orchServer.start();
            LOG.info("Successfully started the mega server");
        } catch (Exception e) {
            throw new RemoteException("Failed to start mega server", e);
        }
    }

    public long getRecentPid() {
        return orchServer.getRecentPid();
    }

    public void prepareNodeStart(final int serverId) {
        orchServer.prepareSid(serverId);
        // TODO: record this asmm
        final AbstractStateMachineManager newAsmm = asServer.createAsmManagerForServer(serverId, true);
    }

    @Override
    public void close() {
        asServer.shutdown();
        fiServer.shutdown();
        orchServer.shutdown();
    }
}
