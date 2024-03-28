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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AttachedClientWorkload extends ClientWorkload {
    private static final Logger LOG = LoggerFactory.getLogger(AttachedClientWorkload.class);

    public AttachedClientWorkload(final Orchestrator orch, final int clientId, final int expected) {
        super(orch, clientId, null, expected);
    }

    @Override
    public void notifyPid(final long pid) {
        LOG.error("AttachedClientWorkload should not receive process id {}", pid);
        throw new RuntimeException("AttachedClientWorkload should not receive process id");
    }

    @Override
    public void waitForStartup(long timeoutMS) { }

    @Override
    public void shutdown() { }

    @Override
    public void run() { }
}
