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
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.Local;
import soot.PatchingChain;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.LocalGenerator;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.IntConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.StaticInvokeExpr;

/**
 * TODO: to be used
 */
public final class SerializerInstrumentor {
    private static final Logger LOG = LoggerFactory.getLogger(SerializerInstrumentor.class);

    private final SootClass serializer;

    public SerializerInstrumentor(final SootClass serializer) {
        this.serializer = serializer;
    }

    public void instrument() {
        int count_s = 0, count_d = 0;
        SootMethod serialize = null, deserialize = null;
        for (final SootMethod method : serializer.getMethods()) {
            final String subSig = method.getSubSignature();
            if (subSig.matches("void serialize\\([^,]*,java\\.io\\.DataOutput,int\\)") &&
                    !subSig.startsWith("void serialize(java.lang.Object,")) {
                try {
                    // sometimes the method is not implemented, and it should be ignored
                    method.retrieveActiveBody();
                    count_s++;
                    serialize = method;
                } catch (final Exception ignored) {}
            }
            if (subSig.matches(".* deserialize\\(java\\.io\\.DataInput,int\\)") &&
                    !subSig.startsWith("java.lang.Object deserialize(")) {
                try {
                    // sometimes the method is not implemented, and it should be ignored
                    method.retrieveActiveBody();
                    count_d++;
                    deserialize = method;
                } catch (final Exception ignored) {}
            }
        }
        if (count_s != 1)
            LOG.warn("abnormal number of serialize methods in " + serializer.getName());
        else
            instrument(serialize);
        if (count_d != 1)
            LOG.warn("abnormal number of deserialize methods in " + serializer.getName());
        else
            instrument(deserialize);
    }

    private void instrument(final SootMethod method) {
        final Body body = method.retrieveActiveBody();
        final PatchingChain<Unit> units = body.getUnits();
        final LocalGenerator lg = new DefaultLocalGenerator(body);
        Unit first = units.getFirst();
        while (AbstractStateInstrumentor.isLeadingStmt(first)) {
            first = units.getSuccOf(first);
        }
        final Set<Unit> returnSet = new HashSet<>();
        for (final Unit unit : units) {
            if (unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt) {
                returnSet.add(unit);
            }
        }
        units.insertBefore(getHandle(1), first);
        for (final Unit unit : returnSet) {
            units.insertBefore(getHandle(-1), unit);
        }
        final Unit last = units.getLast();
        final Local exception = lg.generateLocal(AbstractStateInstrumentor.throwableType);
        final Unit handler = Jimple.v().newIdentityStmt(exception,
                Jimple.v().newCaughtExceptionRef());
        units.insertAfter(handler, last);
        units.insertAfter(Jimple.v().newThrowStmt(exception), handler);
        units.insertAfter(getHandle(-1), handler);
        final Trap trap = Jimple.v().newTrap(AbstractStateInstrumentor.throwableClass, first, last, handler);
        body.getTraps().add(trap);
    }

    private InvokeStmt getHandle(int value) {
        final SootClass agentClass = SootUtils.getLegolasAgentClass();
        final SootMethod handleMethod = agentClass.getMethodByName("handleSerializer");
        final List<Value> args = new LinkedList<>();
        args.add(IntConstant.v(value));
        final StaticInvokeExpr handleExpr = Jimple.v().newStaticInvokeExpr(handleMethod.makeRef(), args);
        return Jimple.v().newInvokeStmt(handleExpr);
        //units.insertAfter(registerStmt, units.getFirst());
    }
}
