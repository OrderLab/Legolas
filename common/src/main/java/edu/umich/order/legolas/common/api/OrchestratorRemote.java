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

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RPC interface for the orchestrator
 */
public interface OrchestratorRemote extends Remote {
    String REMOTE_NAME = "LegolasOrchestrator";
    int REMOTE_PORT = 1099;

    /**
     * Register a process
     *
     * @param pid
     * @return
     * @throws RemoteException
     */
    RegistryRemoteInfo register(final long pid) throws RemoteException;

    ClientFeedbackResponse send(final ClientFeedback feedback) throws RemoteException;

    String[] registerClient(final int clientId, final long pid) throws RemoteException;

    final class RegistryRemoteInfo implements Serializable {
        public final int serverId;
        public final String[] exceptionNames; // TODO: remove it

        public RegistryRemoteInfo(int serverId, String[] exceptionNames) {
            this.serverId = serverId;
            this.exceptionNames = exceptionNames;
        }
    }

    final class ClientFeedback implements Serializable {
        public final int clientId;
        public final String result;
        public final long duration;

        public ClientFeedback(int clientId, String result, long duration) {
            this.clientId = clientId;
            this.result = result;
            this.duration = duration;
        }
    }

    final class ClientFeedbackResponse implements Serializable {
        public final int cont;

        public ClientFeedbackResponse(int cont) {
            this.cont = cont;
        }
    }
}
