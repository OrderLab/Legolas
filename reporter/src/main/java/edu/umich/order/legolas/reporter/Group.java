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
package edu.umich.order.legolas.reporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;

/**
 *
 */
public class Group extends ArtificialGroup {
    public final Map<String, WorkloadResultType[][]> levelsMap = new HashMap<>();
    public final Map<String, TrialCluster> clusters = new HashMap<>();
    public final Set<Trial> highlightSet = new HashSet<>();

    public int clusterNumber = 0;
    public int trialNumber = 0;
    public int highlightCluster = 0;
    public int falseHighlightCluster = 0;
    public final ArrayList<Trial> ranking = new ArrayList<>();

    public Group(Spec spec, String groupName) {
        super(spec, groupName);
    }

    public final String getCategory(final String tag, final WorkloadResultType[][] levels) {
        return tag + Trial.getWorkloadResultTypeValuesStr(levels);
    }

    public void add(final Trial trial, final String tag) {
        // FIXME: why (re-)computing the workload result types here instead of at trial loading time?
        final WorkloadResultType[][] levels = trial.computeWorkloadResultTypes();
        final String category = getCategory(tag, levels);
        trial.severityScore = Trial.computeSeverityScore(levels);
        ranking.add(trial);
        if (this.categories.containsKey(category)) {
            this.categories.get(category).add(trial);
        } else {
            final ArrayList<Trial> newList = new ArrayList<>();
            newList.add(trial);
            this.categories.put(category, newList);
        }
        if (!this.levelsMap.containsKey(category)) {
            levelsMap.put(category, levels);
        }
    }

    public void cluster() {
        for (final Map.Entry<String, ArrayList<Trial>> entry : categories.entrySet()) {
            final String category = entry.getKey();
            final TrialCluster clustering = new TrialCluster(spec, entry.getValue());
            clusters.put(category, clustering);
            clusterNumber += clustering.clusters.size();
            final int trialNumber = clustering.clusters.stream().mapToInt(ArrayList::size).sum();
            this.trialNumber += trialNumber;
        }
    }

    public void highlight() {
        ranking.sort(Comparator.comparingInt(o -> o.severityScore)); // reverse sort
        Collections.reverse(ranking);
        for (final Trial trial : ranking) {
            if (trial.severityScore >= spec.severityThreshold) {
                highlightSet.add(trial);
            }
        }
        for (final Map.Entry<String, TrialCluster> entry : clusters.entrySet()) {
            final TrialCluster clustering = entry.getValue();
            for (final ArrayList<Trial> trials : clustering.clusters) {
                for (final Trial trial : trials) {
                    if (highlightSet.contains(trial)) {
                        highlightCluster++;
                        break;
                    }
                }
            }
        }
        if (!spec.fp) {
            for (final Trial trial : highlightSet) {
                if (spec.matchInvalidInjection(trial)) {
                    falseHighlightCluster++;
                }
            }
        }
    }

    @Override
    public void dump(String dirPath) throws IOException {
        if (dirPath == null || dirPath.isEmpty())
            return;
        final File dir = new File(dirPath, groupName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (final Map.Entry<String, TrialCluster> entry : clusters.entrySet()) {
            final String category = entry.getKey();
            final TrialCluster clustering = entry.getValue();
            final WorkloadResultType[][] levels = levelsMap.get(category);
            final JsonObjectBuilder json = Json.createObjectBuilder();
            json.add("pattern", getPatterns(levels));
            json.add("clusters_number", clustering.clusters.size());
            json.add("clusters", clustering.dumpJson());
            try (final FileWriter writer = new FileWriter(dir + "/" + category + ".json");
                    final JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(writer)) {
                jsonWriter.writeObject(json.build());
            }
        }
    }
}
