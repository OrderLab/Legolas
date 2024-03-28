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

package edu.umich.order.legolas.hd_3_2_2;

import edu.umich.order.legolas.common.api.ClientStubFactory;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote.ClientFeedback;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrayHDFSClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(GrayHDFSClientMain.class);
    private static OrchestratorRemote stub = null;
    private static int clientId = -1;
    private static long t0 = System.nanoTime();

    public static void run(final String [] args) {
        LOG.info("running command " + Arrays.toString(args));
        final String confDir = args[0];
        final boolean isRead = args[1].equals("read");
        final String filename = args[2];
        final int num = Integer.parseInt(args[3]);
        int progress = 0;
        int failure = 0;
        while (progress < num) {
            try (final GrayHDFSClient client = new GrayHDFSClient(confDir)) {
                while (progress < num) {
                    final long nano = System.nanoTime();
                    if (isRead) {
                        client.readFile(filename);
                    } else {
                        client.writeFile(filename, 3_000_000);
                    }
                    progress++;
                    failure = 0;
                    LOG.info("progress = {}, time = {}", progress, System.nanoTime() - t0);
                    if (stub != null) {
                        try {
                            stub.send(new ClientFeedback(clientId, "success", System.nanoTime() - nano));
                        } catch (final RemoteException e) {
                            LOG.error("Fail to send the request result to orchestrator server", e);
                            System.exit(0);
                        }
                    }
                }
            } catch (final IOException e) {
                LOG.warn("Client encounter exception", e);
                failure++;
                if (failure >= 3) {
                    break;
                }
            }
        }
    }

    public static void main(final String[] args) {
        if (args.length == 1) {
            clientId = Integer.parseInt(args[0]);
            LOG.info("My client id is {}", clientId);
            final String name = ManagementFactory.getRuntimeMXBean().getName();
            final long pid = Long.parseLong(name.substring(0, name.indexOf('@')));
            LOG.info("My process's pid is {}", pid);
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