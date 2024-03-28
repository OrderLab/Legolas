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
package edu.umich.order.legolas.common.asm;

import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteQuery;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;
import edu.umich.order.legolas.common.event.ThreadStateEvent;
import java.util.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract state machine for each major class of the System Under Analysis. The ASM derives
 * its states from the concrete state variables in the target class.
 * TODO: add transition, and decouple the stack state
 */
public final class AbstractStateMachine {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateMachine.class);

    public final int serverId;
    public final int instanceId;

    private final Stack<String> stackStateMachineNames;
    private final Stack<AbstractState> stackStates;

    public AbstractState currentState;
    private String stateMachineName;

    public AbstractStateMachine(int serverId, String stateMachineName, int instanceId) {
        this.serverId = serverId;
        this.instanceId = instanceId;
        this.stackStateMachineNames = new Stack<>();
        this.stackStates = new Stack<>();
        this.currentState = null;
        this.stateMachineName = stateMachineName;
    }

    public final synchronized void register(final String className) {
        stackStateMachineNames.push(stateMachineName);
        stateMachineName = className;
        stackStates.push(currentState);
    }

    public final synchronized void unregister() {
        if (stackStateMachineNames.empty()) {
            // this instance disappears and it will not be used anymore.
            return;
        }
        stateMachineName = stackStateMachineNames.peek();
        stackStateMachineNames.pop();
        currentState = stackStates.peek();
        stackStates.pop();
    }

    public final synchronized ThreadStateEvent update(AbstractState state,
            String threadName) {
        currentState = state;
        return new ThreadStateEvent(System.nanoTime(), serverId, threadName,
            instanceId, stateMachineName, currentState);
    }

    public final synchronized ThreadInjectionRequest createInjectionRequest(
            InjectionRemoteQuery query) {
        ThreadInjectionRequest req = new ThreadInjectionRequest(query);
        req.instanceId = instanceId;
        req.state = currentState;
        req.stateMachineName = stateMachineName;
        return req;
    }
}
