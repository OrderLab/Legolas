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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class StateOpPolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(StateOpPolicy.class);

    protected volatile boolean injected = false;
    protected final Random rand = new Random();

    public StateOpPolicy(final InjectionType injectionType) {
        super(injectionType);
    }

    protected static final class UidInfo {
        public volatile int c = 0;
        public volatile double prob = 0.0; // FIXME
        public volatile int budget = 5; // FIXME
    }

    protected final Map<Uid, UidInfo> visited = new HashMap<>();

    @Override
    public void setupNewTrial() {
        injected = false;
        for (final Map.Entry<Uid, UidInfo> entry : visited.entrySet()) {
            final UidInfo info = entry.getValue();
            final Uid uid = entry.getKey();
            final double p = 1 - Math.exp(Math.log(0.01)/(info.c + 1));
            info.c = 0;
            if (info.prob < 1e-6) info.prob = p;
            else info.prob = Math.min(info.prob, p);
        }
    }

    @Override
    public InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (injected) {
            return new InjectionRemoteCommand(0, -1, -1);
        }
        if (injectionType != InjectionType.DELAY) {
            for (final int eid : request.eids) {
                final Uid uid = new Uid(request.serverId, request.stateMachineName, request.state, eid, request.op);
                if (check(uid)) {
                    injected = true;
                    LOG.info("injected in " + uid + " with probability " + visited.get(uid).prob);
                    return new InjectionRemoteCommand(0, eid, 0);
                }
            }
        }
        if (injectionType != InjectionType.EXCEPTION) {
            final Uid uid = new Uid(request.serverId, request.stateMachineName, request.state, -1, request.op);
            if (check(uid)) {
                injected = true;
                LOG.info("injected in " + uid + " with probability " + visited.get(uid).prob);
                return new InjectionRemoteCommand(1, -1, 0);
            }
        }
        return new InjectionRemoteCommand(0, -1, -1);
    }

    protected boolean check(final Uid uid) {
        if (!visited.containsKey(uid)) {
            visited.put(uid, new UidInfo());
//            LOG.debug("Uid {} has not been visited", uid);
        }
        final UidInfo info = visited.get(uid);
//        LOG.debug("Budget for Uid {} is {}", uid, info.budget);
        info.c++;
        double p = rand.nextDouble();
        if (info.budget > 0 && p < info.prob) {
//            LOG.debug("Roll {} passes threshold {}", p, info.prob);
            info.budget--;
            return true;
        } else {
//            LOG.debug("Roll {} does not pass threshold {}", p, info.prob);
        }
        return false;
    }

    protected static final class Uid {
        final int server;
        final String name;
        final AbstractState state;
        final int eid;
        final String op;

        public Uid(int server, String name, AbstractState state, int eid, String op) {
            this.server = server;
            this.name = name;
            this.state = state;
            this.eid = eid;
            this.op = op;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Uid uid = (Uid) o;
            return server == uid.server && eid == uid.eid && Objects.equals(name, uid.name)
                    && Objects.equals(state, uid.state) && Objects.equals(op, uid.op);
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, name, state, eid, op);
        }

        @Override
        public String toString() {
            return "Uid{" +
                    "server=" + server +
                    ", name='" + name + '\'' +
                    ", state=" + state +
                    ", eid=" + eid +
                    ", op='" + op + '\'' +
                    '}';
        }
    }
}
