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
package edu.umich.order.legolas.common.api;

import edu.umich.order.legolas.common.asm.AbstractState;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Objects;

/**
 * The RPC interface for ASM in a system
 */
public interface AbstractStateServerRemote extends Remote {
    String REMOTE_NAME = "LegolasAbstractStateServer";
    int REMOTE_PORT = 1099;

    /**
     * Informs the server about an abstract state "entering" event in a module.
     *
     * @param info
     *
     * @return return true if this update was successfully processed by the state server
     * @throws RemoteException
     */
    boolean informState(StateUpdateRemoteInfo info) throws RemoteException;

    /**
     * Informs the server about a meta-info variable access (SOSP '19)
     * @param info
     * @return
     * @throws RemoteException
     */
    boolean informAccess(MetaInfoAccessRemoteInfo info) throws RemoteException;

    class InformRemoteInfo implements Serializable {
        public final int serverId;
        public final String className;
        public final int instanceId;
        public final String threadName;
        public final int threadId;

        public InformRemoteInfo(int serverId, String className, int instanceId, 
            String threadName, int threadId) {
            this.serverId = serverId;
            this.className = className;
            this.instanceId = instanceId;
            this.threadName = threadName;
            this.threadId = threadId;
        }
    }

    class StateUpdateRemoteInfo extends InformRemoteInfo {
        public final AbstractState state;

        public StateUpdateRemoteInfo(int serverId, String className, int instanceId, 
            String threadName, int threadId, AbstractState state) {
            super(serverId, className, instanceId, threadName, threadId);
            this.state = state;
        }
    }

    class MetaInfoAccessRemoteInfo extends InformRemoteInfo {
        public final MetaInfoAccess access;
        public MetaInfoAccessRemoteInfo(int serverId, String className, int instanceId,
              String threadName, int threadId, MetaInfoAccess access) {
            super(serverId, className, instanceId, threadName, threadId);
            this.access = access;
        }
    }

    class MetaInfoAccess implements Serializable {
        public final String methodSig;
        public final String variableName;
        public final String variableType;
        public final long accessId;
        public final long accessTime;
        public MetaInfoAccess(String method, String variable, String type, long access, long time) {
            this.methodSig = method;
            this.variableName = variable;
            this.variableType = type;
            this.accessId = access;
            this.accessTime = time;
        }

        public MetaInfoAccess(MetaInfoAccess copy) {
            this(copy.methodSig, copy.variableName, copy.variableType, copy.accessId, copy.accessTime);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MetaInfoAccess that = (MetaInfoAccess) o;
            // ignore access time
            return methodSig.equals(that.methodSig) && variableName.equals(that.variableName)
                    && variableType.equals(that.variableType) && accessId == that.accessId;
        }

        @Override
        public int hashCode() {
            // ignore access time
            return Objects.hash(methodSig, variableName, variableType, accessId);
        }

        public String toString() {
            return "MetaInfoAccess{" + "method='" + methodSig + '\'' +
                    ", variable=" + variableName + ", id=" + accessId +  ", time=" +
                    accessTime + "}";
        }
    }

    /**
     * Signal that a particular server has finished initialization stage and is ready to process requests.
     * TODO: to be used
     *
     * @param serverId
     * @return
     * @throws RemoteException
     */
    boolean serverReady(int serverId) throws RemoteException;

    /**
     * Signal that a particular server has stopped (but not exited).
     * TODO: to be used
     *
     * @param serverId
     * @return
     * @throws RemoteException
     */
    boolean serverStopped(int serverId) throws RemoteException;
}
