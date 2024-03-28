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

import edu.umich.order.legolas.analyzer.util.SootUtils;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.IdentityStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.ThisRef;

/**
 * Instrument abstract states into a method
 */
public class AbstractStateInstrumentor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateInstrumentor.class);
    public static final SootClass throwableClass = SootUtils.getThrowableClass();
    public static final RefType throwableType = throwableClass.getType();

    private final SootMethod targetMethod;
    private final String targetClassName;
    private final Body body;
    private final PatchingChain<Unit> units;
    private final LocalGenerator lg;
    private final boolean register;

    public AbstractStateInstrumentor(final SootMethod targetMethod, final boolean register) {
        this.targetMethod = targetMethod;
        body = targetMethod.retrieveActiveBody();
        units = body.getUnits();
        lg = new LocalGenerator(body);
        targetClassName = targetMethod.getDeclaringClass().getName();
        this.register = register;
    }

    public final void instrument(final Map<Unit, Boolean> entryPoints,
            final Set<Unit> instrumentationPoints, final Map<Unit, Integer> indexMap) {
        final Local hashCodeLocal = lg.generateLocal(IntType.v());
        Unit firstStatefulUnit;
        if (register) {
            firstStatefulUnit = units.getFirst();
            while (!entryPoints.containsKey(firstStatefulUnit))
                firstStatefulUnit = units.getSuccOf(firstStatefulUnit);
        }
        for (final Unit unit : entryPoints.keySet()) {
            if (entryPoints.get(unit)) {
                Unit last = units.getSuccOf(unit);
                while (isLeadingStmt(last)) {
                    last = units.getSuccOf(last);
                }
                insertState(indexMap.get(unit), units.getPredOf(last), true, hashCodeLocal);
            }
        }
        for (final Unit unit : instrumentationPoints) {
            int index = indexMap.get(unit);
            insertState(index, unit, false, hashCodeLocal);
        }
        Unit first = units.getFirst();
        while (isLeadingStmt(first)) {
            first = units.getSuccOf(first);
        }
        SootUtils.genHashCode(units, hashCodeLocal, units.getPredOf(first));
        if (register) {
            final Set<Unit> returnSet = new HashSet<>();
            for (final Unit unit : units) {
                if (unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt) {
                    returnSet.add(unit);
                }
            }
            for (final Unit unit : returnSet) {
                insertState(-1, unit, false, hashCodeLocal);
            }
            final Unit last = units.getLast();
            final Local exception = lg.generateLocal(throwableType);
            final Unit handler = Jimple.v().newIdentityStmt(exception,
                    Jimple.v().newCaughtExceptionRef());
            units.insertAfter(handler, last);
            units.insertAfter(Jimple.v().newThrowStmt(exception), handler);
            insertState(-1, handler, true, hashCodeLocal);
            final Trap trap = Jimple.v().newTrap(throwableClass, first, last, handler);
            body.getTraps().add(trap);
        }
    }

    public static boolean isLeadingStmt(final Unit unit) {
        if (!(unit instanceof IdentityStmt))
            return false;
        final Value value = ((IdentityStmt) unit).getRightOp();
        return (value instanceof ThisRef) || (value instanceof ParameterRef);
    }

    private void insertState(final int stateId, final Unit targetUnit, final boolean after,
            final Local hashCodeLocal) {
        final SootClass agentClass = SootUtils.getLegolasAgentClass();
        final SootMethod informMethod = agentClass.getMethodByName("informState");
        LinkedList<Value> args = new LinkedList<>();
        args.add(StringConstant.v(targetClassName));
        args.add(hashCodeLocal);
        args.add(StringConstant.v(targetMethod.getSignature()));
        args.add(IntConstant.v(stateId));
        final StaticInvokeExpr registerExpr = Jimple
                .v().newStaticInvokeExpr(informMethod.makeRef(), args);
        final InvokeStmt registerStmt = Jimple.v().newInvokeStmt(registerExpr);
        if (after) {
            units.insertAfter(registerStmt, targetUnit);
        } else {
            units.insertBefore(registerStmt, targetUnit);
        }
    }
}
