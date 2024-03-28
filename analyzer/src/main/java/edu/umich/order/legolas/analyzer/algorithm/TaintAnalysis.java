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

import java.util.Set;
import soot.Local;
import soot.SootField;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InstanceFieldRef;
import soot.jimple.ThisRef;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

/**
 * Simple taint analysis
 */
public final class TaintAnalysis extends ForwardFlowAnalysis<Unit, FlowSet<Local>> {
    protected final Set<SootField> stateVariables;

    public TaintAnalysis(final DirectedGraph<Unit> graph, final Set<SootField> stateVariables) {
        super(graph);
        this.stateVariables = stateVariables;
        doAnalysis();
    }

    public boolean isTainted(final Unit unit, Value cond) {
        if (cond == null) {
            return false;
        }
        final FlowSet<Local> taintedLocals = getFlowAfter(unit);
        if (cond instanceof Local) {
            return taintedLocals.contains((Local) cond);
        }
        for (final ValueBox valueBox: cond.getUseBoxes()) {
            final Value value = valueBox.getValue();
            if (value instanceof Local) {
                if (taintedLocals.contains((Local) value)) {
                    return true;
                }
            }
            if (value instanceof InstanceFieldRef) {
                if (stateVariables.contains(((InstanceFieldRef) value).getField())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected FlowSet<Local> newInitialFlow() {
        return new ArraySparseSet<>();
    }
    @Override
    protected FlowSet<Local> entryInitialFlow() {
        return new ArraySparseSet<>();
    }
    @Override
    protected void merge(FlowSet<Local> in1, FlowSet<Local> in2, FlowSet<Local> out) {
        in1.union(in2, out);
    }
    @Override
    protected void copy(FlowSet<Local> src, FlowSet<Local> dst) {
        src.copy(dst);
    }
    @Override
    protected void flowThrough(FlowSet<Local> in, Unit node, FlowSet<Local> out) {
        in.copy(out);
        boolean intro = false;
        for (final ValueBox valueBox: node.getUseBoxes()) {
            final Value value = valueBox.getValue();
            if (value instanceof InstanceFieldRef) {
                final SootField field = ((InstanceFieldRef) value).getField();
                if (stateVariables.contains(field)) {
                    intro = true;
                }
            }
            if (value instanceof ThisRef) {
                intro = true;
            }
            if (value instanceof Local) {
                if (in.contains((Local)value)) {
                    intro = true;
                }
            }
        }
        for (final ValueBox valueBox: node.getDefBoxes()) {
            final Value value = valueBox.getValue();
            if (value instanceof Local) {
                final Local local = (Local)value;
                if (out.contains(local)) {
                    out.remove(local);
                }
            }
        }
        if (intro) {
            for (final ValueBox valueBox: node.getDefBoxes()) {
                final Value value = valueBox.getValue();
                if (value instanceof Local) {
                    out.add((Local)value);
                }
            }
        }
    }
}
