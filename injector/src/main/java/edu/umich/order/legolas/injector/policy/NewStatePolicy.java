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
import edu.umich.order.legolas.common.asm.AbstractState;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;
import edu.umich.order.legolas.injector.policy.StateOpPolicy.Uid;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class NewStatePolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(NewStatePolicy.class);

    protected volatile boolean injected = false;
    protected Set<State> records = new HashSet<>();

    public NewStatePolicy(final InjectionType injectionType) {
        super(injectionType);
    }

    @Override
    public void setupNewTrial() {
        LOG.info("state set size = {}", records.size());
        injected = false;
    }

    @Override
    public InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (injected) {
            return new InjectionRemoteCommand(0, -1, -1);
        }
        if (injectionType != InjectionType.EXCEPTION && request.delay) {
            final State state = new State(request.serverId, request.stateMachineName, request.state, -1);
            if (!records.contains(state)) {
                records.add(state);
                LOG.info("injected in " + new Uid(request.serverId, request.stateMachineName,
                        request.state, -1, request.op));
                injected = true;
                return new InjectionRemoteCommand(1, -1, 0);
            }
        }
        if (injectionType != InjectionType.DELAY && request.eids.length > 0) {
            for (final int eid : request.eids) {
                final State state = new State(request.serverId, request.stateMachineName, request.state, eid);
                if (!records.contains(state)) {
                    records.add(state);
                    LOG.info("injected in " + new Uid(request.serverId, request.stateMachineName,
                            request.state, eid, request.op));
                    injected = true;
                    return new InjectionRemoteCommand(0, eid, 0);
                }
            }
        }
        return new InjectionRemoteCommand(0, -1, -1);
    }

    protected static final class State {
        final int server;
        final String name;
        final AbstractState state;
        final int eid;

        public State(int server, String name, AbstractState state, int eid) {
            this.server = server;
            this.name = name;
            this.state = state;
            this.eid = eid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            State state1 = (State) o;
            return server == state1.server && eid == state1.eid && Objects
                    .equals(name, state1.name) && Objects.equals(state, state1.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, name, state, eid);
        }
    }
}
