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
package edu.umich.order.legolas.orchestrator.system;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class LogMonitor extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(LogMonitor.class);

    public static final String INJECTION_LOG_ENTRY = "LegolasAgent injecting";

    protected final ServerNode serverNode;

    private final File file;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public LogMonitor(final ServerNode serverNode) {
        this.serverNode = serverNode;
        final String pathName = serverNode.getLogFilePathName();
        this.file = new File(pathName);
        // timeout: 10s
        for (int retries = 0; retries < 100; retries++) {
            try {
                if (file.exists()) {
                    break;
                }
                Thread.sleep(100); // warm up for 100 to tolerate the late log file
            } catch (InterruptedException ignored) {}
        }
        if (!file.exists()) {
            LOG.error("Log file " + pathName + " does not exist");
        }
    }

    public final void shutdown() {
        if (started.get()) {
            started.set(false);
        }
    }

    abstract protected void handle(final String line);

    @Override
    public final void run() {
        started.set(true);
        LOG.info("LogMonitor for ServerNode {} started to watch {}",
            serverNode.serverId, file);
        try {
            // wait for 3 more seconds if log file has not appeared yet
            for (int retries = 0; retries < 30; retries++) {
                if (file.exists())
                    break;
                Thread.sleep(100);
            }
            final BufferedReader br = new BufferedReader(new FileReader(file));
            while (started.get()) {
                final String line = br.readLine();
                if (line == null) {
                    Thread.sleep(10); // TODO: make it configurable
                } else {
                    if (line.contains(INJECTION_LOG_ENTRY)) {
                        serverNode.setInjected();
                    }
                    handle(line);
                }
            }
        } catch (Exception e) {
            LOG.error("Exception in the log monitor thread of server " + serverNode.serverId + " due to ", e);
        }
    }
}
