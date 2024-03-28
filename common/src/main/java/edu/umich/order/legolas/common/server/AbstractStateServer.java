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
package edu.umich.order.legolas.common.server;

import edu.umich.order.legolas.common.api.AbstractStateServerRemote;
import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import edu.umich.order.legolas.common.record.OrchestratorStats;
import edu.umich.order.legolas.common.util.Mutex;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RMI server that receives and processes abstract state updates from the legolas agents.
 */
public class AbstractStateServer extends RmiServerBase implements AbstractStateServerRemote {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateServer.class);

    private final Map<Integer, AbstractStateMachineManager> asmManagers = new HashMap<>();
    private final Mutex mutex;
    private final OrchestratorStats stats;
    private MetaInfoAccess lastMetaInfoAccess = null;

    private boolean META_INFO_MODE = false; // approximate meta-info (SOSP '19)

    public AbstractStateServer(OrchestratorStats stats, Mutex mutex, Properties properties,
            int port, Registry registry, boolean tryCreateReg) throws RemoteException {
        super(AbstractStateServerRemote.REMOTE_NAME, port, "AbstractStateServer", registry, tryCreateReg);
        META_INFO_MODE = Boolean.parseBoolean(properties.getProperty("metainfo_mode", "false"));
        this.mutex = mutex;
        this.stats = stats;
    }

    @Override
    public boolean informState(final StateUpdateRemoteInfo info) throws RemoteException {
        synchronized (mutex) {
            if (META_INFO_MODE)
                return false;
            final AbstractStateMachineManager asmm = asmManagers.get(info.serverId);
            if (asmm != null) {
                stats.record(asmm.update(info));
                return true;
            }
        }
        LOG.error("Cannot find the ASM for server " + info.serverId);
        return false;
    }

    @Override
    public boolean informAccess(MetaInfoAccessRemoteInfo info) throws RemoteException {
        synchronized (mutex) {
            // LOG.info("Received meta-info access " + info.access);
            lastMetaInfoAccess = info.access;
            return true;
        }
    }

    /**
     * Check if an ASM manager exists for a server node.
     *
     * @param serverId
     * @return
     */
    public boolean existsAsmManager(int serverId) {
        return asmManagers.containsKey(serverId);
    }

    /**
     * Obtain the ASM manager for a server node. This manager is a singleton for that server.
     *
     * @param serverId
     * @return
     */
    public AbstractStateMachineManager getAsmManagerByServer(int serverId) {
        synchronized (mutex) {
            return asmManagers.get(serverId);
        }
    }

    /**
     * Create an ASM manager for a server node. Each server node should contain at most one ASM
     * manager.
     *
     * @param serverId
     * @param force whether to force a re-creation of the ASM manager if it exists before
     * @return
     */
    public AbstractStateMachineManager createAsmManagerForServer(int serverId, boolean force) {
        synchronized (mutex) {
            if (!force && asmManagers.containsKey(serverId)) {
                return asmManagers.get(serverId);
            }
            final AbstractStateMachineManager manager = new AbstractStateMachineManager(serverId);
            asmManagers.put(serverId, manager);
            return manager;
        }
    }

    @Override
    public boolean serverReady(int serverId) throws RemoteException {
        return true;
    }

    @Override
    public boolean serverStopped(int serverId) throws RemoteException {
        return false;
    }

    public MetaInfoAccess getLastMetaInfoAccess() {
        return META_INFO_MODE ? lastMetaInfoAccess : null;
    }
}
