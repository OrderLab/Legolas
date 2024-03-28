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

import edu.umich.order.legolas.analyzer.hook.AbstractStateInstrumentor;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.PatchingChain;
import soot.SootField;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.jimple.IfStmt;
import soot.jimple.SwitchStmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

/**
 * Analyze the abstract states in a given function body.
 */
public class AbstractStateAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateAnalysis.class);

    private final Body body;
    private final Set<SootField> stateVariables;
    private final UnitGraph graph;
    private final PatchingChain<Unit> units;
    private final TaintAnalysis taintAnalysis;
    private final DominatorAnalysis dominanceAnalysis;
    private final Map<Unit, Boolean> entryPoints;
    private final Set<Unit> instrumentationPoints;
    private final Map<Unit, Integer> indexMap;
    private final StatefulMethodFilter statefulMethodFilter;
    private int indexNum; // ASV number

    private static List<Integer> ASVs = new LinkedList<>();

    public AbstractStateAnalysis(final Body body, final Set<SootField> stateVariables,
            final StatefulMethodFilter statefulMethodFilter) {
        this.statefulMethodFilter = statefulMethodFilter;
        this.body = body;
        this.stateVariables = stateVariables;
        this.graph = new ExceptionalUnitGraph(body);
        this.units = body.getUnits();
        this.entryPoints = new HashMap<>();
        this.entryPoints.put(units.getFirst(), false);
        for (final Trap trap : body.getTraps()) {
            this.entryPoints.put(trap.getHandlerUnit(), false);
        }
        this.taintAnalysis = new TaintAnalysis(graph, stateVariables);
        this.dominanceAnalysis = new DominatorAnalysis(graph, units, entryPoints);
        this.instrumentationPoints = new HashSet<>();
        this.indexMap = new HashMap<>();

        // it's a bad practice to invoke heavy method in constructor, but the Soot analysis
        // is structured this way (doAnalysis() is protected), so whatever...
        doAnalysis();
    }

    public final void instrument(final boolean register) {
        new AbstractStateInstrumentor(body.getMethod(), register)
                .instrument(entryPoints, instrumentationPoints, indexMap);
    }

    public int getNumberOfASVs() {
        return indexNum;
    }

    public Map<Unit, Integer> getASVs() {
        return indexMap;
    }

    public Body getAnalyzedBody() {
        return body;
    }

    public Set<SootField> getStateVariables() {
        return stateVariables;
    }

    protected void doAnalysis() {
        // Step 1: compute the abstract states
        entryPoints.replaceAll((u, v) -> process(u));
        entryPoints.put(units.getFirst(), true);

        // Step 2: assign the indexes
        indexNum = 0;
        for (final Unit unit : units) {
            if (instrumentationPoints.contains(unit) ||
                    (entryPoints.containsKey(unit) && entryPoints.get(unit))) {
                indexMap.put(unit, indexNum);
                indexNum++;
            }
        }
        LOG.debug("{} {} # AS = {};  # CSV = {}", body.getMethod().getDeclaringClass().getName(),
                body.getMethod().getSubSignature(), indexNum, stateVariables.size());
        ASVs.add(indexNum);
    }

    public static void printStats() {
        Collections.sort(ASVs);
        int total = 0;
        for (final Integer v : ASVs) total += v;
        LOG.info("ASV stats: total={}, mean={}, max={}, min={}",
                total,
                total * 1.0 / ASVs.size(),
                ASVs.get(ASVs.size() - 1),
                ASVs.get(0));
    }

    public boolean process(Unit unit) {
        boolean flag = false;
        while (unit != null) {
            flag |= ! statefulMethodFilter.filter(unit);
            final LinkedList<Unit> doms = dominanceAnalysis.getDominate(unit);
            Unit next = null;
            Value cond = null;
            final LinkedList<Unit> nestedBlocks = new LinkedList<>();
            if (unit instanceof IfStmt) {
                cond = ((IfStmt) unit).getCondition();
                if (doms.size() == 3) {
                    next = doms.getLast();
                    nestedBlocks.add(doms.get(0));
                    nestedBlocks.add(doms.get(1));
                }
                if (doms.size() == 2) {
                    final Unit x = doms.getFirst();
                    final Unit y = doms.getLast();
                    if (isConnected(x, y)) {
                        next = y;
                        nestedBlocks.add(x);
                    } else {
                        nestedBlocks.add(x);
                        nestedBlocks.add(y);
                    }
                }
                if (doms.size() == 1) {
                    nestedBlocks.add(doms.getFirst());
                }
            } else if (unit instanceof SwitchStmt) {
                cond = ((SwitchStmt) unit).getKey();
                if (doms.size() > 0) {
                    final Unit last = doms.getLast();
                    next = last;
                    for (final Unit dom: doms) {
                        if (dom != last) {
                            nestedBlocks.add(dom);
                            if (next != null && !isConnected(dom, last)) {
                                next = null;
                            }
                        }
                    }
                    if (next == null) {
                        nestedBlocks.add(last);
                    }
                }
            } else {
                if (!doms.isEmpty()) {
                    next = doms.getFirst();
                    if (doms.size() > 1) {
                        for (final Unit dom: doms) {
                            if (dom != next) {
                                flag |= process(dom);
                            }
                        }
                    }
                }
            }
            boolean tainted = taintAnalysis.isTainted(unit, cond);
            for (final Unit block: nestedBlocks) {
                if (process(block)) {
                    if (tainted) {
                        instrumentationPoints.add(block);
                    }
                }
            }
            unit = next;
        }
        return flag;
    }

    private boolean isConnected(final Unit src, final Unit dst) {
        final HashSet<Unit> constraintSet = new HashSet<>();
        boolean started = false;
        for (final Unit unit: units) {
            if (unit == src) {
                started = true;
            }
            if (started) {
                constraintSet.add(unit);
            }
            if (unit == dst) {
                if (!started) {
                    return false;
                }
                break;
            }
        }
        final Queue<Unit> queue = new LinkedList<>();
        constraintSet.remove(src);
        queue.add(src);
        while (!queue.isEmpty()) {
            final Unit unit = queue.remove();
            for (final Unit next: graph.getSuccsOf(unit)) {
                if (constraintSet.contains(next)) {
                    constraintSet.remove(next);
                    queue.add(next);
                    if (next == dst) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
