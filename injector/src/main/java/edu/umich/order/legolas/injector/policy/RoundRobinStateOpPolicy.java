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

import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RoundRobinStateOpPolicy extends StateOpPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(RoundRobinStateOpPolicy.class);

    public RoundRobinStateOpPolicy(final InjectionType injectionType) {
        super(injectionType);
    }

    private final LinkedList<Uid> roundRobinList = new LinkedList<>();
    protected volatile int full = 0;
    private volatile int injectionNum = 0, trialNum = 0;

    @Override
    public void setupNewTrial() {
        trialNum++;
        if (injected) {
            injectionNum++;
        }
        if (trialNum > 30 && injectionNum < trialNum - injectionNum) {
            reset();
        }
        while (roundRobinList.size() > 0) {
            final Uid uid = roundRobinList.getFirst();
            roundRobinList.removeFirst();
            final UidInfo info = visited.get(uid);
            if (info == null || info.budget > 0) {
                roundRobinList.add(uid);
                break;
            }
        }
        while (roundRobinList.size() > 0 && visited.get(roundRobinList.getFirst()).budget == 0) {
            roundRobinList.removeFirst();
        }
        if (roundRobinList.size() > 0) {
            System.out.println("target at " + roundRobinList.getFirst());
        }
        super.setupNewTrial();
        LOG.info("round robin list length = " + roundRobinList.size());
    }

    @Override
    protected boolean check(final Uid uid) {
        if (!visited.containsKey(uid)) {
            visited.put(uid, new UidInfo());
            roundRobinList.add(uid);
        }
        if (roundRobinList.isEmpty()) {
            return false;
        }
        final UidInfo info = visited.get(uid);
        info.c++;
        if (roundRobinList.size() == 0 || !roundRobinList.getFirst().equals(uid)) {
            return false;
        }
        if (info.budget > 0 && rand.nextDouble() < info.prob) {
            info.budget--;
            if (info.budget == 0) full++;
            if (full == visited.size()) {
                reset();
            }
            return true;
        }
        return false;
    }

    protected void reset() {
        trialNum = 0;
        injectionNum = 0;
        visited.clear();
        roundRobinList.clear();
        full = 0;
        LOG.info("reset the budget");
    }
}
