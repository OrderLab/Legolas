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
package edu.umich.order.legolas.analyzer.algorithm;

import edu.umich.order.legolas.analyzer.algorithm.InMemoryUnitAnalysis.MethodParamRuleType;
import edu.umich.order.legolas.analyzer.hook.InjectionPoint;
import edu.umich.order.legolas.analyzer.util.SootUtils;
import edu.umich.order.legolas.common.fault.InjectionFault;
import edu.umich.order.legolas.common.fault.InjectionFault.FaultType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.jimple.ParameterRef;
import soot.util.Chain;

/**
 * Filter invalid injection points such as injecting IOException to in-memory buffer.
 */
public class InvalidInjectionFilter {
    private static final Logger LOG = LoggerFactory.getLogger(InvalidInjectionFilter.class);

    private static final boolean DELAY_ONLY_IO = true;
    private static final boolean DELAY_NONEMPTY_EXCEPTION = true;

    protected final String[] package_prefix_list;

    protected CallSiteAnalysis callSiteAnalysis;
    protected InMemoryUnitAnalysis inMemoryUnitAnalysis;
    protected FieldRefDefsAnalysis fieldRefDefsAnalysis;
    protected ExceptionExtractor exceptionExtractor;

    public InvalidInjectionFilter(String[] package_prefix_list) {
        this.package_prefix_list = package_prefix_list;
        callSiteAnalysis = new CallSiteAnalysis(package_prefix_list);
        fieldRefDefsAnalysis = new FieldRefDefsAnalysis();
        inMemoryUnitAnalysis = new InMemoryUnitAnalysis(package_prefix_list, callSiteAnalysis,
                fieldRefDefsAnalysis);
        if (DELAY_ONLY_IO)
            exceptionExtractor = new ExceptionExtractor(package_prefix_list, null, true);
        else
            exceptionExtractor = null;
    }

    public void analyze(Chain<SootClass> classes) {
        callSiteAnalysis.analyze(classes);
        inMemoryUnitAnalysis.analyze(classes);
        // add java standard library rules
        addStdLibraryRules();
        // add custom application rules
        addApplicationRules(classes);
    }

    public InMemoryUnitAnalysis getInMemoryAnalysis() {
        return inMemoryUnitAnalysis;
    }

    /**
     * Filter the invalid faults in an injection point. If this point has a list of faults, only
     * these valid faults will be retained.
     *
     * @param point The injection point to be examined
     * @return true if some invalid fault is removed; false if no invalid fault is found.
     */
    public boolean filter(InjectionPoint point) {
        if (point.faults.isEmpty() || point.expr == null)
            return false;

        SootMethod callee = point.expr.getMethod();
        Iterator<InjectionFault> iterator = point.faults.iterator();
        boolean inMemChecked = false;
        boolean isDelayChecked = false;
        boolean isIOExceptionInvalid = false;
        boolean isDelayInvalid = false;
        while (iterator.hasNext()) {
            InjectionFault fault = iterator.next();
            if (fault.type == FaultType.DELAY) {
                if (!isDelayChecked) {
                    if (!callee.isPhantom()) {
                        if (DELAY_ONLY_IO && exceptionExtractor != null) {
                            // if we only delay io operations, we determine it by checking if the method
                            // is possible to throw an I/O exception
                            List<SootClass> exceptions = exceptionExtractor.extractFromMethodBody(
                                    callee, false, true);
                            boolean isIOE = false;
                            for (SootClass exception : exceptions) {
                                if (InheritanceDecider.isIOException(exception)) {
                                    isIOE = true;
                                    break;
                                }
                            }
                            if (!isIOE)
                                isDelayInvalid = true;
                        } else if (DELAY_NONEMPTY_EXCEPTION) {
                            if (callee.getExceptions().isEmpty())
                                isDelayInvalid = true;
                        }
                    }
                    isDelayChecked = true;
                }
                if (isDelayInvalid)  {
                    iterator.remove();
                }
            } else if (fault.type == FaultType.EXCEPTION) {
                Object clz = fault.exceptionClass;
                if (fault.exceptionName.equals("java.io.IOException") || ((clz instanceof SootClass)
                        && InheritanceDecider.isIOException((SootClass) clz))) {
                    if (!inMemChecked) {
                        isIOExceptionInvalid = inMemoryUnitAnalysis.checkInvokeInMemObj(
                                point.expr, point.unit, point.function);
                        inMemChecked = true;
                    }
                    if (isIOExceptionInvalid) {
                        // remove the IOException fault
                        iterator.remove();
                    }
                }
            }
        }
        return isIOExceptionInvalid || isDelayInvalid;
    }

    protected void addStdLibraryRules()  {
        Set<String> streamRoot = new HashSet<>(Arrays.asList("java.io.OutputStream",
                "java.io.InputStream"));
        for (SootClass clz : Scene.v().getLibraryClasses()) {
            if (clz.getName().startsWith("java.io")) {
                SootClass root = SootUtils.wrapperClassOfAny(clz, streamRoot);
                if (root == null)
                    continue;
                for (SootMethod m : clz.getMethods()) {
                    if (m.getName().equals("<init>")) {
                        List<Type> paramTypes = m.getParameterTypes();
                        List<ParameterRef> refs = new LinkedList<>();
                        for (int index = 0; index < paramTypes.size(); index++) {
                            Type type = paramTypes.get(index);
                            if (streamRoot.contains(type.toString())) {
                                refs.add(new ParameterRef(type, index));
                            }
                        }
                        if (!refs.isEmpty()) {
                            LOG.debug("Found standard stream class {} to add to in-mem rules", clz.getName());
                            inMemoryUnitAnalysis.addInMemMethodParamRule(MethodParamRuleType.CONSTRUCTOR,
                                    m.getSignature(), refs);
                        }
                    }
                }
            }
        }
    }

    protected void addApplicationRules(Chain<SootClass> classes) {
        SootMethod m;
        int success = 0;
        for (String prefix : package_prefix_list) {
            if (prefix.startsWith("org.apache.zookeeper")) {
                List<MethodParamRule> ruleList = new LinkedList<>();
                ruleList.add(new MethodParamRule(true, "org.apache.jute.BinaryOutputArchive",
                        "getArchive", 0));
                ruleList.add(new MethodParamRule(true, "org.apache.jute.BinaryInputArchive",
                        "getArchive", 0));
                for (MethodParamRule rule : ruleList) {
                    m = SootUtils.getMethodByNameUnsafe(rule.className, rule.methodName);
                    if (m != null) {
                        // in-memory check of getArchive's return value depends on its first parameter
                        inMemoryUnitAnalysis.addInMemMethodParamRule(
                                MethodParamRuleType.RETURN, m, rule.paramIndex);
                        success++;
                    } else {
                        LOG.error("Failed to find method {}.{}", rule.className, rule.methodName);
                    }
                }

                LOG.info("Loaded {}/{} in-mem-return rules for ZooKeeper", success, ruleList.size());

                Set<String> inMemOpTypeSet = new HashSet<>();
                inMemOpTypeSet.add("org.apache.jute.InputArchive");
                inMemOpTypeSet.add("org.apache.jute.OutputArchive");

                success = 0;

                for (SootClass clz : classes) {
                    if (clz.getName().startsWith("org.apache.jute")) {
                        for (SootMethod method : clz.getMethods()) {
                            List<Type> paramTypes = method.getParameterTypes();
                            for (int index = 0; index < paramTypes.size(); index++) {
                                Type type = paramTypes.get(index);
                                if (inMemOpTypeSet.contains(type.toString())) {
                                    // The rule is about whether the internal operations of the method
                                    // can be in-memory operations or real I/O operations
                                    inMemoryUnitAnalysis.addInMemMethodParamRule(
                                            MethodParamRuleType.OPERATION, method, index);
                                    success++;
                                    break;
                                }
                            }
                        }
                    }
                }
                LOG.info("Loaded {} in-mem-operation rules for ZooKeeper", success);
            } else if (prefix.startsWith("org.apache.cassandra")) {
                List<SootClass> inMemWrappers = InMemoryUnitAnalysis.findInMemStandLibIOWrappers(
                        classes, "org.apache.cassandra.io.util");
                for (SootClass wrapper : inMemWrappers) {
                    inMemoryUnitAnalysis.addCustomInMemTypes(wrapper.getName());
                }
            } else if (prefix.startsWith("org.apache.hadoop.hbase")) {
                String[] streamClassNames = new String[]{
                        "org.apache.hbase.thirdparty.com.google.protobuf.CodedOutputStream",
                        "org.apache.hbase.thirdparty.com.google.protobuf.CodedInputStream"
                };
                Set<String> streamArgTypeSet = new HashSet<>(Arrays.asList(
                        "java.io.OutputStream", "java.io.InputStream", "byte[]", "java.nio.ByteBuffer"));
                for (String name : streamClassNames) {
                    SootClass clz = Scene.v().getSootClassUnsafe(name);
                    if (clz != null) {
                        for (SootMethod method : clz.getMethods()) {
                            if (method.getName().equals("newInstance")) {
                                List<Type> paramTypes = method.getParameterTypes();
                                for (int index = 0; index < paramTypes.size(); index++) {
                                    String typeStr = paramTypes.get(index).toString();
                                    if (streamArgTypeSet.contains(typeStr)) {
                                        inMemoryUnitAnalysis.addInMemMethodParamRule(
                                                MethodParamRuleType.RETURN, method, index);
                                        success++;
                                    }
                                }
                            }
                        }
                    }
                }
                LOG.info("Loaded {} in-mem-return rules for HBase", success);
            }
        }
    }

    private static class MethodParamRule {
        public String className;
        public String methodName;
        public int[] paramIndex;
        public boolean aboutReturn;

        public MethodParamRule(boolean aboutReturn, String clz, String method, int...indexes) {
            this.className = clz;
            this.methodName = method;
            this.paramIndex = indexes;
            this.aboutReturn = aboutReturn;
        }
    }
}
