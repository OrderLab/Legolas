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
package edu.umich.order.legolas.analyzer.util;

import edu.umich.order.legolas.common.agent.LegolasAgent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Transform;
import soot.Transformer;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticInvokeExpr;
import soot.tagkit.LineNumberTag;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.util.Chain;

/**
 * A collection of helper functions for using Soot (main code snippets are from AutoWatchdog project).
 */
public class SootUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SootUtils.class);

    private static Map<String, SootClass> cachedSootClasses = new HashMap<>();
    private static SootClass legolasAgentClass;

    private static final Set<String> ioStreamTypes = new HashSet<>(Arrays.asList(
            "java.io.InputStream",
            "java.io.OutputStream",
            "java.io.DataInput",
            "java.io.DataOutput"
    ));

    public static SootClass getCachedSootClass(String className) {
        SootClass cls = cachedSootClasses.get(className);
        if (cls == null) {
            cls = Scene.v().loadClassAndSupport(className);
            cachedSootClasses.put(className, cls);
        }
        return cls;
    }

    public static SootClass getLegolasAgentClass() {
        if (legolasAgentClass == null) {
            legolasAgentClass = getCachedSootClass(LegolasAgent.class.getCanonicalName());
        }
        return legolasAgentClass;
    }

    public static SootClass getThrowableClass() {
        return getCachedSootClass("java.lang.Throwable");
    }

    /**
     * Get line number of a Soot unit
     */
    public static int getLine(final Unit unit) {
        final LineNumberTag tag = (LineNumberTag) unit.getTag("LineNumberTag");
        if (tag != null) {
            return tag.getLineNumber();
        }
        return -1;
    }

    /*
     * Get line number of a Soot method
     */
    public static int getLine(final SootMethod sootMethod) {
        final LineNumberTag tag = (LineNumberTag) sootMethod.getTag("LineNumberTag");
        if (tag == null) {
            LOG.debug("null LineNumberTag for method " + sootMethod.getSubSignature());
            return -1;
        }
        return tag.getLineNumber();
    }

    public static List<SootClass> findStandardIoWrappers(Chain<SootClass> classes, String... prefix_filters) {
        List<SootClass> wrapperClasses = new ArrayList<>();
        for (SootClass clz : classes) {
            if (Arrays.stream(prefix_filters).anyMatch(clz.getName()::startsWith)) {
                if (wrapperClassOfAny(clz, ioStreamTypes) != null)
                    wrapperClasses.add(clz);
            }
        }
        return wrapperClasses;
    }

    public static SootClass wrapperClassOfAny(SootClass clz, Set<String> superClassNames) {
        SootClass wrapper = null;
        SootClass parentClz = clz;
        Chain<SootClass> interfaces;
        while (parentClz != null && parentClz.hasSuperclass()) {
            interfaces = parentClz.getInterfaces();
            parentClz = parentClz.getSuperclass();
            if (parentClz != null && superClassNames.contains(parentClz.getName())) {
                wrapper = parentClz;
                break;
            }
            for (SootClass iface : interfaces) {
                if (superClassNames.contains(iface.getName())) {
                    wrapper = iface;
                    break;
                }
            }
        }
        return wrapper;
    }

    /**
     * Add a new phase into a phase pack in Soot
     *
     * @return the new phase added
     */
    public static Transform addNewTransform(String phasePackName, String phaseName,
            Transformer transformer) {
        Transform phase = new Transform(phaseName, transformer);
        phase.setDeclaredOptions("enabled");
        phase.setDefaultOptions("enabled:false");
        PackManager.v().getPack(phasePackName).add(phase);
        return phase;
    }

    public static SootMethod getMethodByNameUnsafe(String className, String methodName) {
        SootClass clz = Scene.v().getSootClassUnsafe(className);
        if (clz != null) {
            SootMethod m = clz.getMethodByNameUnsafe(methodName);
            return m;
        }
        return null;
    }

    /**
     * Generate a hash code for the object in a target unit.
     *
     * @param units
     * @param hashCodeLocal
     */
    public static void genHashCode(final Chain<Unit> units, final Local hashCodeLocal, final Unit targetUnit) {
        final String className = "java.lang.System";
        final String methodSig = "int identityHashCode(java.lang.Object)";
        final SootClass systemClass = Scene.v().loadClassAndSupport(className);
        final SootMethod hashCodeMethod = systemClass.getMethod(methodSig);
        final Value thisRef = units.getFirst().getDefBoxes().get(0).getValue();
        final StaticInvokeExpr hashCodeExpr = Jimple.v().newStaticInvokeExpr(hashCodeMethod.makeRef(), thisRef);
        final AssignStmt assignStmt = Jimple.v().newAssignStmt(hashCodeLocal, hashCodeExpr);
        units.insertAfter(assignStmt, targetUnit);
    }

    public static void insertLegolasAgentInit(final SootMethod mainMethod) {
        final Chain<Unit> units = mainMethod.retrieveActiveBody().getUnits();
        final SootClass agentClass = SootUtils.getLegolasAgentClass();
        final SootMethod initMethod = agentClass.getMethodByName("init");
        final StaticInvokeExpr registerExpr = Jimple.v()
                .newStaticInvokeExpr(initMethod.makeRef());
        final InvokeStmt registerStmt = Jimple.v().newInvokeStmt(registerExpr);
        units.insertAfter(registerStmt, units.getFirst());
    }

    public static List<SootMethod> findMainMethod(String mainClassName,
            List<String> secondaryMainClassNames) {
        List<SootMethod> mainMethods = new ArrayList<>();
        for (final SootClass sootClass : Scene.v().getApplicationClasses()) {
            if ((mainClassName != null && sootClass.getName().equals(mainClassName)) ||
                    (secondaryMainClassNames != null &&
                            secondaryMainClassNames.contains(sootClass.getName()))) {
                final SootMethod mainMethod = sootClass.getMethodUnsafe(
                        "void main(java.lang.String[])");
                if (mainMethod != null) {
                    mainMethods.add(mainMethod);
                }
            }
        }
        return mainMethods;
    }

    /**
     * Find the definition statement of a value by simply searching the body units in reverse order
     * and check all definition statement units to see if it matches the value.
     *
     * @param value The value whose definition to be found
     * @param stmt The statement from which we search backward. If passed null, we will search from
     *             the last statement of the method body.
     * @param body The method body in which we want to search
     *
     * @return the statement that defines the value if it exists, otherwise return null
     */
    public static DefinitionStmt findValueDefInBody(Value value, Unit stmt, Body body) {
        PatchingChain<Unit> units = body.getUnits();
        Unit u;
        if (stmt != null) {
            u = stmt;
        } else {
            u = units.getLast();
        }
        SootField targetField = null;
        if (value instanceof FieldRef) {
            targetField = ((FieldRef) value).getField();
        }
        while (units.getFirst() != u) {
            u = units.getPredOf(u);
            if (!(u instanceof DefinitionStmt)) {
                continue;
            }
            Value lhs = ((DefinitionStmt) u).getLeftOp();
            if (lhs == value)
                return (DefinitionStmt) u;
            else if (targetField != null && lhs instanceof FieldRef &&
                    ((FieldRef) lhs).getField() == targetField)
                return (DefinitionStmt) u;
        }
        return null;
    }

    /**
     * Local def-use and use-def chains for a soot method
     */
    public static class MethodLocalDefUses {
        public LocalDefs ld;
        public LocalUses lu;

        public UnitGraph graph;

        public MethodLocalDefUses(LocalDefs ld, LocalUses lu, UnitGraph graph) {
            this.ld = ld;
            this.lu = lu;
            this.graph = graph;
        }

        public void buildUses() {
            lu = LocalUses.Factory.newLocalUses(graph, ld);
        }

        public static MethodLocalDefUses build(SootMethod method, boolean buildUse) {
            UnitGraph graph = new ExceptionalUnitGraph(method.getActiveBody());
            LocalDefs ld = LocalDefs.Factory.newLocalDefs(graph);
            LocalUses lu = null;
            if (buildUse)
                lu = LocalUses.Factory.newLocalUses(graph, ld);
            return new MethodLocalDefUses(ld, lu, graph);
        }
    }

    public static class SootCallSite {
        public final InvokeExpr expr;
        public final Unit unit;
        public final SootMethod method;
        public final int lineNo;

        public SootCallSite(InvokeExpr e, Unit u, SootMethod m) {
            expr = e;
            unit = u;
            method = m;
            if (unit == null)
                lineNo = -1;
            else
                lineNo = unit.getJavaSourceStartLineNumber();
        }
    }

    public static class FunctionSummaryBase<T> {
        public final SootMethod method;
        public boolean computed;
        public T summary;

        public FunctionSummaryBase(SootMethod method) {
            this.method = method;
            computed = false;
        }
    }

    public static class FunctionParamSummary<T> extends FunctionSummaryBase<T> {
        public final int paramIndex;
        public final List<SootCallSite> callSites;

        public FunctionParamSummary(SootMethod method, int index) {
            super(method);
            this.paramIndex = index;
            callSites = new ArrayList<>();
        }
    }

    public static class FunctionRetSummary<T> extends FunctionSummaryBase<T> {
        public final List<ReturnStmt> returns;
        public FunctionRetSummary(SootMethod method) {
            super(method);
            returns = new ArrayList<>();
        }
    }

    public static class FunctionValueDefSummary<T> extends FunctionSummaryBase<T> {
        public final Unit unit;
        public final List<DefinitionStmt> defs;
        public FunctionValueDefSummary(Unit unit) {
            super(null);
            this.unit = unit;
            defs = new ArrayList<>();
        }
    }

    public static class FunctionLocalSummary<T> extends FunctionValueDefSummary<T>  {
        public final Local local;

        public FunctionLocalSummary(Local local, Unit unit) {
            super(unit);
            this.local = local;
        }
    }

    public static class FunctionFieldSummary<T> extends FunctionValueDefSummary<T>  {
        public final FieldRef ref;

        public FunctionFieldSummary(FieldRef ref, Unit unit) {
            super(unit);
            this.ref = ref;
        }
    }

    public static class FieldClassDef {
        public final Map<SootMethod, List<Unit>> methodDefMap;
        public FieldClassDef() {
            methodDefMap = new HashMap<>();
        }
    }
}