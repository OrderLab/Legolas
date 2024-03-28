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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.Local;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.FieldRef;

/**
 * Approximate the meta-info analysis in SOSP '19
 */
public class MetaInfoAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(MetaInfoAnalysis.class);

    private Set<String> metaInfoTypes;

    public MetaInfoAnalysis(Set<String> metaInfoTypes) {
        this.metaInfoTypes = metaInfoTypes;
    }

    public static class SourceMetaInfoAccess {
        public final Unit unit;
        public final String methodSig;
        public final String variableName;
        public final String typeName;
        public final long accessId;

        public SourceMetaInfoAccess(Unit unit, String methodSig, String varName,
                String typeName, long accessId) {
            this.unit = unit;
            this.methodSig = methodSig;
            this.variableName = varName;
            this.typeName = typeName;
            this.accessId = accessId;
        }
    }

    public Map<SootMethod, List<SourceMetaInfoAccess>> identifyMetaInfoAccesses(
            Collection<SootClass> sootClasses) {
        Set<SootField> metaInfoFields = new HashSet<>();
        Set<Local> metaInfoLocals = new HashSet<>();
        for (SootClass sootClass : sootClasses) {
            for (final SootField field : sootClass.getFields()) {
                String typeStr = field.getType().toString();
                if (metaInfoTypes.contains(typeStr)) {
                    LOG.debug("Field '" + field.getName() + "' in " + sootClass.getName() +
                            " has type '" + typeStr + "'");
                    metaInfoFields.add(field);
                }
            }
        }
        long accessId = 1;
        Map<SootMethod, List<SourceMetaInfoAccess>> accessPointsMap = new HashMap<>();
        for (SootClass sootClass : sootClasses) {
            for (final SootMethod sootMethod : sootClass.getMethods()) {
                if (sootMethod.isPhantom() || !sootMethod.hasActiveBody())
                    continue;
                final Body body = sootMethod.retrieveActiveBody();
                for (Local local : body.getLocals()) {
                    String typeStr = local.getType().toString();
                    if (metaInfoTypes.contains(typeStr)) {
                        LOG.debug("Local '" + local.getName() + "' in " + sootClass.getName()
                                + ":" + sootMethod.getName() + " has type '" + typeStr + "'");
                        metaInfoLocals.add(local);
                    }
                }
                List<SourceMetaInfoAccess> accessPoints = new LinkedList<>();
                for (final Unit unit : body.getUnits()) {
                    boolean useMetaInfo = false;
                    String metaInfoVarName = null, metaInfoTypeStr = null;
                    for (ValueBox box : unit.getUseBoxes()) {
                        Value value = box.getValue();
                        if (value instanceof FieldRef) {
                            SootField sf = ((FieldRef) value).getField();
                            if (metaInfoFields.contains(sf)) {
                                metaInfoVarName = sf.getName();
                                metaInfoTypeStr = sf.getType().toString();
                                LOG.debug("Use of meta-info field " + metaInfoVarName + " in " +
                                        sootClass.getName() + ":" + sootMethod.getName() + "@" +
                                        unit.getJavaSourceStartLineNumber());
                                useMetaInfo = true;
                            }
                        } else if (value instanceof Local) {
                            Local local = (Local) value;
                            if (metaInfoLocals.contains(local)) {
                                metaInfoVarName = local.getName();
                                metaInfoTypeStr = local.getType().toString();
                                LOG.debug("Use of meta-info local " + local.getName() + " in " +
                                        sootClass.getName() + ":" + sootMethod.getName() + "@" +
                                        unit.getJavaSourceStartLineNumber());
                                useMetaInfo = true;
                            }
                        }
                        if (useMetaInfo)
                            break;
                    }
                    if (useMetaInfo) {
                        SourceMetaInfoAccess access = new SourceMetaInfoAccess(unit,
                                sootMethod.getSignature(), metaInfoVarName, metaInfoTypeStr, accessId);
                        accessId++;
                        accessPoints.add(access);
                    }
                }
                if (!accessPoints.isEmpty()) {
                    accessPointsMap.put(sootMethod, accessPoints);
                }
            }
        }
        return accessPointsMap;
    }
}
