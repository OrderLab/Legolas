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
 * The RPC interface for fault injection controller
 */
public interface FaultInjectorRemote extends Remote {
    String REMOTE_NAME = "LegolasFaultInjector";
    int REMOTE_PORT = 1099;

    /**
     * Request to inject some exceptions to a server and a class instance.
     *
     * @param query
     * @return
     * @throws RemoteException
     */
    InjectionRemoteCommand inject(InjectionRemoteQuery query) throws RemoteException;

    final class InjectionLocation implements Serializable {
        public final String className;
        public final String methodName;
        public final int lineNum;
        public final String op;
        public final long stackTraceId;
        public final long failureId; // only used when evaluating FATE (NSDI '11)

        public InjectionLocation(String className, String methodName, int lineNum,
                String op, long stackTraceId, long failureId) {
            this.className = className;
            this.methodName = methodName;
            this.lineNum = lineNum;
            this.op = op;
            this.stackTraceId = stackTraceId;
            this.failureId = failureId;
        }
    }

    final class InjectionRemoteQuery implements Serializable {
        public final int serverId;
        public final String threadName;
        public final int threadId;

        public final InjectionLocation location;

        public final int delay; // 0 or 1
        public final int[] exceptionIds;

        public InjectionRemoteQuery(int serverId, String threadName,
                int threadId, InjectionLocation location, int delay, 
                int[] exceptionIds) {
            this.serverId = serverId;
            this.threadName = threadName;
            this.threadId = threadId;
            this.location = location;
            this.delay = delay;
            this.exceptionIds = exceptionIds;
        }
    }

    final class InjectionRemoteCommand implements Serializable {
        public final int delay; // 0 or 1
        public final int eid;
        public final int id;

        public InjectionRemoteCommand(int delay, int eid, int id) {
            this.delay = delay;
            this.eid = eid;
            this.id = id;
        }
    }
}
