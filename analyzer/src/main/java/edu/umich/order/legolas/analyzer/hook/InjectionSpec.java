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
package edu.umich.order.legolas.analyzer.hook;

import edu.umich.order.legolas.common.fault.InjectionFault;
import edu.umich.order.legolas.common.fault.InjectionFault.FaultType;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;
import soot.SootMethod;

/**
 * Simple injection specification rule supplied by the users to indicate what type of exceptions
 * or errors or operations is of interest as the injection target.
 */
public final class InjectionSpec {
    private static final Logger LOG = LoggerFactory.getLogger(InjectionSpec.class);

    protected static final InjectionOpSpec EMPTY_OP = new InjectionOpSpec("", "", "");

    boolean fuzzyScope;
    public String scopes;
    public InjectionOpSpec[] ops;
    public String[] faults;

    public String spec_str;

    public InjectionSpec(String scope, InjectionOpSpec[] ops, String[] faults, String spec_str) {
        if (scope.indexOf('*') >= 0) {
            fuzzyScope = true;
            scopes = normalizeRegex(scope, true);
        } else {
            fuzzyScope = false;
            scopes = scope;
        }
        this.ops = ops;
        this.faults = faults;
        this.spec_str = spec_str;
    }

    public static class InjectionOpSpec {
        public String className;
        public String methodName;
        public String signature;

        public boolean fuzzyClzz;
        public boolean fuzzyMethod;
        public boolean anyClzz;
        public boolean anyMethod;

        protected boolean empty;
        protected boolean wildcard;

        public InjectionOpSpec(String cls, String method, String signature) {
            className = cls;
            methodName = method;
            this.signature = signature;
            anyClzz = cls.isEmpty() || cls.equals("*");
            anyMethod = method.isEmpty() || method.equals("*");
            fuzzyClzz = !anyClzz && cls.indexOf('*') >= 0;
            fuzzyMethod = !anyMethod && method.indexOf('*') >= 0;
            if (fuzzyClzz)
                className = normalizeRegex(cls, true);
            if (fuzzyMethod)
                methodName = normalizeRegex(method, false);
            empty = cls.isEmpty() && method.isEmpty();
            wildcard = (cls.isEmpty() && method.equals("*")) || (cls.equals("*") && method.isEmpty());
        }

        public boolean isEmpty() {
            return empty;
        }

        public boolean matchAny() {
            return wildcard;
        }

        @Override
        public String toString() {
            return signature;
        }
    }

    /**
     * Normalize the regex string. In particular, turn 'xxx*' => 'xxx.*' and 'xx.yy.*' =>
     * 'xx\.yy\..*'
     *
     * @param str
     * @param escapeDot
     * @return
     */
    public static String normalizeRegex(String str, boolean escapeDot) {
        int len = str.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '*') {
                if (i == 0 || str.charAt(i - 1) != '.' || escapeDot) {
                    sb.append('.');
                }
            } else if (c == '.' && escapeDot) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public List<InjectionFault> match(String scope, String invokedMethodSig, String invokeMethodName,
            String invokeClassName, String[] exceptionNames) {
        boolean match_op = false;
        for (InjectionOpSpec o : ops) {
            // if the op is empty (omitted) or wildcard or exactly matching the
            // method signature or method name, the invocation matches
            if (o.isEmpty() || o.matchAny() || o.signature.equals(invokedMethodSig)
                    || (o.anyClzz && o.methodName.equals(invokeMethodName))
                    || (o.anyMethod && o.className.equals(invokeClassName))) {
                match_op = true;
                break;
            }
            boolean matchClass = o.anyClzz || (o.fuzzyClzz && invokeClassName.matches(o.className)) ||
                    o.className.equals(invokeClassName);
            boolean matchMethod = o.anyMethod || (o.fuzzyMethod && invokeMethodName.matches(o.methodName)) ||
                    o.methodName.equals(invokeMethodName);
            if (matchClass && matchMethod) {
                match_op = scopes.isEmpty() || scope.equals(scopes) ||
                        (fuzzyScope && scope.matches(scopes));
                break;
            }
        }
        // op must match
        if (!match_op)
            return null;
        List<InjectionFault> match_results = new ArrayList<>();
        if (exceptionNames != null) {
            for (String exceptionName : exceptionNames) {
                for (String f : faults) {
                    if (f.isEmpty() || f.equals("*") || f.equals(exceptionName)) {
                        // caller will update eid
                        match_results.add(new InjectionFault(FaultType.EXCEPTION, null,
                                exceptionName, -1));
                    }
                }
            }
        }
        for (String f : faults) {
            // specially handle delay fault injection
            if (f.equalsIgnoreCase("delay")) {
                match_results.add(new InjectionFault(FaultType.DELAY, null, "delay", -1));
            }
        }
        return match_results;
    }

    /**
     * Check if a callsite should be injected or not
     *
     * @param invokeMethod
     * @return
     */
    public List<InjectionFault> match(String scope, final SootMethod invokeMethod) {
        List<SootClass> exceptions = invokeMethod.getExceptions();
        String[] exceptionNames = new String[exceptions.size()];
        for (int i = 0; i < exceptions.size(); i++) {
            exceptionNames[i] = exceptions.get(i).getName();
        }
        return match(scope, invokeMethod.getSignature(), invokeMethod.getName(),
                invokeMethod.getDeclaringClass().getName(), exceptionNames);
    }

    /**
     * Parse an operation spec. An operation spec could be a full signature
     * '<className: methodName([paramTypes])>' or just 'className:methodName()'
     *
     * @param op_str
     * @return
     */
    public static InjectionOpSpec parseOp(String op_str) {
        if (op_str.isEmpty())
            return EMPTY_OP;
        int len1 = op_str.length();
        String real_str;
        if (op_str.charAt(0) == '<' && op_str.charAt(len1 - 1) == '>') {
            real_str = op_str.substring(1, len1 - 1);
        } else {
            real_str = op_str;
        }
        int idx1 = real_str.indexOf(':');
        String clzzName, methodName;
        if (idx1 >= 0) {
            clzzName = real_str.substring(0, idx1);
            if (idx1 < real_str.length() - 1) {
                if (real_str.charAt(idx1 + 1) == ' ') {
                    idx1 = idx1 + 2;
                } else {
                    idx1 = idx1 + 1;
                }
                if (idx1 >= real_str.length())
                    return null;
                int idx2 = real_str.indexOf('(');
                if (idx2 >= 0) {
                    if (real_str.charAt(real_str.length() - 1) != ')')
                        return null;
                    methodName = real_str.substring(idx1, idx2);
                }
                else {
                    methodName = real_str.substring(idx1);
                }
                String[] parts = methodName.split(" ", 2);
                if (parts.length >= 2)
                    methodName = parts[1];
            } else {
                methodName = ""; // any method of this class
            }
        } else {
            int idx2 = real_str.indexOf('(');
            if (idx2 >= 0) {
                if (real_str.charAt(real_str.length() - 1) != ')')
                    return null;
                int idx3 = real_str.lastIndexOf('.');
                if (idx3 >= 0) {
                    if (idx3 + 1 >= real_str.length())
                        return null;
                    clzzName = real_str.substring(0, idx3);
                    methodName = real_str.substring(idx3 + 1, idx2);
                } else {
                    clzzName = "";
                    methodName = real_str.substring(0, idx2);
                }
            } else {
                clzzName = real_str;
                methodName = ""; // any method of this class
            }
        }
        return new InjectionOpSpec(clzzName, methodName, op_str);
    }

    /**
     * Parse an injection spec string, and construct a spec instance.
     *
     * An injection spec string is of format "scope^op^fault", which means injecting exception/delay
     * fault to invocation op. The spec supports omission of op or fault, i.e., "^fault"
     * (any invocation that throws type "fault") or "op^" (any fault that is thrown with
     * invocation "op"). It also supports wildcard and multiple op/faults, e.g., "*^fault"
     * or "{op1,op2}^fault" or "{op1,op2}^{fault1,fault2}".
     *
     * @param spec_str
     * @return
     */
    public static InjectionSpec parse(String spec_str) {
        String scope_str, ops_str, faults_str;
        int idx1 = -1, idx2 = -1;
        for (int i = 0; i < spec_str.length(); i++) {
            if (spec_str.charAt(i) == '^') {
                if (idx1 < 0)
                    idx1 = i;
                else {
                    idx2 = i;
                    break;
                }
            }
        }
        if (idx1 >= 0 && idx2 >= 0) {
            scope_str = spec_str.substring(0, idx1);
            if (idx1 + 1 < idx2)
                ops_str = spec_str.substring(idx1+ 1, idx2);
            else
                ops_str = "";
            if (idx2 + 1 < spec_str.length())
                faults_str = spec_str.substring(idx2 + 1);
            else
                faults_str = "";
        } else {
            scope_str = ""; // any scope
            if (idx1 >= 0) {
                ops_str = spec_str.substring(0, idx1);
                if (idx1 + 1 < spec_str.length())
                    faults_str = spec_str.substring(idx1 + 1);
                else
                    faults_str = "";
            }
            else {
                ops_str = spec_str;
                faults_str = "";
            }
        }
        int sz1 = ops_str.length(), sz2 = faults_str.length();
        if (sz1 > 0 && ops_str.charAt(0) == '{' && ops_str.charAt(sz1 - 1) == '}') {
            ops_str = ops_str.substring(1, sz1 - 1);
        }
        if (sz2 > 0 && faults_str.charAt(0) == '{' && faults_str.charAt(sz2 - 1) == '}') {
            faults_str = faults_str.substring(1, sz2 - 1);
        }
        String[] op_strs = ops_str.split(",");
        InjectionOpSpec[] ops = new InjectionOpSpec[op_strs.length];
        for (int i = 0; i < ops.length; i++)
            ops[i] = parseOp(op_strs[i]);
        return new InjectionSpec(scope_str, ops, faults_str.split(","), spec_str);
    }

    /**
     * Parse a list of injection spec strings and construct a list of spec instances.
     *
     * @param spec_strs
     * @return
     */
    public static List<InjectionSpec> parse(String[] spec_strs) {
        List<InjectionSpec> specs = new ArrayList<>();
        for (String spec_str : spec_strs) {
            InjectionSpec spec = parse(spec_str);
            if (spec != null)
                specs.add(spec);
        }
        return specs;
    }

    /**
     * Parse an injection spec file line by line and construct a list of spec instances.
     *
     * @param spec_file
     * @return
     */
    public static List<InjectionSpec> parse(File spec_file) {
        List<InjectionSpec> specs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(spec_file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                InjectionSpec spec = parse(line);
                if (spec != null)
                    specs.add(spec);
            }
        } catch (FileNotFoundException e) {
            LOG.error("Failed to find injection spec file " + spec_file, e);
        } catch (IOException e) {
            LOG.error("I/O error in reading spec file" + spec_file, e);
        }
        return specs;
    }

    @Override
    public String toString() {
        return spec_str;
    }
}
