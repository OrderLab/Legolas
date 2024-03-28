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

import edu.umich.order.legolas.analyzer.util.SootUtils.SootCallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.util.Chain;

/**
 * Perform call site analysis for a given list of Soot classes
 */
public class CallSiteAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(CallSiteAnalysis.class);

    private final String[] package_prefix_list;
    private static final Map<String, List<SootCallSite>> callSitesMap = new HashMap<>();

    public CallSiteAnalysis(String[] package_prefix_list) {
        this.package_prefix_list = package_prefix_list;
    }

    /**
     * Process the application classes to build a call site map.
     *
     * Pass the classes as argument for ease of invoking this method in testing.
     *
     * @param classes the list of application classes to be analyzed.
     */
    public void analyze(Chain<SootClass> classes) {
        // Build a call site map. The reason that we need to do this is the call graph built by
        // Soot only analyzes methods reachable from 'main'. Some application classes may not
        // get processed because of this constraint. At least, many test case classes' functions
        // will not appear in the default call graph.

        for (SootClass sootClass : classes) {
            if (Arrays.stream(package_prefix_list).noneMatch(sootClass.getName()::startsWith)) {
                continue;
            }
            for (SootMethod sootMethod : sootClass.getMethods()) {
                if (!sootMethod.hasActiveBody()) {
                    continue;
                }
                Body body = sootMethod.retrieveActiveBody();
                for (Unit unit : body.getUnits()) {
                    for (ValueBox valueBox : unit.getUseBoxes()) {
                        Value value = valueBox.getValue();
                        if (value instanceof InvokeExpr) {
                            InvokeExpr expr = (InvokeExpr) value;
                            // Note: here we must call expr.getMethodRef(). If we call expr.getMethod()
                            // instead, we can get a java.util.ConcurrentModificationException.
                            // This is because getMethod() has a potential side effect that it will
                            // try to resolve the method if it does not exist, which will be added
                            // to some class's method list during the iteration and cause the exception.
                            SootMethodRef target = expr.getMethodRef();
                            List<SootCallSite> callSites = callSitesMap.computeIfAbsent(
                                    target.getSignature(), k -> new ArrayList<>());
                            callSites.add(new SootCallSite(expr, unit, sootMethod));
                        }
                    }
                }
            }
        }
        LOG.info("Built call site map for " + callSitesMap.size() + " methods");
    }

    public List<SootCallSite> getCallSites(String methodName) {
        return callSitesMap.get(methodName);
    }
}
