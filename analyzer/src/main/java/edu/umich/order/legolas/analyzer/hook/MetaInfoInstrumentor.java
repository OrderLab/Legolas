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

import edu.umich.order.legolas.analyzer.algorithm.MetaInfoAnalysis.SourceMetaInfoAccess;
import edu.umich.order.legolas.analyzer.util.SootUtils;
import java.util.LinkedList;
import soot.Body;
import soot.PatchingChain;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.LongConstant;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;

/**
 *  Approximate the meta-info analysis in SOSP '19
 */
public class MetaInfoInstrumentor {
    private final SootMethod targetMethod;
    private final String targetClassName;
    private final Body body;
    private final PatchingChain<Unit> units;

    public MetaInfoInstrumentor(SootMethod targetMethod) {
        this.targetMethod = targetMethod;
        body = targetMethod.retrieveActiveBody();
        units = body.getUnits();
        targetClassName = targetMethod.getDeclaringClass().getName();
    }

    public void instrument(SourceMetaInfoAccess access, boolean after) {
        final SootClass agentClass = SootUtils.getLegolasAgentClass();
        final SootMethod informMethod = agentClass.getMethodByName("informAccess");
        LinkedList<Value> args = new LinkedList<>();
        args.add(StringConstant.v(targetClassName));
        args.add(IntConstant.v(-1));
        args.add(StringConstant.v(targetMethod.getSignature()));
        args.add(StringConstant.v(access.variableName));
        args.add(StringConstant.v(access.typeName));
        args.add(LongConstant.v(access.accessId));
        final StaticInvokeExpr registerExpr = Jimple
                .v().newStaticInvokeExpr(informMethod.makeRef(), args);
        final InvokeStmt registerStmt = Jimple.v().newInvokeStmt(registerExpr);
        if (after) {
            units.insertAfter(registerStmt, access.unit);
        } else {
            units.insertBefore(registerStmt, access.unit);
        }
    }
}
