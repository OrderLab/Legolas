/*
 *  @author Ryan Huang <ryanph@umich.edu>
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
package edu.umich.order.legolas.common.agent;

import edu.umich.order.legolas.common.api.AbstractStateServerRemote;
import edu.umich.order.legolas.common.api.AbstractStateServerRemote.MetaInfoAccess;
import edu.umich.order.legolas.common.api.AbstractStateServerRemote.MetaInfoAccessRemoteInfo;
import edu.umich.order.legolas.common.api.AbstractStateServerRemote.StateUpdateRemoteInfo;
import edu.umich.order.legolas.common.api.ClientStubFactory;
import edu.umich.order.legolas.common.api.FaultInjectorRemote;
import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionLocation;
import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteCommand;
import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteQuery;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote.RegistryRemoteInfo;
import edu.umich.order.legolas.common.asm.AbstractState;
import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import edu.umich.order.legolas.common.fault.ExceptionTable;
import edu.umich.order.legolas.common.fault.ExceptionTableParser;
import edu.umich.order.legolas.common.fault.InjectionManager;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main Legolas agent that will execute inside a target system to manage the ASM, fault
 * injection and orchestration related tasks.
 *
 * <p>
 * An agent could provide both internal-facing interfaces and external-facing interfaces.
 *
 * The internal-facing interfaces are meant for the internal usage by the target system code.
 * The Legolas analyzer will instrument hooks in the target system to <i>statically</i> invoke these
 * interfaces, e.g., inform() informing a module entering an abstract state. Such APIs can be safely
 * invoked in any place, even when the Legolas agent thread has not been created.
 *
 * The external-facing interfaces are ones that the agent could provide to external servers,
 * e.g., enable/disable an ASM. The will be used by either operator or the orchestrator.
 * </p>
 *
 * <p>
 * Besides the interfaces, the Legolas agent manages dynamic aspects, such as starting the agent thread,
 * handling query command, using client stub to interact with the orchestrator, etc.
 * </p>
 */
public final class LegolasAgent {
    private static final Logger LOG = LoggerFactory.getLogger(LegolasAgent.class);

    private static final int delayMillis = 60 * 1000; // 1 min

    // process id of the target system
    private static final long pid;

    // the server id of the current process
    // currently only assigned once
    private static int serverId = -1;

    private static final LegolasAgentConfig config;

    private static final ExceptionTable exceptionTable;

    // the global substrate (only applicable for a stateful agent)
    // TODO: to be used
    private static LegolasAgentSubstrate global_substrate = null;

    private static final boolean META_INFO_MODE = 
      Boolean.parseBoolean(System.getProperty("metainfo_mode", "false")); // approximate Meta-Info (SOSP '19)
    private static final boolean FATE_MODE = 
      Boolean.parseBoolean(System.getProperty("fate_mode", "false")); // approximate FATE (NSDI '11)

    // FIXME: it'd be better if it's a static method rather than static block
    static {
        final String name = ManagementFactory.getRuntimeMXBean().getName();
        pid = Long.parseLong(name.substring(0, name.indexOf('@')));
        LOG.info("My host process's pid is " + pid);
        final String configPath = "##";
        config = new LegolasAgentConfig(configPath);
        String[] exceptionNames = null;
        if (config.localMode) {
            LOG.info("Starting LegolasAgent in pure local mode");
            exceptionNames = ExceptionTableParser.parse(config.exceptionTablePath);
        } else {
            LOG.info("Trying to register with the orchestrator");
            final OrchestratorRemote stub = ClientStubFactory.getOrchestratorStub(config.rmiPort);
            if (stub == null) {
                LOG.error("Failed to get a client for orchestrator server");
            }
            try {
                final RegistryRemoteInfo info = stub.register(pid);
                serverId = info.serverId;
                exceptionNames = info.exceptionNames;
                LOG.info("Registered LegolasAgent with the orchestrator server and get server id " + serverId);
            } catch (RemoteException e) {
                LOG.error("Failed to register with the orchestrator server");
            }
        }
        exceptionTable = new ExceptionTable(exceptionNames);
    }

    private static LegolasAgentSubstrate getSubstrate() {
        // TODO: refactor
        if (global_substrate == null) {
            AbstractStateMachineManager asmm = new AbstractStateMachineManager(serverId);
            InjectionManager im = new InjectionManager(asmm);
            global_substrate = new LegolasAgentSubstrate(asmm, im);
        }
        return global_substrate;
    }

    /**
     * Invoked by the hooks in a module of the target system to notify that this module has entered
     * certain state.
     *
     * This information will be recorded and used by the fault injector controller and orchestrator.
     *
     * @param className
     * @param instanceId
     * @param methodSig
     * @param stateId
     */
    public static boolean informState(String className, int instanceId, String methodSig, int stateId) {
        if (FATE_MODE || META_INFO_MODE) {
            // Not applicable for FATE or Meta-Info
            return true;
        }
        // Here we are going to use the currentThread() to calculate the identity hash code instead
        // of using the instanceId (which is identityHashCode of *this*). The reason is that some
        // state classes may be a runnable and then the runnable is used in a new thread so the
        // this will refer to the runnable but not the thread. Something like below:
        //
        // Runnable rb = new MyRunnable();
        // Thread thd = new Thread(rb);
        // thd.start();
        //
        final Thread current = Thread.currentThread();
        final int threadId = System.identityHashCode(current);
        switch (config.agentType) {
            case STATEFUL: {
                LegolasAgentSubstrate substrate = getSubstrate();
                //substrate.asmManager.update(className, instanceId, threadId, stateId, isRun, true);
                // TODO: in addition to update the substrate, we may also need to inform the server
                //  under certain condition
                return true;
            }
            case STATELESS: {
                AbstractStateServerRemote stub = ClientStubFactory.getStateServerStub(config.rmiPort);
                if (stub == null) {
                    LOG.error("Failed to get a client stub for abstract server");
                    return false;
                }
                final StateUpdateRemoteInfo info = new StateUpdateRemoteInfo(
                        serverId, className, instanceId, current.getName(),
                        threadId, new AbstractState(methodSig, stateId));
                try {
                    return stub.informState(info);
                } catch (RemoteException e) {
                    LOG.error("Failed to call rmi inform", e);
                    return false;
                }
            }
            default:
                LOG.error("Unsupported agent type " + config.agentType);
                return false;
        }
    }

    public static boolean informAccess(String className, int instanceId, String methodSig,
            String variable, String type, long accessId) {
        if (!META_INFO_MODE) {
            // Not applicable is not in meta-info mode
            return true;
        }
        final Thread current = Thread.currentThread();
        final int threadId = System.identityHashCode(current);
        final long now = System.currentTimeMillis();
        switch (config.agentType) {
            case STATEFUL: {
                return true;
            }
            case STATELESS: {
                AbstractStateServerRemote stub = ClientStubFactory.getStateServerStub(config.rmiPort);
                if (stub == null) {
                    LOG.error("Failed to get a client stub for abstract server");
                    return false;
                }
                final MetaInfoAccessRemoteInfo info = new MetaInfoAccessRemoteInfo(serverId,
                        className, instanceId, current.getName(), threadId,
                        new MetaInfoAccess(methodSig, variable,
                        type, accessId, now));
                try {
                    return stub.informAccess(info);
                } catch (RemoteException e) {
                    LOG.error("Failed to call rmi inform", e);
                    return false;
                }
            }
            default: {
                LOG.error("Unsupported agent type " + config.agentType);
                return false;
            }
        }
    }

    // TODO: to be used
//    private static final ConcurrentMap<Long, Integer> thread2injectionId = new ConcurrentHashMap<>();
//    private static final ConcurrentMap<Long, Integer> thread2stack = new ConcurrentHashMap<>();

    // TODO: to be used
    public static void handleSerializer(int d) {
//        long id = Thread.currentThread().getId();
//        Integer stack = thread2stack.get(id);
//        if (stack != null)
//            d += stack;
//        thread2stack.put(id, d);
    }

    /**
     * Invoked by the hooks in a module of the target system to intercept an operation and decide
     * whether some fault should be injected.
     *
     * @param delay
     * @param exceptionIds
     * @param className
     * @param methodName
     * @param lineNum
     * @param invokedMethodSig
     * @param id
     * @throws Throwable
     */
    public static void inject(int delay, int[] exceptionIds, String className,
            String methodName, int lineNum, String invokedMethodSig, int id) throws Throwable {
        final Thread current = Thread.currentThread();
        final int threadId = System.identityHashCode(current);
        String stackTrace = "";
        long failureId = -1;
        if (!META_INFO_MODE) {
            stackTrace = getStackTrace(current);
        }
        if (FATE_MODE) {
            failureId = className.hashCode() * 3L + methodName.hashCode() * 5L +
                    lineNum + invokedMethodSig.hashCode() * 7L + stackTrace.hashCode() * 9L;
        }
        // TODO: implement Serializer
//        Integer stack = thread2stack.get(threadId);
//        if (stack != null && stack > 0)
//            return;
        // TODO: implement repetition avoidance
//        Integer previousId = thread2injectionId.get(threadId);
//        if (previousId != null && previousId == id)
//            return;
//        thread2injectionId.put(threadId, id);
        switch (config.agentType) {
            case STATEFUL: {
                // LegolasAgentSubstrate substrate = getSubstrate();
                //substrate.injectionManager.recordInjection(instanceId, (delay != 0), exceptionIds, info);
                // TODO: implement local injection policy
                return;
            }
            case STATELESS: {
                final FaultInjectorRemote stub = ClientStubFactory.getFaultInjectorStub(config.rmiPort);
                if (stub == null) {
                    LOG.error("Failed to get a client stub for abstract server");
                    return;
                }
                final InjectionLocation location = new InjectionLocation(className,
                        methodName, lineNum, invokedMethodSig, 
                        stackTrace.hashCode(), failureId);
                final InjectionRemoteQuery query = new InjectionRemoteQuery(serverId,
                        current.getName(), threadId, location, delay, exceptionIds);
                InjectionRemoteCommand command;
                try {
                    command = stub.inject(query);
                } catch (RemoteException e) {
                    LOG.error("Failed to call rmi inform", e);
                    return;
                }
                if (command == null) {
                    LOG.error("fail to get the injection command");
                    return;
                }
                if (command.id == -1) {
                    // no injection
                    return;
                }
                if (!FATE_MODE) {
                    stackTrace = getStackTrace(current);
                }
                LOG.info("the stack trace of injection " + command.id + " is " + stackTrace);
                if (command.delay == 1) {
                    try {
                        LOG.info("LegolasAgent injecting delay");
                        Thread.sleep(delayMillis);
                    } catch (final Exception ignored) {
                        LOG.error("the delay injection is paused");
                    }
                }
                if (command.eid != -1) {
                    final Throwable t = exceptionTable.getException(command.eid);
                    if (t == null) {
                        LOG.error("Trying to inject an exception of invalid id = " + command.eid);
                    } else {
                        LOG.info("LegolasAgent injecting exception " + t.getClass().getName());
                        throw t;
                    }
                }
                return;
            }
            default:
                LOG.error("Unsupported agent type " + config.agentType);
        }
    }

    private static String getStackTrace(final Thread current) {
        final StackTraceElement[] stackTraceElements = current.getStackTrace();
        int iter = 1;
        while (iter < stackTraceElements.length &&
                stackTraceElements[iter].getClassName().startsWith("edu."))
            iter++;
        final StringBuilder sb = new StringBuilder("[");
        if (iter < stackTraceElements.length) {
            for (int i = iter; i < stackTraceElements.length; i++) {
                sb.append('(');
                sb.append(stackTraceElements[i].getClassName());
                sb.append(',');
                sb.append(stackTraceElements[i].getMethodName());
                sb.append(',');
                sb.append(stackTraceElements[i].getLineNumber());
                sb.append("), ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static void inject(int delay, String className, String methodName,
            int lineNum, String invokedMethodSig, int id) throws Throwable {
        inject(delay, new int[]{}, className, methodName, lineNum, invokedMethodSig, id);
    }

    public static void inject(int delay, int eid1, String className, String methodName,
            int lineNum, String invokedMethodSig, int id) throws Throwable {
        inject(delay, new int[]{eid1}, className, methodName, lineNum, invokedMethodSig, id);
    }

    // The reason that we are doing this stupid unrolling is to save some efforts for the instrumentation.
    // To instrument a call to inject with varargs, we must create a new array and do assignment,
    // which is a hassle. The unrolling can get around this issue.
    public static void inject(int delay, int eid1, int eid2, String className,
            String methodName, int lineNum, String invokedMethodSig, int id) throws Throwable {
        inject(delay, new int[]{eid1, eid2}, className, methodName, lineNum, invokedMethodSig, id);
    }

    public static void inject(int delay, int eid1, int eid2, int eid3, String className,
            String methodName, int lineNum, String invokedMethodSig, int id) throws Throwable {
        inject(delay, new int[]{eid1, eid2, eid3}, className, methodName, lineNum, invokedMethodSig, id);
    }

    public static void inject(int delay, int eid1, int eid2, int eid3, int eid4, String className,
            String methodName, int lineNum, String invokedMethodSig, int id) throws Throwable {
        inject(delay, new int[]{eid1, eid2, eid3, eid4}, className, methodName, lineNum, invokedMethodSig, id);
    }

    public static void inject(int delay, int eid1, int eid2, int eid3, int eid4, int eid5,
            String className, String methodName, int lineNum, String invokedMethodSig,
            int id) throws Throwable {
        inject(delay, new int[]{eid1, eid2, eid3, eid4, eid5}, className, methodName, lineNum, invokedMethodSig, id);
    }

    public static void inject(int delay, int eid1, int eid2, int eid3, int eid4, int eid5, int eid6,
            String className, String methodName, int lineNum, String invokedMethodSig,
            int id) throws Throwable {
        inject(delay, new int[]{eid1, eid2, eid3, eid4, eid5, eid6}, className, methodName, lineNum, invokedMethodSig, id);
    }

    /**
     * Invoked by the hook in a target system to indicate that the target system has successfully finished
     * initialization and is ready to take requests.
     * TODO: refactor
     * TODO: to be used
     * @return
     */
    public static boolean sysReady() {
        AbstractStateServerRemote stub = ClientStubFactory.getStateServerStub(config.rmiPort);
        if (stub == null) {
            LOG.error("Failed to get a client for orchestrator server");
            return false;
        }
        try {
            return stub.serverReady(serverId);
        } catch (RemoteException e) {
            LOG.error("Error in signaling system ready", e);
            return false;
        }
    }

    /**
     * Invoked by the hook in a target system to indicate that the target system has stopped.
     * TODO: refactor
     * TODO: to be used
     * @return
     */
    public static boolean sysStopped() {
        AbstractStateServerRemote stub = ClientStubFactory.getStateServerStub(config.rmiPort);
        if (stub == null) {
            LOG.error("Failed to get a client for orchestrator server");
            return false;
        }
        try {
            return stub.serverStopped(serverId);
        } catch (RemoteException e) {
            LOG.error("Error in signaling system ready", e);
            return false;
        }
    }

    /**
     * Invoked by the hooks in a target system to initialize the agent. Should be called only once.
     * TODO: add param configPath
     */
    public static void init() {
        LOG.info("LegolasAgent init: server id = " + serverId);
    }
}
