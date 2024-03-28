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

import edu.umich.order.legolas.analyzer.algorithm.MetaInfoAnalysis;
import edu.umich.order.legolas.analyzer.algorithm.MetaInfoAnalysis.SourceMetaInfoAccess;
import edu.umich.order.legolas.analyzer.algorithm.TargetClassFilter;
import edu.umich.order.legolas.analyzer.hook.MetaInfoInstrumentor;
import edu.umich.order.legolas.analyzer.option.AnalyzerOptions;
import edu.umich.order.legolas.analyzer.util.FileUtils;
import edu.umich.order.legolas.analyzer.util.SootUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;

/**
 * Approximate the meta-info analysis in SOSP '19
 */
public class MetaInfoTransformer extends SceneTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(MetaInfoTransformer.class);

    public static final PhaseInfo PHASE_INFO = new PhaseInfo("wjtp", "metainfo",
            "Derive and instrument meta info in a target software", true, false);

    private final AnalyzerOptions analyzerOptions = AnalyzerOptions.getInstance();
    private TargetClassFilter targetClassFilter;

    private Map<SootMethod, List<SourceMetaInfoAccess>> instrumentPointsMap = new HashMap<>();

    private static final int MAX_INSTRUMENT_PER_METHOD = 3;

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        InjectionTransformer injectionTransformer = new InjectionTransformer();

        LOG.info("Running meta-info analysis and instrumentation");

        String[] package_prefix_list = analyzerOptions.system_package_prefix_list;
        Set<String> exclude_packages = analyzerOptions.exclude_packages;
        Set<String> classSet = FileUtils.getClassSetByFile(analyzerOptions.class_set_file);

        targetClassFilter = new TargetClassFilter(package_prefix_list,
                exclude_packages, classSet);

        identifyMetaInfo();

        instrumentMetaInfo();

        // Instrument fault injection points
        injectionTransformer.transform();

        // Instrument main()
        instrumentMainInit();
    }

    private void identifyMetaInfo() {
        List<SootClass> appClasses = new LinkedList<>();
        for (SootClass sootClass: Scene.v().getApplicationClasses()) {
            if (targetClassFilter.filter(sootClass)) {
                continue;
            }
            appClasses.add(sootClass);
        }
        Set<String> metaInfoTypes = predefinedMetaInfoTypes();
        MetaInfoAnalysis analysis = new MetaInfoAnalysis(metaInfoTypes);
        instrumentPointsMap = analysis.identifyMetaInfoAccesses(appClasses);
    }

    private Set<String> predefinedMetaInfoTypes() {
        Set<String> typeStrings = new HashSet<>();
        typeStrings.add("java.net.SocketAddress");
        for (String prefix : analyzerOptions.system_package_prefix_list) {
            if (prefix.startsWith("org.apache.zookeeper")) {
                typeStrings.add("org.apache.zookeeper.server.quorum.QuorumPeer");
                typeStrings.add("org.apache.zookeeper.server.quorum.Leader");
                typeStrings.add("org.apache.zookeeper.server.quorum.Follower");
                typeStrings.add("org.apache.zookeeper.server.quorum.Observer");
            } else if (prefix.startsWith("org.apache.hadoop")) {
                typeStrings.add("org.apache.hadoop.yarn.api.records.ApplicationAttemptId");
                typeStrings.add("org.apache.hadoop.yarn.api.records.ApplicationId");
                typeStrings.add("org.apache.hadoop.yarn.api.records.ContainerId");
                typeStrings.add("org.apache.hadoop.fs.FSDataOutputStream");
                typeStrings.add("org.apache.hadoop.fs.FSDataInputStream");
                typeStrings.add("org.apache.hadoop.hdfs.protocol.DatanodeInfo");
                typeStrings.add("org.apache.hadoop.hdfs.server.namenode.NameNode");
                typeStrings.add("org.apache.hadoop.hdfs.server.datanode.DataNode");
                typeStrings.add("org.apache.hadoop.hdfs.server.datanode.BPOfferService");
            } else if (prefix.startsWith("org.apache.kafka")) {
                typeStrings.add("kafka.controller.ControllerState");
                typeStrings.add("kafka.controller.ReplicaStateMachine");
                typeStrings.add("kafka.server.BrokerServer");
            } else if (prefix.startsWith("org.apache.hbase")) {
                typeStrings.add("org.apache.hadoop.hbase.HRegionInfo");
                typeStrings.add("org.apache.hadoop.hbase.regionserver.HRegionServer");
                typeStrings.add("org.apache.hadoop.hbase.regionserver.HRegion");
                typeStrings.add("org.apache.hadoop.hbase.master.assignment.RegionStateNode");
                typeStrings.add("org.apache.hadoop.hbase.master.assignment.TransitRegionStateProcedure");
            } else if (prefix.startsWith("org.apache.cassandra")) {
                typeStrings.add("org.apache.cassandra.gms.Gossiper");
                typeStrings.add("org.apache.cassandra.net.MessageIn");
                typeStrings.add("org.apache.cassandra.net.MessageOut");
            }
        }
        return typeStrings;
    }

    private void instrumentMetaInfo() {
        for (Map.Entry<SootMethod, List<SourceMetaInfoAccess>> entry : instrumentPointsMap.entrySet()) {
            MetaInfoInstrumentor instrumentor = new MetaInfoInstrumentor(entry.getKey());
            List<SourceMetaInfoAccess> accesses = entry.getValue();
            int instrumented = 0;
            for (SourceMetaInfoAccess access : accesses) {
                instrumentor.instrument(access, true);
                instrumented++;
                if (MAX_INSTRUMENT_PER_METHOD > 0 && instrumented >= MAX_INSTRUMENT_PER_METHOD) {
                    // Avoid excessive instrumentations that will hang the system
                    break;
                }
            }
        }
    }

    private void instrumentMainInit() {
        AnalyzerOptions options = AnalyzerOptions.getInstance();
        List<SootMethod> mainMethods = SootUtils.findMainMethod(options.main_class,
                options.secondary_main_classes);
        if (mainMethods.isEmpty()) {
            LOG.warn("Failed to find main() method");
        } else {
            for (SootMethod mainMethod : mainMethods) {
                SootUtils.insertLegolasAgentInit(mainMethod);
                LOG.info("Successfully injected LegolasAgent.init() call in " +
                        mainMethod.getDeclaringClass().getName());
            }
        }
    }
}
