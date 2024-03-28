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

import edu.umich.order.legolas.analyzer.util.SootUtils;
import edu.umich.order.legolas.analyzer.util.SootUtils.FieldClassDef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;

/**
 * Analyze the definitions for class field references
 */
public class FieldRefDefsAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(FieldRefDefsAnalysis.class);

    final Map<SootMethod, Set<SootField>> methodFieldDefMap;
    final Map<SootClass, Map<SootField, FieldClassDef>> cntorFieldDefMap;

    public FieldRefDefsAnalysis() {
        methodFieldDefMap = new HashMap<>();
        cntorFieldDefMap = new HashMap<>();
    }

    private Set<SootField> checkMethodFieldDef(SootMethod method) {
        Set<SootField> result = new HashSet<>();
        if (!method.hasActiveBody())
            return result;
        Body body = method.getActiveBody();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof DefinitionStmt) {
                Value lhs = ((DefinitionStmt) unit).getLeftOp();
                if (lhs instanceof FieldRef) {
                    result.add(((FieldRef) lhs).getField());
                }
            }
        }
        return result;
    }

    public List<Unit> getDefsOfAt(FieldRef ref, Unit unit, SootMethod method) {
        Set<SootField> mdefs = methodFieldDefMap.get(method);
        if (mdefs == null) {
            mdefs = checkMethodFieldDef(method);
            methodFieldDefMap.put(method, mdefs);
        }

        List<Unit> result = new LinkedList<>();
        DefinitionStmt fieldDef = null;
        SootField field = ref.getField();
        if (mdefs.contains(field))
            fieldDef= SootUtils.findValueDefInBody(ref, unit, method.getActiveBody());
        if (fieldDef != null) {
            LOG.debug("Found definition for field " + field + " in method " + method.getName());
            result.add(fieldDef);
        } else {
            LOG.debug("Cannot find definition for field " + field + " in "
                    + method.getName());
        }
        return result;
    }

    public FieldClassDef getDefsOfInClass(FieldRef ref, SootClass clz) {
        SootField field = ref.getField();

        FieldClassDef result;
        Map<SootField, FieldClassDef> fmap = cntorFieldDefMap.computeIfAbsent(clz,
                k -> new HashMap<>());
        result = fmap.get(field);
        if (result != null)
            return result;

        result = new FieldClassDef();
        fmap.put(field, result);

        // First, find the constructor of the class. A class may have multiple constructors
        List<SootMethod> constructors = new LinkedList<>();
        for (SootMethod m : clz.getMethods()) {
            if (m.getName().equals("<init>")) {
                constructors.add(m);
            }
        }

        // Next, for each constructor that has an active body, we try to find a definition
        // statement in it that matches the field.
        for (SootMethod constructor : constructors) {
            List<Unit> defs = new LinkedList<>();
            if (constructor != null && constructor.hasActiveBody()) {
                DefinitionStmt fieldDef = SootUtils.findValueDefInBody(ref, null,
                        constructor.getActiveBody());
                if (fieldDef != null) {
                    defs.add(fieldDef);
                    LOG.debug("Found definition for field " + field + " in constructor "
                            + constructor.getSubSignature());
                }
            }
            if (!defs.isEmpty()) {
                result.methodDefMap.put(constructor, defs);
            }
        }

        if (result.methodDefMap.isEmpty()) {
            // If unfortunately this field is not initialized in the constructor, we will have to
            // search all methods of the class to find one that does the initialization. This
            // can be obviously expensive...
            // Example where this is necessary: the 'leaderIs' field in the 'Learner' class in ZooKeeper
            List<Unit> defs = new LinkedList<>();
            for (SootMethod m : clz.getMethods()) {
                // skip constructors, we just checked them
                if (!m.getName().equals("<init>") && m.hasActiveBody()) {
                    DefinitionStmt fieldDef = SootUtils.findValueDefInBody(ref, null,
                            m.getActiveBody());
                    if (fieldDef != null) {
                        defs.add(fieldDef);
                        LOG.debug("Found definition for field " + field + " in method "
                                + m.getSubSignature());
                    }
                }
                if (!defs.isEmpty()) {
                    result.methodDefMap.put(m, defs);
                    // TODO: to save time, we stop once we find one def in a non-constructor method
                    break;
                }
            }
        }

        if (result.methodDefMap.isEmpty()) {
            LOG.warn("Cannot find definition for field " + field + " in class " + clz.getName());
        }
        return result;
    }
}
