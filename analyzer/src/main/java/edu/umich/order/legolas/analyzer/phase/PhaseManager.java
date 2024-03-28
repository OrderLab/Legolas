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
package edu.umich.order.legolas.analyzer.phase;

import edu.umich.order.legolas.analyzer.util.SootUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import soot.Transform;
import soot.Transformer;

/**
 * Manage all the analysis phases.
 */
public class PhaseManager {
    private Map<String, Transform> analysisMap;
    private Map<String, PhaseInfo> phaseInfoMap;
    private Set<String> enabledAnalysisSet;

    // Information about all the phases available in Legolas.
    private static PhaseInfo[] PHASES  = {
        AbstractStateTransformer.PHASE_INFO,
            MetaInfoTransformer.PHASE_INFO
    };
    private static PhaseManager instance;

    private PhaseManager() {
        analysisMap = new HashMap<>();
        phaseInfoMap = new HashMap<>();
        for (PhaseInfo info : PHASES) {
            phaseInfoMap.put(info.getFullName(), info);
        }
        enabledAnalysisSet = new HashSet<>();
    }

    public static PhaseManager getInstance() {
        if (instance == null) {
            instance = new PhaseManager();
        }
        return instance;
    }

    public Transform getAnalysis(String name) {
        return analysisMap.get(name);
    }

    public boolean isAnalysiEnabled(String name) {
        return enabledAnalysisSet.contains(name);
    }

    public boolean enableAnalysis(String name) {
        if (phaseInfoMap.containsKey(name)) {
            // Enable only it is available
            enabledAnalysisSet.add(name);
            return true;
        }
        return false;
    }

    public Set<String> enabledAnalyses() {
        return enabledAnalysisSet;
    }

    public PhaseInfo getPhaseInfo(String name) {
        return phaseInfoMap.get(name);
    }

    public Iterator<PhaseInfo> phaseInfoIterator() {
        return phaseInfoMap.values().iterator();
    }

    public String[] getAnalyses() {
        String[] analyses = new String[phaseInfoMap.size()];
        analyses = phaseInfoMap.keySet().toArray(analyses);
        return analyses;
    }

    public Transform registerAnalysis(Transformer analysis, PhaseInfo info) {
        Transform phase = SootUtils.addNewTransform(info.getPack(), info.getFullName(), analysis);
        analysisMap.put(info.getFullName(), phase);
        phaseInfoMap.put(info.getFullName(), info);
        return phase;
    }
}