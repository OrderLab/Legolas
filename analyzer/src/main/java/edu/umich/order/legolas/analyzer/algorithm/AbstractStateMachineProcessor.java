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
package edu.umich.order.legolas.analyzer.algorithm;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;

/**
 * Extract the state machine for a class
 */
public final class AbstractStateMachineProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateMachineProcessor.class);

    /*
     * TODO: move to util
     */
    private static SootMethod getMethodBySubSig(final SootClass sootClass, final String subSig) {
        final SootMethod method = sootClass.getMethodUnsafe(subSig);
        if (method == null) return null;
        // sometimes the method is not implemented, and it should be ignored
        try {
            method.retrieveActiveBody();
        } catch (final Exception ignored) {
            return null;
        }
        return method;
    }

    /*
     * TODO: move to util
     */
    private static SootMethod getMethodByName(final SootClass sootClass, final String name) {
        final SootMethod method = sootClass.getMethodByNameUnsafe(name);
        if (method == null) return null;
        // sometimes the method is not implemented, and it should be ignored
        try {
            method.retrieveActiveBody();
        } catch (final Exception ignored) {
            return null;
        }
        return method;
    }

    public static AbstractStateMachineProcessor create(final SootClass sootClass) {
        final Map<SootMethod, Boolean> stateMethods = new HashMap<>();
        if (InheritanceDecider.isThreadOrRunnable(sootClass)) {
            // FIXME: we will need to specially handle the main class as it may not extend thread
            //  need to make it more robust: we should identify the class that starts the
            //  while(true) control loop, e.g., ZooKeeperServer vs. ZooKeeperServerMain
            //  moduleClasses.add(Scene.v().getMainClass());
            final SootMethod runMethod = getMethodBySubSig(sootClass, "void run()");
            if (runMethod != null)
                stateMethods.put(runMethod, true);
        }
        if (InheritanceDecider.isVerbHandler(sootClass)) {
            final SootMethod doVerbMethod = getMethodBySubSig(sootClass,
                    "void doVerb(org.apache.cassandra.net.MessageIn,int)");
            if (doVerbMethod != null)
                stateMethods.put(doVerbMethod, true);
        }
        if (InheritanceDecider.isWrappedRunnable(sootClass)) {
            final SootMethod runMayThrowMethod = getMethodBySubSig(sootClass,
                    "void runMayThrow()");
            if (runMayThrowMethod != null) {
                stateMethods.put(runMayThrowMethod, true);
            }
        }
        if (InheritanceDecider.isDiskAwareRunnable(sootClass)) {
            final SootMethod runWithMethod = getMethodBySubSig(sootClass,
                    "void runWith(java.io.File)");
            if (runWithMethod != null) {
                stateMethods.put(runWithMethod, true);
            }
        }
        if (stateMethods.isEmpty()) {
            return null;
        }
        return new AbstractStateMachineProcessor(sootClass, stateMethods);
    }

    private final SootClass sootClass;
    private final Map<SootMethod, Boolean> stateMethods; // method -> register
    private final Set<SootField> stateVariables;
    private final Map<SootMethod, AbstractStateAnalysis> analyses; // method -> analysis

    private AbstractStateMachineProcessor(final SootClass sootClass,
            final Map<SootMethod, Boolean> stateMethods) {
        this.sootClass = sootClass;
        this.stateMethods = stateMethods;
        final ConcreteStateAnalysis concreteStateAnalysis = new ConcreteStateAnalysis(sootClass);
        this.stateVariables = concreteStateAnalysis.getResult();
        this.analyses = new HashMap<>();
    }

    public final void identifyAbstractStates(final StatefulMethodFilter statefulMethodFilter) {
        for (final SootMethod method : stateMethods.keySet()) {
            final AbstractStateAnalysis abstractStateAnalysis = new AbstractStateAnalysis(
                    method.retrieveActiveBody(), stateVariables, statefulMethodFilter);
            analyses.put(method, abstractStateAnalysis);
        }
    }

    public Collection<AbstractStateAnalysis> getAnalysesForMethods() {
        return analyses.values();
    }

    public SootClass getAnalyzedClass() {
        return sootClass;
    }

    public void instrument() {
        for (final SootMethod method : analyses.keySet()) {
            final AbstractStateAnalysis analysis = analyses.get(method);
            final boolean register = stateMethods.get(method);
            analysis.instrument(register);
        }
    }

    public void dumpCSV(PrintWriter writer, boolean print_header) {
        if (print_header)
            writer.println("class,method,asv,csv");
        for (AbstractStateAnalysis analysis : getAnalysesForMethods()) {
            SootMethod method = analysis.getAnalyzedBody().getMethod();
            String className = sootClass.getName();
            int asvCnt = analysis.getNumberOfASVs();
            int csvCnt = analysis.getStateVariables().size();
            writer.println(className + ",\"" + method.getSubSignature()
                    + "\"," + asvCnt + "," + csvCnt);
        }
    }
}
