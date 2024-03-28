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
package edu.umich.order.legolas.injector.policy;

import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteCommand;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class ExhaustivePolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(ExhaustivePolicy.class);

    protected int trialId = 0;
    protected volatile boolean injected = false;

    // In an exhaustive policy, we do not simply check if an injection location has been exercised
    // before. For example, in the following snippet:
    //  for (int i = 0; i < 10; i++)
    //      foo(i);         <--- injection location
    //  we must exhaustively try injection 10 times!
    //
    // We use the first injection trial to collect all the injection sequences, such that
    // we know for a given injection location, how many times we should grant. This assumes
    // the execution is deterministic, which may not be true. But this is the simplest way
    // to determine the injection times for a location, which is otherwise hard to know on the fly.
    protected Map<Long, AtomicInteger> initialSequences = new HashMap<>();
    protected Map<Long, AtomicInteger> grantedSequences = new HashMap<>();
    public ExhaustivePolicy(final InjectionType injectionType) {
        super(injectionType);
    }

    @Override
    public void setupNewTrial() {
        LOG.info("initial sequence set size = {}, granted set size = {}", initialSequences.size(),
                grantedSequences.size());
        injected = false;
        trialId++;
    }

    @Override
    public InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (injected) {
            return new InjectionRemoteCommand(0, -1, -1);
        }
        long requestId = request.hashId();
        if (!initialSequences.containsKey(requestId)) {
            initialSequences.put(requestId, new AtomicInteger(0));
        }
        if (trialId == 1) {
            // Only collect the sequence id for each request id in the first trial
            initialSequences.get(requestId).incrementAndGet();
            return new InjectionRemoteCommand(0, -1, -1);
        }
        // Always not-null
        AtomicInteger maxSeq = initialSequences.get(requestId);
        if (maxSeq.get() == 0) {
            // We did not see this request in the first trial, it should have at least one budget
            maxSeq.incrementAndGet();
        }
        if (!grantedSequences.containsKey(requestId)) {
            grantedSequences.put(requestId, new AtomicInteger(0));
        }
        AtomicInteger grantedSeq = grantedSequences.get(requestId);
        InjectionRemoteCommand command = null;
        if (injectionType != InjectionType.DELAY && request.eids.length != 0) {
            if (grantedSeq.get() < maxSeq.get()) {
                grantedSeq.incrementAndGet();
                command = new InjectionRemoteCommand(0, request.eids[0], 0);
            }
        } else if (injectionType != InjectionType.EXCEPTION && request.delay) {
            if (grantedSeq.get() < maxSeq.get()) {
                grantedSeq.incrementAndGet();
                command = new InjectionRemoteCommand(1, -1, 0);
            }
        }
        if (command != null) {
            LOG.info("injected in {server={}, op={}, class={}, method={}, line={}}",
                request.serverId, request.op, request.className, request.methodName,
                request.lineNum);
            injected = true;
            return command;
        } else {
            return new InjectionRemoteCommand(0, -1, -1);
        }
    }
}
