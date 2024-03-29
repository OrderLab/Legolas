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
package edu.umich.order.legolas.analyzer.hook;

import static edu.umich.order.legolas.analyzer.util.SootUtils.getLine;

import edu.umich.order.legolas.analyzer.algorithm.ExceptionExtractor;
import edu.umich.order.legolas.analyzer.algorithm.InheritanceDecider;
import edu.umich.order.legolas.analyzer.algorithm.InvalidInjectionFilter;
import edu.umich.order.legolas.analyzer.option.AnalyzerOptions;
import edu.umich.order.legolas.analyzer.util.SootUtils;
import edu.umich.order.legolas.common.fault.ExceptionTable;
import edu.umich.order.legolas.common.fault.InjectionFault;
import edu.umich.order.legolas.common.fault.InjectionFault.FaultType;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.PatchingChain;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.LocalGenerator;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;

/**
 * Inject exception in a target method
 */
public class InjectionHookInstrumentor {
    private static final Logger LOG = LoggerFactory.getLogger(InjectionHookInstrumentor.class);
    private static int uniqueId = 0; // unique in the globe

    private static final String SELF_PACKAGE_PREFIX = "edu.umich.order";
    private static final boolean DELAY_FILTER = true;

    private final SootMethod targetMethod;
    private final Body body;
    private final PatchingChain<Unit> units;
    private final LocalGenerator lg;

    private List<InjectionSpec> specs;
    private boolean defaultRule;
    private String scope;
    private ExceptionExtractor exceptionExtractor;
    private ExceptionTable exceptionTable;
    private String[] package_prefix_list;
    private boolean ignoreSelf; // whether to skip instrumenting classes with self package name

    private final List<InjectionPoint> injectionPoints = new ArrayList<>();
    private final List<InjectionPoint> invalidInjectionPoints = new LinkedList<>();

    public InjectionHookInstrumentor(SootMethod targetMethod,
            ExceptionExtractor exceptionExtractor, List<InjectionSpec> specs,
            ExceptionTable exceptionTable, boolean ignoreSelf) {
        this.exceptionTable = exceptionTable;
        this.targetMethod = targetMethod;
        this.exceptionExtractor = exceptionExtractor;
        body = targetMethod.retrieveActiveBody();
        units = body.getUnits();
        lg = new DefaultLocalGenerator(body);
        this.specs = specs;
        // use default injection rule is the specs is null or empty
        defaultRule = specs == null || specs.isEmpty();
        package_prefix_list = AnalyzerOptions.getInstance().system_package_prefix_list == null ?
                new String[]{} : AnalyzerOptions.getInstance().system_package_prefix_list;
        scope = targetMethod.getDeclaringClass().getName();
        this.ignoreSelf = ignoreSelf;
    }

    public final List<InjectionPoint> instrument(InvalidInjectionFilter filter) {
        final List<UnitHookInstrumentor> instrumentors = new ArrayList<>();
        for (final Unit unit : units) {
            final UnitHookInstrumentor unitHookInstrumentor = new UnitHookInstrumentor(unit);
            for (final ValueBox valueBox : unit.getUseBoxes()) {
                final Value value = valueBox.getValue();
                if (value instanceof InvokeExpr) {
                    InjectionPoint point = unitHookInstrumentor.processInvocation((InvokeExpr) value, filter);
                    if (point != null) {
                        injectionPoints.add(point);
                    }
                }
            }
            if (!unitHookInstrumentor.hasFault())
                continue;
            instrumentors.add(unitHookInstrumentor);
        }
        int success = 0;
        for (UnitHookInstrumentor instrumentor : instrumentors) {
            if (instrumentor.instrument())
                success++;
        }
        if (success != injectionPoints.size()) {
            LOG.error("Calculated " + injectionPoints.size() + " injection points, "
                    + "but " + success + " are successfully injected");
        }
        // what's this null unit injection for??
        final UnitHookInstrumentor unitHookInstrumentor = new UnitHookInstrumentor(null);
        unitHookInstrumentor.processInvocation(null, filter);
        if (unitHookInstrumentor.hasFault())
            unitHookInstrumentor.instrument();
        return injectionPoints;
    }

    public SootMethod getTargetMethod() {
        return targetMethod;
    }

    public class UnitHookInstrumentor {
        private final List<Value> exceptionIds = new LinkedList<>();
        private final Unit unit;
        private int delay = 0;
        private String methodSig = "";

        private UnitHookInstrumentor(final Unit unit) {
            this.unit = unit;
        }

        private InjectionPoint processInvocation(InvokeExpr expr, InvalidInjectionFilter filter) {
            if (!methodSig.isEmpty()) {
                LOG.error("Encounter multiple invocations in a unit in method "
                        + targetMethod.getSignature());
                return null;
            }
            SootMethod method;
            if (expr == null)
                method = targetMethod;
            else
                method = expr.getMethod();
            methodSig = method.getSignature(); // including the class and method signature
            final String methodName = method.getName();
            final String className = method.getDeclaringClass().getName();
            if ((ignoreSelf && className.startsWith(SELF_PACKAGE_PREFIX))) {
                // Avoid injecting the invocation to our own tool
                return null;
            }
            // Let ExceptionExtractor decide if this method is worthy of injection.
            // For example, while we prefer injecting at the application boundary, i.e., mostly
            // library calls, the Extractor will handle this logic, including custom criteria.
            final List<SootClass> exceptions = exceptionExtractor.getExceptions(method);
            LOG.debug("Encountered invocation " + methodSig);
            final List<InjectionFault> faults = new ArrayList<>();
            if (defaultRule) {
                // under the default injection rule, any operation that throws an exception
                // in the pre-defined exception table will be injected..
                for (final SootClass e : exceptions) {
                    if (!InheritanceDecider.isIOException(e)) continue; // FIXME: add this to spec
                    final int eid = exceptionTable.getExceptionId(e.getName());
                    if (eid >= 0) {
                        faults.add(new InjectionFault(FaultType.EXCEPTION, e, e.getName(), eid));
                    }
                }
            } else {
                String[] exceptionNames = new String[exceptions.size()];
                boolean hasIOE = false;
                boolean hasInterruptE = false;
                for (int i = 0; i < exceptions.size(); i++) {
                    final SootClass e = exceptions.get(i);
                    if (DELAY_FILTER) {
                        if (InheritanceDecider.isIOException(e))
                            hasIOE = true;
                        else if (InheritanceDecider.isInterruptException(e))
                          hasInterruptE = true;
                    }
                    exceptionNames[i] = e.getName();
                }
                for (InjectionSpec spec : specs) {
                    List<InjectionFault> matches = spec.match(scope, methodSig,
                            methodName, className, exceptionNames);
                    if (matches == null || matches.isEmpty())
                        continue;
                    for (InjectionFault fault : matches) {
                        if (fault.type == FaultType.EXCEPTION) {
                            final int eid = exceptionTable.getExceptionId(fault.exceptionName);
                            if (eid < 0) {
                                LOG.error("To-be-injected exception " + fault.exceptionName
                                        + " does not exist in the exception table");
                                continue;
                            }
                            fault.exceptionId = eid;
                            for (SootClass clz : exceptions) {
                                if (clz.getName().equals(fault.exceptionName)) {
                                    fault.exceptionClass = clz;
                                    break;
                                }
                            }
                        } else if (fault.type == FaultType.DELAY) {
                            if (DELAY_FILTER && !hasIOE && !hasInterruptE) {
                                LOG.debug("Skip delay injection to {}, which does not throw IOException or InterruptedException", methodSig);
                                continue;
                            }
                        }
                        faults.add(fault);
                    }
                    break;
                }
            }
            if (faults.isEmpty())
                return null;

            InjectionPoint point = new InjectionPoint(targetMethod, unit, expr, faults);
            if (filter != null && filter.filter(point)) {
                LOG.debug("Injection point (" + point + ") contains invalid faults");
                invalidInjectionPoints.add(point);
                if (point.faults.isEmpty())
                    return null; // no remaining faults
            }
            for (InjectionFault fault:point.faults) {
                if (fault.type == FaultType.EXCEPTION) {
                    exceptionIds.add(IntConstant.v(fault.exceptionId));
                } else if (fault.type == FaultType.DELAY)
                    delay = 1;
            }
            return point;
        }

        private boolean hasFault() {
            return delay != 0 || !exceptionIds.isEmpty();
        }

        private boolean instrument() {
            exceptionIds.add(0, IntConstant.v(delay));
            StringBuilder sb = new StringBuilder("void inject(");
            for (int i = 0; i < exceptionIds.size(); i++) {
                sb.append("int");
                if (i != exceptionIds.size() - 1)
                    sb.append(",");
            }
            exceptionIds.add(StringConstant.v(targetMethod.getDeclaringClass().getName()));
            exceptionIds.add(StringConstant.v(targetMethod.getName()));
            int lineNum = -1;
            if (unit != null)
                lineNum = getLine(unit);
            if (lineNum == -1) {
                lineNum = getLine(targetMethod);
            }
            exceptionIds.add(IntConstant.v(lineNum));
            exceptionIds.add(StringConstant.v(methodSig));
            exceptionIds.add(IntConstant.v(uniqueId++));
            sb.append(",java.lang.String,java.lang.String,int,java.lang.String,int)");
            // FIXME: ugly but simple...
            // The reason being that to instrument a call to a function with varargs, we have
            // to create a new array and do assignment, which is a hassle. The unrolling can
            // get around this issue.
            if (unit == null) {
                Unit first = units.getFirst();
                while (AbstractStateInstrumentor.isLeadingStmt(first))
                    first = units.getSuccOf(first);
                return insertInjection(first, sb.toString(), exceptionIds);
            }
            return insertInjection(unit, sb.toString(), exceptionIds);
        }
    }

    private boolean insertInjection(final Unit targetUnit, String methodSig, List<Value> args) {
        final SootClass agentClass = SootUtils.getLegolasAgentClass();
        final SootMethod injectionMethod = agentClass.getMethodUnsafe(methodSig);
        if (injectionMethod == null) {
            LOG.error("Failed to find the injection method " + methodSig + " in agent class");
            return false;
        }
        final StaticInvokeExpr injectionExpr = Jimple.v()
                .newStaticInvokeExpr(injectionMethod.makeRef(), args);
        final InvokeStmt injectionStmt = Jimple.v().newInvokeStmt(injectionExpr);
        units.insertBefore(injectionStmt, targetUnit);
        try {
            body.validate();
        } catch (Exception e) {
            LOG.error("Instrumenting call to inject with " + args.size() +
                    " args results in invalid body for " + targetMethod, e);
            System.exit(1);
        }
        return true;
    }

    public int injectionCount() {
        return injectionPoints.size();
    }

    public int invalidInjectionCount() {
        return invalidInjectionPoints.size();
    }

    public List<InjectionPoint> getInjectionPoints() {
        return injectionPoints;
    }

    public List<InjectionPoint> getInvalidInjectionPoints() {
        return invalidInjectionPoints;
    }

    public void dumpInjectionsPlain(List<InjectionPoint> points, PrintWriter writer)  {
        writer.println(targetMethod.getSignature());
        writer.println("-----");
        for (InjectionPoint point : points) {
            SootMethod invocation = point.expr.getMethod();
            String invocationStr = invocation.getDeclaringClass().getName() + "." +
                    invocation.getName() + "()";
            writer.print("@" + point.lineNo + ":" +  invocationStr + ":");
            for (InjectionFault fault : point.faults)
                writer.print(fault.toString() + " ");
            writer.println("");
        }
        writer.println("-----");
    }

    public void dumpInjectionsCSV(List<InjectionPoint> points, PrintWriter writer,
            boolean print_header) {
        if (print_header)
            writer.println("method,injections,injected_invocations,injected_exceptions");
        writer.print("\"" + targetMethod.getSignature() + "\",");
        writer.print(points.size() + ",\"");
        StringBuilder sb1 = new StringBuilder(), sb2 = new StringBuilder();
        for (InjectionPoint point : points) {
            SootMethod invocation = point.expr.getMethod();
            String invocationStr = invocation.getDeclaringClass().getName() + "." +
                    invocation.getName() + "()";
            sb1.append('@').append(point.lineNo).append(':').append(invocationStr);
            sb2.append('@').append(point.lineNo).append(':');
            for (InjectionFault fault : point.faults) {
                sb2.append(fault.toString());
            }
        }
        writer.println(sb1 + "\",\"" + sb2 + "\"");
    }
}
