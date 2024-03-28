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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import soot.PatchingChain;
import soot.Unit;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

/**
 * Analyze control dependency of a program statement and some concrete state variable.
 */
public final class DominatorAnalysis extends ForwardFlowAnalysis<Unit, FlowSet<Unit>> {
    private final FlowSet<Unit> fullSet;
    private final Map<Unit, LinkedList<Unit>> dominate;

    public DominatorAnalysis(final DirectedGraph<Unit> graph, final PatchingChain<Unit> units,
            final Map<Unit, Boolean> entryPoints) {
        super(graph);
        fullSet = new ArraySparseSet<>();
        for (final Unit unit: units)
            fullSet.add(unit);
        doAnalysis();
        dominate = new HashMap<>();
        for (final Unit unit: units) {
            dominate.put(unit, new LinkedList<>());
        }
        for (final Unit unit: units) {
            if (!entryPoints.containsKey(unit)) {
                FlowSet<Unit> doms = getFlowAfter(unit);
                int size = doms.size();
                for (final Unit idom: doms) {
                    if (getFlowAfter(idom).size() + 1 == size) {
                        dominate.get(idom).add(unit);
                        break;
                    }
                }
            }
        }
    }

    public final LinkedList<Unit> getDominate(final Unit unit) {
        return dominate.get(unit);
    }

    @Override
    protected FlowSet<Unit> newInitialFlow() {
        return fullSet.clone();
    }
    @Override
    protected FlowSet<Unit> entryInitialFlow() {
        return new ArraySparseSet<>();
    }
    @Override
    protected void merge(FlowSet<Unit> in1, FlowSet<Unit> in2, FlowSet<Unit> out) {
        in1.intersection(in2, out);
    }
    @Override
    protected void copy(FlowSet<Unit> src, FlowSet<Unit> dst) {
        src.copy(dst);
    }
    @Override
    protected void flowThrough(FlowSet<Unit> in, Unit node, FlowSet<Unit> out) {
        in.copy(out);
        out.add(node);
    }
}
