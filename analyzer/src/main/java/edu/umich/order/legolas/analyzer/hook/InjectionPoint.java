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
import java.util.List;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;

/**
 * An injection point, typically an invocation at somewhere in the code.
 */
public class InjectionPoint {
    public SootMethod function;
    public Unit unit;
    public InvokeExpr expr;
    public List<InjectionFault> faults;
    public int lineNo;

    public InjectionPoint(SootMethod func, Unit unit, InvokeExpr expr, List<InjectionFault> faults) {
        this.function = func;
        this.unit = unit;
        this.expr = expr;
        this.faults = faults;
        if (unit == null)
            lineNo = -1;
        else
            lineNo = unit.getJavaSourceStartLineNumber();
    }

    @Override
    public String toString() {
        SootClass cls = function.getDeclaringClass();
        return cls.getName() + "." + function.getName() + "@line " + lineNo + ": " + unit;
    }
}
