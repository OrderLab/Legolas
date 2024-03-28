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
package edu.umich.order.legolas.fl_1_14_0;

import edu.umich.order.legolas.common.api.ClientStubFactory;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote.ClientFeedback;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlinkGrayClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkGrayClientMain.class);

    public static OrchestratorRemote stub = null;

    public static int clientId = -1;

    public static int clientProgress = 0;
    public static long t0 = System.nanoTime();

    public static void reply(final String result, final long nano) {
        LOG.info("progress = {}, time = {}", ++clientProgress, System.nanoTime() - t0);
        if (stub != null) {
            try {
                stub.send(new ClientFeedback(clientId, result, System.nanoTime() - nano));
            } catch (final RemoteException e) {
                LOG.error("Fail to send the request result to orchestrator server", e);
                System.exit(0);
            }
        }
    }

    private static void run(final String[] args) throws Exception {
        LOG.info("running command " + Arrays.toString(args));
        final String command = args[0];
        final String[] cmd = new String[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            cmd[i - 1] = args[i];
        }
        switch (command) {
            case "streaming":
                FlinkGrayStreamingClientMain.run(cmd);
                break;
            case "batch":
                FlinkGrayBatchClientMain.run(cmd);
                break;
            default:
                LOG.error("undefine type of workload -- " + command);
        }
    }

    public static void main(final String[] args) throws Exception {
        final String name = ManagementFactory.getRuntimeMXBean().getName();
        final long pid = Long.parseLong(name.substring(0, name.indexOf('@')));
        LOG.info("My process's pid is {}", pid);
        if (args.length == 1) {
            clientId = Integer.parseInt(args[0]);
            LOG.info("My client id is {}", clientId);
            stub = ClientStubFactory.getOrchestratorStub(1099);
            if (stub == null) {
                LOG.error("Failed to get a client for orchestrator server");
            } else {
                try {
                    final String[] command = stub.registerClient(clientId, pid);
                    run(command);
                } catch (final RemoteException e) {
                    LOG.error("Failed to register with the orchestrator server");
                }
            }
        } else {
            run(args);
        }
    }
}