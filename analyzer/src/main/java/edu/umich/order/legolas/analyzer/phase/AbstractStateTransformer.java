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

import edu.umich.order.legolas.analyzer.algorithm.AbstractStateAnalysis;
import edu.umich.order.legolas.analyzer.algorithm.AbstractStateMachineProcessor;
import edu.umich.order.legolas.analyzer.algorithm.InheritanceDecider;
import edu.umich.order.legolas.analyzer.algorithm.StatefulMethodFilter;
import edu.umich.order.legolas.analyzer.algorithm.TargetClassFilter;
import edu.umich.order.legolas.analyzer.hook.AbstractStateInstrumentor;
import edu.umich.order.legolas.analyzer.hook.SerializerInstrumentor;
import edu.umich.order.legolas.analyzer.option.AnalyzerOptions;
import edu.umich.order.legolas.analyzer.util.FileUtils;
import edu.umich.order.legolas.analyzer.util.SootUtils;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

/**
 * Identify all concrete states in a system, derive and instrument abstract states in the modules.
 */
public class AbstractStateTransformer extends SceneTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStateTransformer.class);

    public static final PhaseInfo PHASE_INFO = new PhaseInfo("wjtp", "asmi",
            "Derive and instrument abstract states in a target software", true, false);

    final Map<SootClass, AbstractStateMachineProcessor> stateMachineCandidates = new HashMap<>();
    protected int classCnt = 0;
    protected int asmCnt = 0;

    private final AnalyzerOptions analyzerOptions = AnalyzerOptions.getInstance();

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        LOG.info("Running abstract state analysis and instrumentation");

        String[] package_prefix_list = analyzerOptions.system_package_prefix_list;
        Set<String> exclude_packages = analyzerOptions.exclude_packages;
        Set<String> classSet = FileUtils.getClassSetByFile(analyzerOptions.class_set_file);

        StatefulMethodFilter statefulMethodFilter = new StatefulMethodFilter(package_prefix_list);
        TargetClassFilter targetClassFilter = new TargetClassFilter(package_prefix_list,
                exclude_packages, classSet);

        InjectionTransformer injectionTransformer = new InjectionTransformer();

        // Step 1: identify classes that could make a state machine
        identifyTargetStateMachines(targetClassFilter);

        // Step 2: identify abstract states for state machines
        identifyAbstractStates(statefulMethodFilter);

        // Step 3: instrument abstract states for state machines
        instrumentAbstractStates();

        // step 4: deal with Serializer call stack (only Cassandra for now)
        // TODO: to be used
        //instrumentSerializer();

        // Step 5: instrument fault injection
        injectionTransformer.transform();

        // Step 6: instrument LegolasAgent.init in main
        // TODO: add config file path
        instrumentMainInit();
        AbstractStateAnalysis.printStats();
    }

    /*
     * In AnalyzerOptions.getInstance(), requires:
     * system_package_prefix,
     * exclude_packages,
     * class_set_file,
     */
    private void identifyTargetStateMachines(final TargetClassFilter targetClassFilter) {
        for (final SootClass sootClass: Scene.v().getApplicationClasses()) {
            if (targetClassFilter.filter(sootClass))
                continue;
            classCnt++;
            final AbstractStateMachineProcessor processor =
                    AbstractStateMachineProcessor.create(sootClass);
            if (processor != null) {
                LOG.debug("Found state machine class: " + sootClass.getName());
                stateMachineCandidates.put(sootClass, processor);
                asmCnt++;
            }
        }
        LOG.info("Analyzed {} classes and found {} state machine classes", classCnt, asmCnt);
    }

    private void identifyAbstractStates(final StatefulMethodFilter statefulMethodFilter) {
        int total_asm_cnt = 0;
        int total_asv_cnt = 0;
        for (final AbstractStateMachineProcessor processor : stateMachineCandidates.values()) {
            processor.identifyAbstractStates(statefulMethodFilter);
            total_asm_cnt++;
            for (AbstractStateAnalysis analysis : processor.getAnalysesForMethods()) {
                total_asv_cnt += analysis.getNumberOfASVs();
            }
        }
        LOG.info("Identified in total {} ASVs in {} ASMs", total_asv_cnt, total_asm_cnt);

        if (AnalyzerOptions.getInstance().dump_analysis_result) {
            dumpAbstractStates(stateMachineCandidates.values());
        }
    }

    private void instrumentAbstractStates() {
        for (final AbstractStateMachineProcessor processor : stateMachineCandidates.values()) {
            processor.instrument();
        }
    }

    private void instrumentSerializer() {
        for (final SootClass sootClass : Scene.v().getApplicationClasses()) {
            if (InheritanceDecider.isSerializer(sootClass)) {
                new SerializerInstrumentor(sootClass).instrument();
            }
        }
    }
    private void instrumentMainInit() {
        List<SootMethod> mainMethods = SootUtils.findMainMethod(analyzerOptions.main_class,
                analyzerOptions.secondary_main_classes);
        if (mainMethods.isEmpty()) {
            LOG.warn("Failed to find main() method");
        } else {
            for (SootMethod mainMethod : mainMethods) {
                Unit first = mainMethod.retrieveActiveBody().getUnits().getFirst();
                final Map<Unit, Boolean> entryPoints = new HashMap<>();
                entryPoints.put(first, true);
                final Map<Unit, Integer> indexMap = new HashMap<>();
                indexMap.put(first, 0);
                new AbstractStateInstrumentor(mainMethod, true)
                        .instrument(entryPoints, new HashSet<>(), indexMap);
                SootUtils.insertLegolasAgentInit(mainMethod);
                LOG.info("Successfully injected LegolasAgent.init() call in " +
                        mainMethod.getDeclaringClass().getName());
            }
        }
    }

    protected void dumpAbstractStates(Collection<AbstractStateMachineProcessor> processors) {
        File asResultText = new File(AnalyzerOptions.getInstance().data_dir,
                "abstract_states.csv");
        try (PrintWriter csvWriter = new PrintWriter(asResultText)) {
            boolean header_printed = false;
            for (final AbstractStateMachineProcessor processor : processors) {
                processor.dumpCSV(csvWriter, !header_printed);
                header_printed = true;
            }
        } catch (IOException e) {
            LOG.error("Failed to write to abstract states file");
        }
    }
}
