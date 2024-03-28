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
package edu.umich.order.legolas.common.api;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory that creates all sorts of client stubs for the services defined in the api package.
 *
 * The client stubs created will be singleton.
 * TODO: support multiple ports
 */
public final class ClientStubFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ClientStubFactory.class);

    private static AbstractStateServerRemote ss_stub;
    private static FaultInjectorRemote fi_stub;
    private static OrchestratorRemote orch_stub;
    private static LegolasAgentRemote ga_stub;

    /**
     * Obtain a client stub for the abstract state server. This client stub is a singleton (is it safe
     * to use it in multi-threaded code?)
     *
     * @param port
     * @return the client stub if successfully connected to the server, or null if the connection failed
     */
    public static synchronized AbstractStateServerRemote getStateServerStub(final int port) {
        if (ss_stub == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(port);
                ss_stub = (AbstractStateServerRemote) registry.lookup(AbstractStateServerRemote.REMOTE_NAME);
            } catch (RemoteException e) {
                LOG.error("Failed to find the abstract state server: " + e);
            } catch (NotBoundException e) {
                LOG.error("Failed to bind to remote: " + e);
            }
        }
        return ss_stub;
    }

    public static synchronized AbstractStateServerRemote getStateServerStub() {
        return getStateServerStub(AbstractStateServerRemote.REMOTE_PORT);
    }

    /**
     * Obtain a client stub for the fault injector server. This client stub is a singleton.
     *
     * @param port
     * @return the client stub if successfully connected to the server, or null if the connection failed
     */
    public static synchronized FaultInjectorRemote getFaultInjectorStub(final int port) {
        if (fi_stub == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(port);
                fi_stub = (FaultInjectorRemote) registry.lookup(FaultInjectorRemote.REMOTE_NAME);
            } catch (RemoteException e) {
                LOG.error("Failed to find the fault injector server: " + e);
            } catch (NotBoundException e) {
                LOG.error("Failed to bind to remote: " + e);
            }
        }
        return fi_stub;
    }

    public static synchronized FaultInjectorRemote getFaultInjectorStub() {
        return getFaultInjectorStub(FaultInjectorRemote.REMOTE_PORT);
    }

    /**
     * Obtain a client stub for the orchestrator server. This client stub is a singleton.
     *
     * @param port
     * @return the client stub if successfully connected to the server, or null if the connection failed
     */
    public static synchronized OrchestratorRemote getOrchestratorStub(final int port) {
        if (orch_stub == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(port);
                orch_stub = (OrchestratorRemote) registry.lookup(OrchestratorRemote.REMOTE_NAME);
            } catch (RemoteException e) {
                LOG.error("Failed to find the orchestrator server: " + e);
            } catch (NotBoundException e) {
                LOG.error("Failed to bind to remote: " + e);
            }
        }
        return orch_stub;
    }

    public static synchronized OrchestratorRemote getOrchestratorStub() {
        return getOrchestratorStub(OrchestratorRemote.REMOTE_PORT);
    }

    /**
     * Obtain a client stub for the legolas agent in a target system. This client stub is a singleton.
     *
     * @param port
     * @return the client stub if successfully connected to the server, or null if the connection failed
     */
    public static synchronized LegolasAgentRemote getGrayAgentStub(final int port) {
        if (ga_stub == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(port);
                ga_stub = (LegolasAgentRemote) registry.lookup(LegolasAgentRemote.REMOTE_NAME);
            } catch (RemoteException e) {
                LOG.error("Failed to find the legolas agent: " + e);
            } catch (NotBoundException e) {
                LOG.error("Failed to bind to remote: " + e);
            }
        }
        return ga_stub;
    }

    public static synchronized LegolasAgentRemote getGrayAgentStub() {
        return getGrayAgentStub(LegolasAgentRemote.REMOTE_PORT);
    }
}
