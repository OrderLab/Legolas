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
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class NewStateOpPolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(NewStateOpPolicy.class);

    protected volatile boolean injected = false;
    protected Set<Uid> records = new HashSet<>();

    public NewStateOpPolicy(final InjectionType injectionType) {
        super(injectionType);
    }

    @Override
    public void setupNewTrial() {
        LOG.info("state-op set size = {}", records.size());
        injected = false;
    }

    @Override
    public InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (injected) {
            return new InjectionRemoteCommand(0, -1, -1);
        }
        if (injectionType != InjectionType.EXCEPTION && request.delay) {
            final Uid uid = new Uid(request.serverId, request.stateMachineName, request.state,
                    -1, request.op);
            if (!records.contains(uid)) {
                records.add(uid);
                LOG.info("injected in " + uid);
                injected = true;
                return new InjectionRemoteCommand(1, -1, 0);
            }
        }
        if (injectionType != InjectionType.DELAY && request.eids.length > 0) {
            for (final int eid : request.eids) {
                final Uid uid = new Uid(request.serverId, request.stateMachineName,
                        request.state, eid, request.op);
                if (!records.contains(uid)) {
                    records.add(uid);
                    LOG.info("injected in " + uid);
                    injected = true;
                    return new InjectionRemoteCommand(0, eid, 0);
                }
            }
        }
        return new InjectionRemoteCommand(0, -1, -1);
    }
}