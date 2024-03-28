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

import edu.umich.order.legolas.common.api.AbstractStateServerRemote.StateUpdateRemoteInfo;
import edu.umich.order.legolas.common.event.ThreadStateEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage all the abstract state machines in a target system.
 *
 * The analyzer will instrument hooks in the target system to invoke the *static* APIs of the manager,
 * e.g., informing the manager that in some class the abstract state has changed.
 *
 * Note that the manager is a data structure encapsulation for all the ASMs in a system; the manager
 * itself is *not* a dynamic entity. It will be used by the other dynamic entities like an agent
 * that interacts with an external controller or the orchestrator server.
 */
public final class AbstractStateMachineManager {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateMachineManager.class);
    private static final DateFormat PLOT_DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private final Map<Integer, AbstractStateMachine> statesMachines; // instance -> asm
    private final Map<Integer, Stack<Integer>> thread2instance; // thread -> instance

    public final int serverId;

    public AbstractStateMachineManager(final int serverId) {
        statesMachines = new HashMap<>();
        thread2instance = new HashMap<>();
        this.serverId = serverId;
        final AbstractStateMachine dummy = new AbstractStateMachine(
                serverId, "DummyASM", -1);
        statesMachines.put(-1, dummy);
        dummy.currentState = new AbstractState("dummy", 1);
    }

    /**
     * Return the ASM for the instance id.
     *
     * @param instanceId
     * @return
     */
    public final synchronized AbstractStateMachine getAsmByInstanceId(final int instanceId) {
        if (statesMachines.containsKey(instanceId)) {
            return statesMachines.get(instanceId);
        }
        return null;
    }

    public final synchronized int getInstanceIdByThreadId(final int threadId) {
        if (thread2instance.containsKey(threadId)) {
            final Stack<Integer> instances = thread2instance.get(threadId);
            if (instances.empty()) {
                return -1;
            }
            return instances.peek();
        }
        return -1;
    }

    /**
     * Update the state in the associated ASM
     *
     * Thread-based state machine:
     * info.state.id=0 => register
     * info.state.id=-1 => unregister
     * info.state.id=others => normal state
     *
     * @param info
     */
    public final synchronized ThreadStateEvent update(final StateUpdateRemoteInfo info) {
        int instanceId = info.instanceId;
        if (info.state.id == 0) {
            Stack<Integer> instances;
            if (thread2instance.containsKey(info.threadId)) {
                instances = thread2instance.get(info.threadId);
            } else {
                instances = new Stack<>();
                thread2instance.put(info.threadId, instances);
            }
            instances.push(info.instanceId);
        } else if (info.state.id == -1) {
            thread2instance.get(info.threadId).pop();
        } else {
            instanceId = thread2instance.get(info.threadId).peek();
        }
        AbstractStateMachine asm;
        if (statesMachines.containsKey(instanceId)) {
            asm = statesMachines.get(instanceId);
            if (info.state.id == 0) {
                asm.register(info.className);
            }
        } else {
            asm = new AbstractStateMachine(serverId, info.className, instanceId);
            statesMachines.put(instanceId, asm);
        }
        final ThreadStateEvent result = asm.update(info.state, info.threadName);
        if (info.state.id == -1) {
            asm.unregister();
        }
        return result;
    }
}
