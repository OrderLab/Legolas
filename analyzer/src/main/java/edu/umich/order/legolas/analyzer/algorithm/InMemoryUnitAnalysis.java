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

import edu.umich.order.legolas.analyzer.util.SootUtils;
import edu.umich.order.legolas.analyzer.util.SootUtils.FieldClassDef;
import edu.umich.order.legolas.analyzer.util.SootUtils.FunctionFieldSummary;
import edu.umich.order.legolas.analyzer.util.SootUtils.FunctionLocalSummary;
import edu.umich.order.legolas.analyzer.util.SootUtils.FunctionParamSummary;
import edu.umich.order.legolas.analyzer.util.SootUtils.FunctionRetSummary;
import edu.umich.order.legolas.analyzer.util.SootUtils.MethodLocalDefUses;
import edu.umich.order.legolas.analyzer.util.SootUtils.SootCallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.NewExpr;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.util.Chain;

/**
 * Performs analysis for various types of Soot unit to determine if the unit potentially involves
 * some in-memory objects.
 */
public class InMemoryUnitAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryUnitAnalysis.class);

    // max depth to handle situation such as cycles in analyzing recursive methods
    // and to prevent the analysis from getting stuck for too long
    private static final int IN_MEM_CHECK_MAX_DEPTH = 100;

    public static final Set<String> JAVA_LIB_IN_MEM_TYPE = new HashSet<>(Arrays.asList(
            "java.io.ByteArrayOutputStream",
            "java.io.ByteArrayInputStream",
            "java.nio.ByteBuffer"
    ));

    private final Set<String> inMemTypes = new HashSet<>(JAVA_LIB_IN_MEM_TYPE);

    private final String[] package_prefix_list;

    private final Map<String, Map<Integer, FunctionParamSummary<InMemCheckResult>>>
            paramInMemorySummaryMap = new HashMap<>();

    private final Map<String, FunctionRetSummary<InMemCheckResult>>
            retInMemorySummaryMap = new HashMap<>();

    private final Map<String, Map<Local, FunctionLocalSummary<InMemCheckResult>>>
            localInMemorySummaryMap = new HashMap<>();

    private final Map<String, Map<SootField, FunctionFieldSummary<InMemCheckResult>>>
            fieldInMemorySummaryMap = new HashMap<>();

    private final Map<SootClass, Map<SootField, FunctionFieldSummary<InMemCheckResult>>>
            fieldInMemoryClassSummaryMap = new HashMap<>();

    private final Map<SootMethod, MethodLocalDefUses> defUsesMap = new HashMap<>();

    // Rules that specify a method return-parameter relationship, e.g., for external library methods.
    private final Map<String, List<ParameterRef>> inMemRetParamRules = new HashMap<>();
    // Rules that specify a method operation-parameter relationship, e.g., for external library methods.
    private final Map<String, List<ParameterRef>> inMemOpParamRules = new HashMap<>();
    // Rules that specify a method constructor-parameter relationship,
    private final Map<String, List<ParameterRef>> inMemCtorParamRules = new HashMap<>();

    private CallSiteAnalysis callSiteAnalysis;
    private FieldRefDefsAnalysis fieldRefDefsAnalysis;

    public InMemoryUnitAnalysis(String[] package_prefix_list, CallSiteAnalysis csa,
            FieldRefDefsAnalysis fda) {
        this.package_prefix_list = package_prefix_list;
        callSiteAnalysis = csa;
        fieldRefDefsAnalysis = fda;
    }

    public boolean isImpossibleInMemType(Type type) {
        // if this is not a reference type, e.g., int, it is impossible to be an in-mem type
        if (!(type instanceof RefType))
            return true;
        RefType rtype = (RefType) type;
        String clzName = rtype.getClassName();
        // Should be very conservative with the class name rule here. Even if a class name does
        // not start with java.io, it could still be in-mem type, e.g., an application class
        // BinaryOutputArchive that internally has an outputstream.
        return clzName.startsWith("java.") && !(clzName.startsWith("java.io")
                || clzName.startsWith("java.nio"));
    }

    /**
     * Process the application classes to enable the in-memory unit analysis.
     *
     * @param classes the application classes to be checked
     */
    @SuppressWarnings("unused")
    public void analyze(Chain<SootClass> classes) {
        //  For now, we don't do any processing, since it is cheaper to perform the check on-demand
        //  with various summaries to cache the analysis results.
    }

    /**
     * Add custom summary rules about the in-memory status of a method's return value or internal
     * operations.
     *
     * If the rule is regarding the return value, it specifies that a particular parameter
     * decides if the return value will be an in-memory object.
     *
     * If the rule is regarding the internal operations, it specifies that a particular parameter
     * decides whether the method will operate on an in-memory object or not.
     *
     * If the rule is regarding the construcor, it specifies that a particular parameter
     * decides if a new instance of the associated class will be an in-memory object.
     *
     * This is espeically useful for external library function that we do not analyze but is
     * used frequently inside the application.
     *
     * @param ruleType Type of the rule
     * @param method The method whose return value is to be checked
     * @param paramIndexes The list of parameter indexes that can determine whether a return value
     *                     is in-memory or not.
     */
    public void addInMemMethodParamRule(MethodParamRuleType ruleType, SootMethod method, int... paramIndexes) {
        Map<String, List<ParameterRef>> map =  getMethodParamRuleMap(ruleType);
        if (map == null)
            return;
        List<ParameterRef> rule = map.computeIfAbsent(
                method.getSignature(), k -> new ArrayList<>(paramIndexes.length));
        List<Type> types = method.getParameterTypes();
        for (int index : paramIndexes) {
            if (index >= 0 && index < types.size())
                rule.add(new ParameterRef(types.get(index), index));
        }
    }

    public void addInMemMethodParamRule(MethodParamRuleType ruleType, String methodSignature, List<ParameterRef> refs) {
        Map<String, List<ParameterRef>> map =  getMethodParamRuleMap(ruleType);
        if (map == null)
            return;
        List<ParameterRef> rule = map.computeIfAbsent(methodSignature,
                k -> new ArrayList<>());
        rule.addAll(refs);
    }

    public Map<String, List<ParameterRef>> getMethodParamRuleMap(MethodParamRuleType ruleType) {
        switch (ruleType) {
            case RETURN: return inMemRetParamRules;
            case OPERATION: return inMemOpParamRules;
            case CONSTRUCTOR: return inMemCtorParamRules;
        }
        return null;
    }

    /**
     * Add custom in-memory object types. This is helpful for applications that use custom I/O wrapper
     * implemented using third-party libraries that we do not analyze.
     *
     * If an application implements custom I/O wrappers on top of standard Java I/O class, our analysis
     * is able to handle them. Thus, their types do not need to be added here.
     *
     * @param typeNames The names of these custom in-memory object types
     */
    public void addCustomInMemTypes(String... typeNames) {
        inMemTypes.addAll(Arrays.asList(typeNames));
    }

    public enum MethodParamRuleType {
        RETURN,
        OPERATION,
        CONSTRUCTOR
    }

    public enum InMemCheckResult {
        MAY_IN_MEMORY,
        MUST_IN_MEMORY,
        NOT_IN_MEMORY,
        MAX_DEPTH_REACHED,
        NO_DEF_FOUND
    }

    /**
     * Check if an invocation instruction is invoking on an in-memory object. This method performs
     * a best-effort inter-procedural analysis. In particular, if the definition point for the
     * invocation instruction's object comes from the enclosing method's parameters, it
     * examines call sites of the method and determine if some call-site supplies an in-memory
     * object as the argument. It does this check recursively.
     *
     * @param expr The invocation expression to be checked
     * @param unit The unit that contains this invocation expression
     * @param method The method that contains the unit
     *
     * @return If this invocation point <bold>MAY</bold> be operating on an in-memory object
     */
    public boolean checkInvokeInMemObj(InvokeExpr expr, Unit unit, SootMethod method) {
        SootMethod target = expr.getMethod();

        List<ParameterRef> rule = inMemOpParamRules.get(target.getSignature());
        if (rule != null && !rule.isEmpty()) {
            LOG.debug("Direct rule check on parameter of '" + target.getSignature() + "'");
            for (ParameterRef pref : rule) {
                Value arg = expr.getArg(pref.getIndex());
                if (arg instanceof Local) {
                    InMemCheckResult result = checkLocalInMemObj((Local) arg, unit, method, 0);
                    if (result == InMemCheckResult.MAY_IN_MEMORY ||
                            result == InMemCheckResult.MUST_IN_MEMORY) {
                        return true;
                    }
                } else {
                    LOG.error("Argument {} for invocation {} is not a local", arg, expr);
                }
            }
            return false;
        } else {
            if (!(expr instanceof InstanceInvokeExpr))
                return false;
            InstanceInvokeExpr iie = (InstanceInvokeExpr) expr;
            Value base = iie.getBase();
            InMemCheckResult result = checkLocalInMemObj((Local) base, unit, method, 0);
            return result == InMemCheckResult.MAY_IN_MEMORY
                    || result == InMemCheckResult.MUST_IN_MEMORY;
        }
    }

    /**
     * Check if a local at a unit in a method is potentially an in-memory object.
     *
     * @param local The local variable to be analyzed
     * @param unit The unit in which the local is used
     * @param method The method in which the unit resides
     * @param depth The current analysis algorithm's stack depth
     *
     * @return A result indicating whether the local is an in-memory object
     */
    protected InMemCheckResult checkLocalInMemObj(Local local, Unit unit, SootMethod method, int depth) {
        if (depth >= IN_MEM_CHECK_MAX_DEPTH) {
            LOG.error("Reached max depth in the in-memory check for {} in {}", local, method.getSignature());
            return InMemCheckResult.MAX_DEPTH_REACHED;
        }
        depth++;

        String localStr = method.getSignature() + "@" + unit.getJavaSourceStartLineNumber()
                + ":" + local;

        Map<Local, FunctionLocalSummary<InMemCheckResult>> lmap =
                localInMemorySummaryMap.computeIfAbsent(method.getSignature(),
                        k -> new HashMap<>());
        FunctionLocalSummary<InMemCheckResult> summary = lmap.computeIfAbsent(local,
                k -> new FunctionLocalSummary<>(k, unit));
        if (summary.computed) {
            return summary.summary;
        }
        summary.summary = InMemCheckResult.NOT_IN_MEMORY;
        MethodLocalDefUses defUses = defUsesMap.computeIfAbsent(method, k ->
                MethodLocalDefUses.build(k, false));

        List<Unit> defs = defUses.ld.getDefsOfAt(local, unit);
        if (defs.isEmpty()) {
            summary.summary = InMemCheckResult.NO_DEF_FOUND;
            summary.computed = true;
            return summary.summary;
        }
        boolean cacheable = true;
        for (Unit def : defs) {
            if (!(def instanceof DefinitionStmt)) {
                LOG.warn("Def for local (" + localStr + ") is not a DefinitionStmt: " + def);
                continue;
            }
            DefinitionStmt definitionStmt = (DefinitionStmt) def;
            if (definitionStmt.getRightOp() instanceof ParameterRef) {
                // If this local depends on some parameter, we should not cache the result,
                // because each call site's in-mem check result might be different.
                cacheable = false;
            }
            InMemCheckResult result = checkValueDefInMemObj(definitionStmt, localStr, method, depth);
            if (result == InMemCheckResult.MAY_IN_MEMORY ||
                    result == InMemCheckResult.MUST_IN_MEMORY) {
                summary.summary = result;
                summary.defs.add(definitionStmt);
                break;
            }
        }
        if (cacheable)
            summary.computed = true;
        return summary.summary;
    }

    /**
     * Check if value definition is potentially an in-memory object.
     *
     * @param def The definition statement for the value
     * @param valueStr The human-readable string of the value
     * @param method The method in which the value comes from
     * @param depth The current analysis algorithm's stack depth
     *
     * @return A result indicating whether the local is an in-memory object
     */
    protected InMemCheckResult checkValueDefInMemObj(DefinitionStmt def, String valueStr,
            SootMethod method, int depth) {
        Value rhs = def.getRightOp();
        Type type = rhs.getType();

        // Do a simple type check first. If it's already an in-memory object type,
        // we can simply return without performing additional analysis
        if (inMemTypes.contains(type.toString()))
            return InMemCheckResult.MUST_IN_MEMORY;
        // If the type is impossible to be an in-memory object type, we can also return now
        if (isImpossibleInMemType(type))
            return InMemCheckResult.NOT_IN_MEMORY;

        if (rhs instanceof NewExpr) {
            NewExpr nexpr = (NewExpr) rhs;
            String clzName = nexpr.getBaseType().getClassName();
            // If the definition point is a new instance of an in-memory object type
            if (inMemTypes.contains(clzName))
                return InMemCheckResult.MUST_IN_MEMORY;

            // Otherwise, this could still be an in-memory object if the constructor's
            // parameter decides the in-memory status, e.g., a DataOutputStream object.
            // We need to check the NEXT invoke instruction the immediately follows the
            // NewExpr to find out what is the constructor and what argument is passed.
            if (method.hasActiveBody()) {
                Unit nextUnit = method.getActiveBody().getUnits().getSuccOf(def);
                if (nextUnit instanceof InvokeStmt) {
                    InvokeExpr invokeExpr = ((InvokeStmt) nextUnit).getInvokeExpr();
                    SootMethod targetMethod = invokeExpr.getMethod();
                    List<ParameterRef> rule = inMemCtorParamRules.get(targetMethod.getSignature());
                    if (rule != null) {
                        LOG.debug("Direct rule check on constructor of '{}'", targetMethod.getSignature());
                        // This is indeed a constructor-dependent class
                        for (ParameterRef ref : rule) {
                            Value arg = invokeExpr.getArg(ref.getIndex());
                            if (arg instanceof Local) {
                                InMemCheckResult result = checkLocalInMemObj((Local) arg,
                                        nextUnit, method, depth);
                                if (result == InMemCheckResult.MAY_IN_MEMORY ||
                                        result == InMemCheckResult.MUST_IN_MEMORY) {
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
            return InMemCheckResult.NOT_IN_MEMORY;
        } else if (rhs instanceof ParameterRef) {
            ParameterRef pref = (ParameterRef) rhs;
            LOG.debug("Value (" + valueStr + ") depends on parameter " + pref.getIndex());
            return checkMethodParamInMemObj(pref, method, depth);
        } else if (rhs instanceof IdentityRef) {
            IdentityRef iref = (IdentityRef) rhs;
            LOG.debug("Value (" + valueStr + ") depends on 'this' of type " + iref.getType());
            // We have done simple check on the identity's type.
            // If not, it's still possible that 'this' can be an in-memory object, e.g.,
            // some instance creation could pass an in-memory field.
            // FIXME: check the instance creation sites if necessary
        } else if (rhs instanceof InvokeExpr) {
            // The object comes from the return value of another invocation point
            // Check the return value for this specific call site
            InvokeExpr exp = (InvokeExpr) rhs;
            LOG.debug("Value (" + valueStr + ") depends on return value of " + exp);
            return checkCallRetInMemObj(exp, def, method, depth);
        } else if (rhs instanceof Local) {
            LOG.debug("Value (" + valueStr + ") depends on another local");
            // In this case, we should check the definition of this rhs local recursively
            // to determine if rhs is an in-memory local.
            return checkLocalInMemObj((Local) rhs, def, method, depth);
        } else if (rhs instanceof FieldRef) {
            FieldRef fref = (FieldRef) rhs;
            LOG.debug("Value (" + valueStr + ") depends on class field");
            // Checking if this field reference is defined in this method or constructor
            return checkFieldRefInMemObj(fref, def, method, depth);
        } else {
            LOG.warn("Unhandled def for value (" + valueStr + "): " + def
                    + ", " + rhs.getClass());
        }
        return InMemCheckResult.NO_DEF_FOUND;
    }

    /**
     * Check if a field reference in a unit is potentially backed by an in-memory object.
     *
     * @param ref The field reference to be checked
     * @param unit The unit in which the reference is used
     * @param method The method that contains the unit
     * @param depth The current analysis algorithm's stack depth
     *
     * @return A result indicating whether the local is an in-memory object
     */
    protected InMemCheckResult checkFieldRefInMemObj(FieldRef ref, Unit unit, SootMethod method,
            int depth) {
        if (depth >= IN_MEM_CHECK_MAX_DEPTH) {
            LOG.error("Reached max depth in the in-memory check for {} in {}", ref, method.getSignature());
            return InMemCheckResult.MAX_DEPTH_REACHED;
        }
        depth++;

        String refStr = method.getSignature() + "@" + unit.getJavaSourceStartLineNumber() + ":" + ref;

        Map<SootField, FunctionFieldSummary<InMemCheckResult>> fmap =
                fieldInMemorySummaryMap.computeIfAbsent(method.getSignature(), k -> new HashMap<>());

        FunctionFieldSummary<InMemCheckResult> summaryFunc, summaryClz;

        summaryFunc = fmap.computeIfAbsent(ref.getField(), k -> new FunctionFieldSummary<>(ref, unit));
        if (summaryFunc.computed) {
            return summaryFunc.summary;
        }

        List<Unit> localDefs = fieldRefDefsAnalysis.getDefsOfAt(ref, unit, method);
        Map<SootMethod, List<Unit>> methodDefMap = new HashMap<>();

        if (localDefs.isEmpty()) {
            SootClass fclz = method.getDeclaringClass();
            fmap = fieldInMemoryClassSummaryMap.computeIfAbsent(fclz, k -> new HashMap<>());
            summaryClz = fmap.computeIfAbsent(ref.getField(), k -> new FunctionFieldSummary<>(ref, unit));
            if (summaryClz.computed) {
                return summaryClz.summary;
            }
            FieldClassDef classDef = fieldRefDefsAnalysis.getDefsOfInClass(ref, fclz);
            if (classDef.methodDefMap.isEmpty()) {
                summaryClz.summary = InMemCheckResult.NO_DEF_FOUND;
                summaryClz.computed = true;
                return summaryClz.summary;
            }
            methodDefMap = classDef.methodDefMap;
        } else {
            summaryClz = null;
            methodDefMap.put(method, localDefs);
        }

        boolean found = false;
        for (Map.Entry<SootMethod, List<Unit>> entry : methodDefMap.entrySet()) {
            List<Unit> defs = entry.getValue();
            for (Unit def : defs) {
                if (!(def instanceof DefinitionStmt))
                    continue;
                DefinitionStmt definitionStmt = (DefinitionStmt) def;
                InMemCheckResult result = checkValueDefInMemObj(definitionStmt, refStr, entry.getKey(), depth);
                if (result == InMemCheckResult.MAY_IN_MEMORY || result == InMemCheckResult.MUST_IN_MEMORY) {
                    summaryFunc.summary = result;
                    summaryFunc.defs.add(definitionStmt);
                    if (summaryClz != null) {
                        summaryClz.summary = result;
                        summaryFunc.defs.add(definitionStmt);
                    }
                    found = true;
                    break;
                }
            }
            if (found)
                break;
        }
        summaryFunc.computed = true;
        if (summaryClz != null)
            summaryClz.computed = true;
        return summaryFunc.summary;
    }

    /**
     * Check if a parameter of a method is potentially an in-memory object.
     *
     * @param ref The parameter reference
     * @param method The method that the parameter belongs to
     * @param depth The current analysis algorithm's stack depth
     *
     * @return A result indicating whether the local is an in-memory object
     */
    protected InMemCheckResult checkMethodParamInMemObj(ParameterRef ref,
            SootMethod method, int depth) {
        if (depth >= IN_MEM_CHECK_MAX_DEPTH) {
            LOG.error("Reached max depth in the in-memory check for {} in {}", ref, method.getSignature());
            return InMemCheckResult.MAX_DEPTH_REACHED;
        }
        depth++;
        int index = ref.getIndex();

        List<SootCallSite> callSites = callSiteAnalysis.getCallSites(method.getSignature());

        Map<Integer, FunctionParamSummary<InMemCheckResult>> map =
                paramInMemorySummaryMap.computeIfAbsent(
                        method.getSignature(), k -> new HashMap<>());
        FunctionParamSummary<InMemCheckResult> summary = map.computeIfAbsent(index, k ->
                new FunctionParamSummary<>(method, index));
        if (summary.computed) {
            // A summary has been computed before, directly use the result
            return summary.summary;
        }
        // No summary is present, compute it
        LOG.debug("Call sites for " + method.getSignature());
        summary.summary = InMemCheckResult.NOT_IN_MEMORY;
        if (callSites == null || callSites.isEmpty()) {
            // No call sites found, we are not going to get lucky next time, so
            // we consider this as computed.
            summary.computed = true;
            LOG.warn("No call sites found for " + method.getSignature() + " to check param " + index);
            return summary.summary;
        }

        for (SootCallSite callSite : callSites) {
            LOG.debug("\t cs - '" + callSite.expr + "' in " + callSite.method
                    + "@line " + callSite.lineNo);
            if (callSite.method == method) {
                LOG.info("Recursion detected, skip further analysis for call site: " + callSite.expr);
                continue;
            }
            if (index >= 0 && index < callSite.expr.getArgCount()) {
                Value argument = callSite.expr.getArg(index);
                if (argument instanceof Local) {
                    InMemCheckResult result = checkLocalInMemObj((Local) argument, callSite.unit,
                            callSite.method, depth);
                    if (result == InMemCheckResult.MAY_IN_MEMORY ||
                            result == InMemCheckResult.MUST_IN_MEMORY) {
                        summary.summary = result;
                        // record the call site that passes the in-memory object in the summary
                        summary.callSites.add(callSite);
                        LOG.debug("'" + callSite.expr + "' in " + callSite.method + "@line " +
                                callSite.lineNo + " passes an in-memory object at arg " + index);
                        break;
                    }
                }
            } else {
                LOG.error("Call site " + callSite.expr + " in " + callSite.method +
                        " has wrong arg count");
            }

        }
        summary.computed = true;
        return summary.summary;
    }

    /**
     * Check if a return value from a call site is an in-memory object.
     *
     * @param expr The invocation expression to be checked
     * @return true if the return value is an in-memory object; false otherwise
     */
    protected InMemCheckResult checkCallRetInMemObj(InvokeExpr expr, Unit unit,
            SootMethod method, int depth) {
        if (depth >= IN_MEM_CHECK_MAX_DEPTH) {
            LOG.error("Reached max depth in the in-memory check for {} in {}", expr, method.getSignature());
            return InMemCheckResult.MAX_DEPTH_REACHED;
        }
        depth++;

        SootMethod target = expr.getMethod();
        Type retType = target.getReturnType();
        if (inMemTypes.contains(retType.toString())) {
            // if the method's return type is an in-memory object type
            // then this return value is an in-memory object
            return InMemCheckResult.MUST_IN_MEMORY;
        }

        List<ParameterRef> rule = inMemRetParamRules.get(target.getSignature());
        if (rule == null && !target.hasActiveBody()) {
            // if this method's body is unknown, e.g., an external library function,
            // and we do not have a known rule to handle the analysis of its return value,
            // then we cannot perform the following analysis
            return InMemCheckResult.NOT_IN_MEMORY;
        }

        if (rule != null) {
            // Should not use cache for custom rules
            LOG.debug("Direct rule check on return value of '" + target.getSignature() + "'");
            for (ParameterRef pref : rule) {
                // Based on the custom rule, the i-th parameter determines the called method's
                // return value's in-mem status. Note that here we directly check the argument
                // since we are analyzing an invocation instruction. We should NOT call
                // checkMethodParamInMemObj, because that will incorrectly analyze other call sites
                // that are irrelevant to this invocation.
                Value arg = expr.getArg(pref.getIndex());
                if (arg instanceof Local) {
                    InMemCheckResult result = checkLocalInMemObj((Local) arg, unit, method, depth);
                    if (result == InMemCheckResult.MAY_IN_MEMORY ||
                            result == InMemCheckResult.MUST_IN_MEMORY) {
                        return result;
                    }
                } else {
                    LOG.error("Argument {} for invocation {} is not a local", arg, expr);
                }
            }
            return InMemCheckResult.NOT_IN_MEMORY;
        } else {
            // If the called target method is not determined by one of the custom rules,
            // we need to scan the target method body and analyze its return statements.
            // There are two possible scenarios: (1) the return value can be determined
            // purely locally in the target method; (2) the return value depends on one of
            // the target method's parameter. For (1), we can cache the result; for (2),
            // we should not cache the result, and we should use the specific argument
            // we have now to analyze the in-mem status.

            FunctionRetSummary<InMemCheckResult> summary = retInMemorySummaryMap.computeIfAbsent(
                target.getSignature(), k -> new FunctionRetSummary<>(target));
            if (summary.computed)
                return summary.summary;

            MethodLocalDefUses defUses = defUsesMap.computeIfAbsent(target, k ->
                    MethodLocalDefUses.build(k, false));

            summary.summary = InMemCheckResult.NOT_IN_MEMORY;

            Body targetBody = target.retrieveActiveBody();
            List<ParameterRef> newRule = new LinkedList<>();
            for (Unit targetUnit : targetBody.getUnits()) {
                if (!(targetUnit instanceof ReturnStmt))
                    continue;
                ReturnStmt returnStmt = (ReturnStmt) targetUnit;
                Value targetRet = returnStmt.getOp();
                if (!(targetRet instanceof Local))
                    continue;
                // The valued returned comes from a local in-mem obj
                Local local = (Local) targetRet;

                // Here we should do a quick check on if the return depends on the parameter.
                // If so, we must use the specific call-site from 'expr' to check the particular
                // parameter we are passing. Otherwise, we could get wrong result, and we could
                // incorrectly cache the result.
                List<Unit> defs = defUses.ld.getDefsOfAt(local, returnStmt);
                InMemCheckResult result = InMemCheckResult.NO_DEF_FOUND;
                boolean foundInMem = false;
                for (Unit defUnit : defs) {
                    if (!(defUnit instanceof DefinitionStmt))
                        continue;
                    DefinitionStmt definitionStmt = (DefinitionStmt) defUnit;
                    Value rhs = definitionStmt.getRightOp();
                    if (rhs instanceof ParameterRef) {
                        // add this ref to our new rule
                        newRule.add((ParameterRef) rhs);
                        Value arg = expr.getArg(((ParameterRef) rhs).getIndex());
                        if (arg instanceof Local) {
                            // In this case, we should check the specific argument passed
                            // in the invoke expression and its enclosing method
                            result = checkLocalInMemObj((Local) arg, unit, method, depth);
                        }
                    } else {
                        String localStr = method.getSignature() + "@" + targetUnit.getJavaSourceStartLineNumber()
                                + ":" + local;
                        result = checkValueDefInMemObj(definitionStmt, localStr, target, depth);
                    }
                    if (result == InMemCheckResult.MAY_IN_MEMORY ||
                            result == InMemCheckResult.MUST_IN_MEMORY) {
                        foundInMem = true;
                        break;
                    }
                }
                if (foundInMem) {
                    if (!newRule.isEmpty()) {
                        // We should directly return now.
                        return result;
                    } else {
                        // Only cache the result if the return is not param dependent
                        summary.summary = result;
                        summary.returns.add(returnStmt);
                        break;
                    }
                }
            }
            if (newRule.isEmpty()) {
                // Only consider the summary to be computed if the return value is not param dependent
                summary.computed = true;
            }
            else {
                // We just discovered a parameter-return rule for the target method, cache it
                inMemRetParamRules.put(target.getSignature(), newRule);
            }
            return summary.summary;
        }
    }

    public static List<SootClass> findInMemStandLibIOWrappers(Chain<SootClass> classes,
                String... prefix_filters) {
        List<SootClass> ioWrappers = SootUtils.findStandardIoWrappers(classes, prefix_filters);
        List<SootClass> inMemWrappers = new LinkedList<>();

        Set<String> lib_in_mem_types = new HashSet<>(JAVA_LIB_IN_MEM_TYPE);
        lib_in_mem_types.add("byte[]"); // also treat primitive byte array as in-mem

        for (SootClass wrapper : ioWrappers) {
            boolean has_in_mem_param = false;
            boolean has_ref_type = false;
            for (SootMethod method : wrapper.getMethods()) {
                if (method.getName().equals("<init>")) {
                    List<Type> paramTypes = method.getParameterTypes();
                    for (Type type : paramTypes) {
                        if (lib_in_mem_types.contains(type.toString())) {
                            has_in_mem_param = true;
                        } else if (type instanceof RefType) {
                            has_ref_type = true;
                        }
                    }
                }
            }
            // Here we should be very conservative to decide if a class is **definitely**
            // an in-memory class: other than primitive types, the only type of parameters
            // used in all of the class' constructors is some 'lib_in_mem_types'. Otherwise,
            // as long as one of the constructors has a RefType parameter, we treat this
            // class as potentially non-in-memory.
            if (has_in_mem_param && !has_ref_type) {
                LOG.info("Found custom IO wrapper that is in-mem type: " + wrapper.getName());
                inMemWrappers.add(wrapper);
            }
        }
        return inMemWrappers;
    }
}
