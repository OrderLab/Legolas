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

import java.util.HashSet;
import java.util.Set;
import soot.SootClass;
import soot.SootField;

/**
 * Extract the concrete state variables in a class using heuristics
 */
public final class ConcreteStateAnalysis {
    private final SootClass targetClass;
    private final Set<SootField> stateVariables;

    public ConcreteStateAnalysis(SootClass cls) {
        targetClass = cls;
        stateVariables = new HashSet<>();
        doAnalysis();
    }

    protected void doAnalysis() {
        for (final SootField field: targetClass.getFields()) {
            // FIXME: enhance the heuristics here
            if (!field.isStatic()) {
                stateVariables.add(field);
            }
        }
    }

    public Set<SootField> getResult() {
        return stateVariables;
    }
}
