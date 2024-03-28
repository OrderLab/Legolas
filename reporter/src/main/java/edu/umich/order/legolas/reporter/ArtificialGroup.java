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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;

/**
 *
 */
public class ArtificialGroup {
    public static final Comparator<Trial> stacktraceComparator = (o1, o2) -> {
        for (int i = 0; i < o1.injectionStacktrace.length &&
                i < o2.injectionStacktrace.length; i++) {
            final int t = o1.injectionStacktrace[i].compareTo(o2.injectionStacktrace[i]);
            if (t != 0) {
                return t;
            }
        }
        return o1.injectionStacktrace.length - o2.injectionStacktrace.length;
    };

    public static final Comparator<Trial> idComparator = Comparator.comparingInt(o -> o.id);

    public final Spec spec;
    public final String groupName;
    public final Map<String, ArrayList<Trial>> categories = new HashMap<>();

    protected ArtificialGroup(Spec spec, String groupName) {
        this.spec = spec;
        this.groupName = groupName;
    }

    public ArtificialGroup(Spec spec, final ArrayList<Trial> failures, String groupName) {
        this.spec = spec;
        this.groupName = groupName;
        for (final Trial trial : failures) {
            if (trial.injectionStacktrace == null) {
                throw new RuntimeException("no injection stack trace in trial " + trial.id);
            }
            String category = null;
            for (final Spec.Location pattern : spec.locations) {
                if (pattern.match(trial.injectionStacktrace)) {
                    category = pattern.type;
                    break;
                }
            }
            if (category == null) {
                category = "others";
            }
            if (!this.categories.containsKey(category)) {
                this.categories.put(category, new ArrayList<>());
            }
            this.categories.get(category).add(trial);
        }
    }

    public void dump(String dirPath) throws IOException {
        if (dirPath == null || dirPath.isEmpty())
            return;
        final File dir = new File(dirPath, groupName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (final Map.Entry<String, ArrayList<Trial>> entry : this.categories.entrySet()) {
            final String name = entry.getKey();
            final ArrayList<Trial> trials = entry.getValue();
            final ArrayList<Pattern> patterns = new ArrayList<>();
            for (final Trial trial : trials) {
                final WorkloadResultType[][] levels = trial.computeWorkloadResultTypes();
                Pattern pattern = null;
                for (final Pattern p : patterns) {
                    if (Arrays.deepEquals(levels, p.levels)) {
                        pattern = p;
                        break;
                    }
                }
                if (pattern == null) {
                    pattern = new Pattern(levels);
                    patterns.add(pattern);
                }
                pattern.trials.add(trial);
            }
            final JsonArrayBuilder json = Json.createArrayBuilder();
            for (final Pattern pattern : patterns) {
                json.add(pattern.dump());
            }
            try (final FileWriter writer = new FileWriter(dir + "/" + name + ".json");
                    final JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(writer)) {
                jsonWriter.writeObject(Json.createObjectBuilder().add(name, json).build());
            }
        }
    }

    protected final JsonObjectBuilder getPatterns(final WorkloadResultType[][] levels) {
        final JsonObjectBuilder patterns = Json.createObjectBuilder();
        for (int i = 0; i < levels.length; i++) {
            final Spec.Phase phase = spec.phases.get(i);
            final JsonObjectBuilder pattern = Json.createObjectBuilder();
            for (int j = 0; j < levels[i].length; j++) {
                pattern.add(phase.workloads[j].type, WorkloadResultType.getTypeMsg(levels[i][j]));
            }
            patterns.add("Phase " + i, pattern);
        }
        for (int i = levels.length; i < spec.phases.size(); i++) {
            final Spec.Phase phase = spec.phases.get(i);
            final JsonObjectBuilder workload = Json.createObjectBuilder();
            for (int j = 0; j < phase.workloads.length; j++) {
                workload.add(phase.workloads[j].type, "not started");
            }
            patterns.add("Phase " + i, workload);
        }
        return patterns;
    }

    private final class Pattern {
        final JsonArrayBuilder jsons = Json.createArrayBuilder();
        final WorkloadResultType[][] levels;
        final ArrayList<Trial> trials = new ArrayList<>();
        Pattern(final WorkloadResultType[][] levels) {
            this.levels = levels;
        }
        void addJson(final Trial trial) {
            jsons.add(trial.dumpJson(spec));
        }
        JsonObjectBuilder dump() {
            trials.sort(stacktraceComparator);
            for (final Trial trial : trials) {
                addJson(trial);
            }
            return Json.createObjectBuilder()
                    .add("pattern", getPatterns(levels))
                    .add("trials_number", trials.size())
                    .add("trials", jsons);
        }
    }
}
