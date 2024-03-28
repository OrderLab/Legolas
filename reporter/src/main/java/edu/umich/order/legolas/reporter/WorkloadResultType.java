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
package edu.umich.order.legolas.reporter;

import edu.umich.order.legolas.reporter.ClientLog.ClientProgressType;
import edu.umich.order.legolas.reporter.Spec.Workload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Categorize the workload results into types based on the progress of clients and target servers.
 *
 * The result types are used to compute severity.
 */
public enum WorkloadResultType {
    COMPLETE(12),
    LOCAL_SINGLE_ZERO(11),
    LOCAL_SINGLE_PARTIAL(10),
    REMOTE_SINGLE_ZERO(9),
    REMOTE_SINGLE_PARTIAL(8),
    ALL_ZERO(7),
    LOCAL_ALL_ZERO(6),
    REMOTE_ALL_ZERO(5),
    LOCAL_MULTI_CLIENTS(4),
    REMOTE_MULTI_CLIENTS(3),
    LOCAL_MULTI_SERVERS(2),
    REMOTE_MULTI_SERVERS(1);

    private final int value;

    WorkloadResultType(int value) {
        this.value = value;
    }

    public static String getTypeMsg(final WorkloadResultType level) {
        switch (level) {
            case COMPLETE:
                return "no failure";
            case LOCAL_SINGLE_ZERO:
                return "single zero progress client in the server of injection";
            case LOCAL_SINGLE_PARTIAL:
                return "single partial progress client in the server of injection";
            case REMOTE_SINGLE_ZERO:
                return "single zero progress client in a server of no injection";
            case REMOTE_SINGLE_PARTIAL:
                return "single partial progress client in a server of no injection";
            case ALL_ZERO:
                return "zero progress for all clients";
            case LOCAL_ALL_ZERO:
                return "zero progress for all clients in the server of injection";
            case REMOTE_ALL_ZERO:
                return "zero progress for all clients in servers of no injection";
            case LOCAL_MULTI_CLIENTS:
                return "affect multiple clients in the server of injection";
            case REMOTE_MULTI_CLIENTS:
                return "affect multiple clients in a server of no injection";
            case LOCAL_MULTI_SERVERS:
                return "affect multiple clients in the server of injection and some other servers";
            case REMOTE_MULTI_SERVERS:
                return "affect multiple clients in multiple servers of no injection";
            default:
                throw new RuntimeException("Invalid suspicious level");
        }
    }

    /**
     * Compute the workload result type based on client progress and injected server
     *
     * @param clients
     * @param workload
     * @param injectedServer
     * @return
     */
    public static WorkloadResultType computeResultType(ClientLog[] clients, Workload workload,
            int injectedServer) {
        int failure_server = -1; // any server; must be target server if applicable
        final List<Integer> failureServers = new ArrayList<>();
        int localTotal = 0, remoteTotal = 0, localZero = 0, localPartial = 0,
                remoteZero = 0, remotePartial = 0;
        for (int i = 0; i < clients.length; i++) {
            final ClientLog client = clients[i];
            final int server = workload.target[i];
            ClientProgressType progressType = client.getProgressType(workload.progress[i]);
            if (server == injectedServer) {
                localTotal++;
                if (progressType == ClientProgressType.ZERO)
                    localZero++;
                else if (progressType == ClientProgressType.PARTIAL)
                    localPartial++;
            } else {
                remoteTotal++;
                if (progressType == ClientProgressType.ZERO)
                    remoteZero++;
                else if (progressType == ClientProgressType.PARTIAL)
                    remotePartial++;
            }
            if (progressType == ClientProgressType.COMPLETE) {
                continue;
            }
            failureServers.add(server);
            if (failure_server == -1 || injectedServer == workload.target[i]) {
                failure_server = workload.target[i];
            }
        }
        if (failureServers.isEmpty())
            return WorkloadResultType.COMPLETE;
        if (failureServers.size() == 1) {
            if (localZero == 1)
                return WorkloadResultType.LOCAL_SINGLE_ZERO;
            if (localPartial == 1)
                return WorkloadResultType.LOCAL_SINGLE_PARTIAL;
            if (remoteZero == 1)
                return WorkloadResultType.REMOTE_SINGLE_ZERO;
            if (remotePartial == 1)
                return WorkloadResultType.REMOTE_SINGLE_PARTIAL;
            throw new RuntimeException("Single failed client, but no client has zero or partial progress");
        }
        if (remoteTotal > 0 && remoteZero == remoteTotal) {
            if (localTotal > 0 && localZero == localTotal)
                return WorkloadResultType.ALL_ZERO;
            return WorkloadResultType.REMOTE_ALL_ZERO;
        }
        if (localTotal > 0 && localZero == localTotal)
            return WorkloadResultType.LOCAL_ALL_ZERO;
        if (Collections.frequency(failureServers, failure_server) == failureServers.size()) {
            if (failure_server == injectedServer) {
                return WorkloadResultType.LOCAL_MULTI_CLIENTS;
            } else {
                return WorkloadResultType.REMOTE_MULTI_CLIENTS;
            }
        } else {
            if (failure_server == injectedServer) {
                return WorkloadResultType.LOCAL_MULTI_SERVERS;
            } else {
                return WorkloadResultType.REMOTE_MULTI_SERVERS;
            }
        }
    }

    public int getValue() {
        return value;
    }
}
