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
package edu.umich.order.legolas.orchestrator.workload;

import edu.umich.order.legolas.orchestrator.Orchestrator;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ClientWorkload extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(ClientWorkload.class);

    public final int clientId;
    public final String[] command;

    private long pid = -1;
    private Process process = null;

    protected final Orchestrator orch;
    protected final int expected; // requests: [0, expected), where expected must be greater than 0
    protected int progress = 0;

    private final CountDownLatch signal = new CountDownLatch(1);

    public void notifyPid(final long pid) {
        this.pid = pid;
        LOG.info("client {} started with pid {}", clientId, pid);
        logger = new Thread(() -> {
            boolean stopped = false;
            final char[] chars = new char[256];
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                try (final FileWriter writer = new FileWriter(orch.getTrialDir() + "/client-" + clientId + ".out")) {
                    while (true) {
                        final int num = in.read(chars);
                        if (num <= 0) {
                            if (interrupted() || stopped) {
                                break;
                            }
                            try {
                                Thread.sleep(10);
                            } catch (final InterruptedException ignored) {
                                stopped = true;
                            }
                        } else {
                            writer.write(chars, 0, num);
                        }
                    }
                }
            } catch (final IOException e) {
                LOG.error("Encounter exception in client log writer", e);
            }
        });
        logger.start();
        signal.countDown();
    }

    private Thread logger = null;

    public void waitForStartup(long timeoutMS) throws InterruptedException {
        if (!signal.await(timeoutMS, TimeUnit.MILLISECONDS)) {
            LOG.warn("Timed out in waiting for client startup");
        }
    }

    public void proceed() {
//        LOG.info("Client {} proceeds", clientId);
        progress++;
    }

    public final boolean isFinished() {
        return progress == expected;
    }

    public String getResult() {
        return "" + progress + "/" + expected;
    }

    public ClientWorkload(final Orchestrator orch, final int clientId, final String[] command,
            final int expected) {
        this.orch = orch;
        this.clientId = clientId;
        this.command = command;
        this.expected = expected;
    }

    /*
     * must not be synchronized
     */
    public void shutdown() throws Exception {
        if (process.isAlive()) {
            try {
                if (pid > 0) {
                    Runtime.getRuntime().exec("kill -9 " + pid).waitFor();
                } else {
                    // if pid is not set, directly call process API to kill it
                    process.destroyForcibly();
                }
            } catch (final Exception e) {
                throw new Exception("possibly fail to kill client " + clientId);
            }
        }
        if (logger != null) {
            logger.interrupt();
            logger.join();
        }
    }

    public String clientScriptName() {
        return "client.sh";
    }

    public String clientScriptArgs() {
        return String.valueOf(clientId);
    }

    public void onClientStarted(Process process) {}
    public void onClientFinish() {}

    @Override
    public void run() {
        final ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", orch.workspacePathName + "/" + clientScriptName(), clientScriptArgs());
        pb.redirectErrorStream(true);
        try {
            process = pb.start();
            onClientStarted(process);
            process.waitFor();
        } catch (final Exception e) {
            LOG.warn("Encounter exception in client control ", e);
            return;
        }
//        LOG.info("Client {} ends", clientId);
        onClientFinish();
    }
}
