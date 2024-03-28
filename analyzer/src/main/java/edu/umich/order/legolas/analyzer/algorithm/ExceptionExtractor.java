/*
 *  @author Haoze Wu <haoze@jhu.edu>, Ryan Huang <ryanph@umich.edu>
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

import edu.umich.order.legolas.analyzer.util.SootUtils;
import edu.umich.order.legolas.common.fault.ExceptionTable;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.PatchingChain;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.ThrowStmt;
import soot.util.Chain;

/**
 * Extract exception created within method bodies.
 */
public final class ExceptionExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionExtractor.class);

    private final String[] package_prefix_list;
    private final ExceptionTable exceptionTable;
    private final boolean extractCallInstr;   // whether to consider exceptions in call instructions

    private final Map<SootMethod, List<SootClass>> table = new HashMap<>();
    private final List<SootClass> emptyList = new ArrayList<>();
    private final Set<SootClass> customExternalClasses = new HashSet<>();
    private final Set<SootMethod> customExternalMethods = new HashSet<>();

    public ExceptionExtractor(String[] package_prefix_list, ExceptionTable exception_table,
            boolean extract_call_instr) {
        this.package_prefix_list = package_prefix_list;
        this.exceptionTable = exception_table;
        this.extractCallInstr = extract_call_instr;
    }

    public List<SootClass> getExceptions(final SootMethod method) {
        if (table.containsKey(method))
            return table.get(method);
        SootClass clz = method.getDeclaringClass();
        // if the class or the method is in the custom external set or the class name
        // does not match the package prefix, we treat the method as external and use
        // the exceptions defined in its signature as is.
        if (customExternalClasses.contains(clz) || customExternalMethods.contains(method) ||
                Arrays.stream(package_prefix_list).noneMatch(clz.getName()::startsWith)) {
            if (method.isPhantom() && method.getExceptions().isEmpty())
                LOG.debug("Phantom method {} with unknown exception signature", method.getSignature());
            return method.getExceptions();
        }
        return emptyList;
    }

    public void addExternalClasses(Collection<SootClass> classes) {
        customExternalClasses.addAll(classes);
    }

    public Map<SootMethod, List<SootClass>> extract(Chain<SootClass> classes) {
        // Add application rules first
        addApplicationRules(classes);

        for (final SootClass sootClass : classes) {
            if (Arrays.stream(package_prefix_list).noneMatch(sootClass.getName()::startsWith)) {
                continue;
            }
            for (final SootMethod sootMethod : sootClass.getMethods()) {
                extractFromMethodBody(sootMethod, true, true);
            }
        }
        LOG.info("Extracted exceptions for " + table.size() + " methods.");
        return table;
    }

    public List<SootClass> extractFromMethodBody(SootMethod method, boolean uncaughtOnly, boolean cache) {
        if (!method.hasActiveBody()) {
            LOG.debug("Method " + method.getSignature() + " has no active body");
            // if we can't have find active body (e.g., this is a library function),
            // use the exceptions declared in the function signature
            if (extractCallInstr) {
                return method.getExceptions();
            }
            return emptyList;
        }
        Set<SootClass> exceptions = new HashSet<>();
        Body body = method.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        List<ExceptionIndex> newExceptions = new ArrayList<>();
        Map<Unit, Integer> indexes = new HashMap<>();
        Map<Unit, Unit> catch2throw = new HashMap<>();
        for (Unit unit : units) {
            int index = indexes.size();
            indexes.put(unit, index);
            if (unit instanceof ThrowStmt) {
                Value exceptionRef = ((ThrowStmt) unit).getOp();
                // FIXME: this simple analysis does not handle def-use chain completely.
                DefinitionStmt def = SootUtils.findValueDefInBody(exceptionRef, unit, body);
                if (def == null) {
                    LOG.debug("Failed to find definition for throw statement " +
                        unit + " at line " + unit.getJavaSourceStartLineNumber() +
                        " in " + method.getDeclaringClass().getName());
                    continue;
                }
                Value rhs = def.getRightOp();
                if (rhs instanceof CaughtExceptionRef) {
                    catch2throw.put(def, unit);
                } else if (rhs instanceof NewExpr) {
                    SootClass exception = ((NewExpr) rhs).getBaseType().getSootClass();
                    if (InheritanceDecider.isThrowable(exception)) {
                        newExceptions.add(new ExceptionIndex(exception, index));
                    }
                } else {
                    // This happens due to the simple analysis in findThrowDef.
                    LOG.debug("Definition for throw in " + method.getSignature() + "@line " +
                            unit.getJavaSourceStartLineNumber() + " is neither caught or new: " + unit);
                }
            } else if (extractCallInstr) {
                for (ValueBox valueBox : unit.getUseBoxes()) {
                    Value value = valueBox.getValue();
                    if (value instanceof InvokeExpr) {
                        InvokeExpr expr = (InvokeExpr) value;
                        SootMethod invoked = expr.getMethod();
                        if (invoked != null) {
                            // TODO: here we trust the exceptions declared in the
                            // function signature. it's better to use the extractor
                            // logic recursively to analyze the function if possible.
                            List<SootClass> declared = invoked.getExceptions();
                            for (SootClass exception : declared) {
                                if (InheritanceDecider.isThrowable(exception)) {
                                    newExceptions.add(new ExceptionIndex(exception, index));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (ExceptionIndex entry : newExceptions) {
            if (!uncaughtOnly) {
                exceptions.add(entry.exception);
                continue;
            }
            int index = entry.index;
            if (exceptionTable != null &&
                    exceptionTable.getExceptionId(entry.exception.getName()) == -1) {
                LOG.debug("New exception not in the table: " + entry.exception.getName());
            }
            while (index != -1) {
                boolean caught = false;
                for (final Trap trap : body.getTraps()) {
                    if (indexes.get(trap.getBeginUnit()) <= index &&
                            index < indexes.get(trap.getEndUnit()) &&
                            InheritanceDecider.isSubtype(entry.exception,
                                    trap.getException())) {
                        Unit handler = trap.getHandlerUnit();
                        if (catch2throw.containsKey(handler)) {
                            index = indexes.get(catch2throw.get(handler));
                        } else {
                            index = -1;
                        }
                        caught = true;
                        break;
                    }
                }
                if (!caught) {
                    exceptions.add(entry.exception);
                    break;
                }
            }
        }
        List<SootClass> exceptionList = new ArrayList<>(exceptions);
        if (cache)
            table.put(method, exceptionList);
        return exceptionList;
    }

    public int size() {
        return table.size();
    }

    /**
     * Sort the exception table by the class name first and then method name
     *
     * @return the sorted exception table entries
     */
    public Collection<Map.Entry<SootMethod, List<SootClass>>> sortExceptionTableEntries() {
        List<Map.Entry<SootMethod, List<SootClass>>> entries = new ArrayList<>(table.entrySet());
        Comparator<Map.Entry<SootMethod, List<SootClass>>> comparator = (o1, o2) -> {
            String c1 = o1.getKey().getDeclaringClass().getName();
            String m1 = o1.getKey().getName();
            String c2= o2.getKey().getDeclaringClass().getName();
            String m2 = o2.getKey().getName();
            int c = c1.compareTo(c2);
            if (c == 0)
                return m1.compareTo(m2);
            return c;
        };
        entries.sort(comparator);
        return entries;
    }

    private static class ExceptionIndex {
        public SootClass exception;
        public int index;

        public ExceptionIndex(SootClass exception, int index) {
            this.exception = exception;
            this.index = index;
        }
    }

    public void dumpCSV(PrintWriter writer, boolean print_header) {
        if (print_header)
            writer.println("class,method,exceptions");
        Collection<Map.Entry<SootMethod, List<SootClass>>> sortedEntries = sortExceptionTableEntries();
        for (Map.Entry<SootMethod, List<SootClass>> entry : sortedEntries) {
            SootClass declaringClass = entry.getKey().getDeclaringClass();
            SootMethod method = entry.getKey();
            List<SootClass> exceptions = entry.getValue();
            if (exceptions.isEmpty())
                continue;
            StringBuilder sb = new StringBuilder();
            for (SootClass exception:exceptions) {
                sb.append("#");
                sb.append(exception.getName());
            }
            writer.println("\"" + declaringClass.getName() + "\",\"" +
                    method.getName()+ "\"," + sb);
        }
    }

    public void addApplicationRules(Chain<SootClass> classes) {
        for (String prefix : package_prefix_list) {
            if (prefix.startsWith("org.apache.zookeeper")) {
                List<SootClass> externalClasses = new ArrayList<>();
                for (SootClass clz : classes) {
                    if (clz.getName().startsWith("org.apache.jute")) {
                        externalClasses.add(clz);
                    }
                }
                addExternalClasses(externalClasses);
            } else if (prefix.startsWith("org.apache.cassandra")) {
                // TODO: add those classes in cassandra that we consider to be injection boundary
                // as the external classes, e.g., those in org.apache.cassandra.io.util.
            }
        }
    }
}
