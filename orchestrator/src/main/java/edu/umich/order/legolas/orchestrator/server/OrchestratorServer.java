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
package edu.umich.order.legolas.orchestrator.server;

import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.fault.ExceptionTableParser;
import edu.umich.order.legolas.common.record.OrchestratorStats;
import edu.umich.order.legolas.common.server.RmiServerBase;
import edu.umich.order.legolas.common.util.Mutex;
import edu.umich.order.legolas.orchestrator.workload.Workload;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The legolas orchestrator server
 */
public class OrchestratorServer extends RmiServerBase implements OrchestratorRemote {
    private static final Logger LOG = LoggerFactory.getLogger(OrchestratorServer.class);

    private final BlockingQueue<Long> signalQueue = new LinkedBlockingDeque<>();
    private volatile int currentSid = -1;
    private final Mutex mutex;
    private final String[] exceptionNames;
    private Workload workload = null;

    public OrchestratorServer(Properties properties, Mutex mutex,
            int port, Registry registry, boolean tryCreateReg) throws RemoteException {
        super(OrchestratorRemote.REMOTE_NAME, port, "OrchestratorServer", registry, tryCreateReg);
        this.mutex = mutex;
        final String exceptionTablePath = properties.getProperty("exceptionTableFilePath");
        this.exceptionNames = ExceptionTableParser.parse(exceptionTablePath);
    }

    public void initStats(final OrchestratorStats stats) {
        stats.init(exceptionNames);
    }

    @Override
    public RegistryRemoteInfo register(long pid) throws RemoteException {
        LOG.info("Received registration request from pid " + pid);
        synchronized (signalQueue) {
            signalQueue.clear();
            signalQueue.add(pid);
            final int result = currentSid;
            // sometime currentSid will be used twice (e.g., in HBase HMaster)
            // currentSid = -1;
            return new RegistryRemoteInfo(result, exceptionNames);
        }
    }

    public final void setWorkload(final Workload workload) {
        this.workload = workload;
    }

    @Override
    public final synchronized ClientFeedbackResponse send(final ClientFeedback feedback) throws RemoteException {
        workload.proceed(feedback.clientId);
        return new ClientFeedbackResponse(1);
    }

    @Override
    public final synchronized String[] registerClient(final int clientId, final long pid) throws RemoteException {
        return workload.registerClient(clientId, pid);
    }

    public void prepareSid(final int sid) {
        currentSid = sid;
    }

    /**
     * Get the most recent pid
     *
     * @return
     */
    public long getRecentPid() {
        try {
            final Long result = signalQueue.poll(10, TimeUnit.SECONDS);
            //final Long result = signalQueue.poll();
            if (result == null) {
                return -1;
            }
            return result;
        } catch (final InterruptedException e) {
            return -1;
        }
    }
}
