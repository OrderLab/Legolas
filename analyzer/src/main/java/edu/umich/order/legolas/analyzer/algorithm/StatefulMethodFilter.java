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

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;

/**
 * Analyze if a method invocation is "stateful", which just means that invoked method is not
 * some basic Java library calls such as toString and the method performs some meaningful
 * system actions.
 */
public final class StatefulMethodFilter {

    private final String[] package_prefix_list;

    public StatefulMethodFilter(final String[] package_prefix_list) {
        this.package_prefix_list = package_prefix_list;
    }

    /*
     * Pipeline: check the whitelists and the blacklists
     * todo: refactor the rules into pipelines
     *
     * @param unit
     * @return
     */
    public boolean filter(final Unit unit) {
        for (final ValueBox valueBox: unit.getUseBoxes()) {
            final Value value = valueBox.getValue();
            if (!(value instanceof InvokeExpr)) continue;
            final SootMethod invokeMethod = ((InvokeExpr) value).getMethod();
            final String methodName = invokeMethod.getName();
            final String className = invokeMethod.getDeclaringClass().toString();
            if (Arrays.stream(package_prefix_list).anyMatch(className::startsWith)) {
                if (className.endsWith("Exception")) return true;
                if (className.endsWith("Error")) return true;
                if (methodName.startsWith("access$")) return true;
                if (methodName.startsWith("get")) return true;
                if (methodName.startsWith("is")) return true;
                if (methodName.equals("compare")) return true;
                if (methodName.equals("hashCode")) return true;
                if (methodName.equals("iterator")) return true;
                if (methodName.equals("toString")) return true;
                return false;
            }
            if (className.startsWith("java")) {
                switch (className) {
                    case "java.lang.Process": return !(methodName.equals("waitFor") ||
                            methodName.equals("destroy"));
                    case "java.lang.ProcessBuilder": return !methodName.equals("start");
                    case "java.lang.Runtime": return !methodName.equals("exec");
                    case "java.lang.System": return !methodName.equals("exit");
                    case "java.lang.Thread": return !(methodName.equals("start") ||
                            methodName.equals("interrupt"));
                    case "java.net.ServerSocket": return !(methodName.equals("accept") ||
                            methodName.equals("bind") || methodName.equals("close"));
                    case "java.util.Timer": return !(methodName.equals("cancel") ||
                            methodName.startsWith("schedule"));
                    default: return true;
                }
            }
            if (className.startsWith("org.apache.log4j.") ||
                    className.startsWith("org.slf4j.")) {
                return !(methodName.equals("error") || methodName.equals("warn") ||
                        methodName.equals("info"));
            }
        }
        return true;
    }
}
