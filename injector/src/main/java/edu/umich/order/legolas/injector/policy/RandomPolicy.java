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
import edu.umich.order.legolas.injector.policy.StateOpPolicy.Uid;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class RandomPolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(RandomPolicy.class);
    public static int rate = 100; // by default 1% probability

    protected volatile boolean injected = false;
    protected final Random rand = new Random();

    public RandomPolicy(final InjectionType injectionType) {
        super(injectionType);
        LOG.info("Using RandomPolicy: P = 1/{}", rate);
    }

    @Override
    public void setupNewTrial() {
        injected = false;
    }

    @Override
    public InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (injected) {
            return new InjectionRemoteCommand(0, -1, -1);
        }
        if ((injectionType != InjectionType.DELAY && request.eids.length != 0) ||
                (injectionType != InjectionType.EXCEPTION && request.delay)) {
            // FIXME: it's problematic, because now we're essentially assuming both types exist
            if (rand.nextInt(rate) == 0) {
                InjectionRemoteCommand command;
                if (injectionType == InjectionType.DELAY) {
                    command = new InjectionRemoteCommand(1, -1, 0);
                } else if (injectionType == InjectionType.EXCEPTION) {
                    final int eid = request.eids[rand.nextInt(request.eids.length)];
                    command = new InjectionRemoteCommand(0, eid, 0);
                } else {
                    final int choice = rand.nextInt(request.eids.length + 1);
                    if (choice < request.eids.length) {
                        command = new InjectionRemoteCommand(0, request.eids[choice], 0);
                    } else {
                        command = new InjectionRemoteCommand(1, -1, 0);
                    }
                }
                LOG.info("injected in " + new Uid(request.serverId, request.stateMachineName,
                            request.state, command.eid, request.op));
                injected = true;
                return command;
            }
        }
        return new InjectionRemoteCommand(0, -1, -1);
    }
}
