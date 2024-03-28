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
package edu.umich.order.legolas.injector.controller;

import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteCommand;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;
import edu.umich.order.legolas.common.fault.InjectionPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injection controller
 */
public class InjectionController {
    private static final Logger LOG = LoggerFactory.getLogger(InjectionController.class);
    protected final InjectionPolicy policy;

    protected int maxTrials;
    protected int trialId;
    private AtomicBoolean ready = new AtomicBoolean(false);

    public InjectionController(final InjectionPolicy policy) {
        this.policy = policy;
        trialId = -1;
    }

    public InjectionController(final InjectionPolicy policy, int maxTrials) {
        this.policy = policy;
        trialId = -1;
        this.maxTrials = maxTrials;
        LOG.info("# of trials = {}", maxTrials);
    }

    public final InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (!ready.get()) {
            return new InjectionRemoteCommand(0,  -1, -1);
        }
        return policy.inject(request);
    }

    public final void setReady() {
        ready.set(true);
    }

    public final int getTrialId() {
        return trialId;
    }

    public boolean hasNextTrial() {
        // if maxTrials is zero or negative, it means endless; trialId starts from 0
        return maxTrials <= 0 || trialId + 1 < maxTrials;
    }

    /**
     * Do preparation work for a new trial. Normally, this would increment the global trial id,
     * but in the scenario we are retrying a failed trial, incrementId should be set to false
     *
     * @param incrementId: true - we should increment the trial id; false - keep the current id.
     */
    public final void setupNewTrial(boolean incrementId) {
        ready.set(false);
        if (incrementId)
            trialId++;
        policy.setupNewTrial();
    }
}
