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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

/**
 *
 */
public class TrialCluster {
    private static final String[] wrappers = {
            "java.lang.Runnable,run,",
            "java.lang.Thread,run,",
            "java.util.concurrent.Executors$RunnableAdapter,call,",
            "java.util.concurrent.FutureTask,run,",
            "java.util.concurrent.ThreadPoolExecutor$Worker,run,",
            "java.util.concurrent.ThreadPoolExecutor,runWorker,",
            "org.apache.cassandra.utils.WrappedRunnable,run,",
            "org.apache.cassandra.io.util.DiskAwareRunnable,run,",
            "kafka.utils.ShutdownableThread,run,",
    };

    private static final class Node {
        public final Map<String, Node> children = new TreeMap<>();
        public final ArrayList<Trial> trials = new ArrayList<>();
    }

    public final ArrayList<ArrayList<Trial>> clusters = new ArrayList<>();
    public final Spec spec;

    private void searchHeads(final Node p, final ArrayList<Node> heads) {
        if (!p.trials.isEmpty()) {
            p.trials.sort(ArtificialGroup.idComparator);
            // wrapper is a separate cluster
            clusters.add(p.trials);
        }
        for (Map.Entry<String, Node> entry : p.children.entrySet()) {
            final String child = entry.getKey();
            final Node c = entry.getValue();
            boolean isWrapper = false;
            for (final String wrapper : wrappers) {
                if (child.startsWith(wrapper)) {
                    isWrapper = true;
                    break;
                }
            }
            if (isWrapper) {
                searchHeads(c, heads);
            } else {
                heads.add(c);
            }
        }
    }

    private void searchRemaining(final Node p, final ArrayList<Trial> cluster) {
        if (!p.trials.isEmpty()) {
            p.trials.sort(ArtificialGroup.idComparator);
            cluster.addAll(p.trials);
        }
        for (Map.Entry<String, Node> entry : p.children.entrySet()) {
            final Node c = entry.getValue();
            searchRemaining(c, cluster);
        }
    }

    public TrialCluster(final Spec spec, final ArrayList<Trial> trials) {
        this.spec = spec;
        final Node root = new Node();
        for (final Trial trial : trials) {
            Node p = root;
            for (final String s : trial.injectionStacktrace) {
                if (p.children.containsKey(s)) {
                    p = p.children.get(s);
                } else {
                    final Node q = new Node();
                    p.children.put(s, q);
                    p = q;
                }
            }
            p.trials.add(trial);
        }
        final ArrayList<Node> heads = new ArrayList<>();
        searchHeads(root, heads);
        for (final Node head : heads) {
            final ArrayList<Trial> cluster = new ArrayList<>();
            searchRemaining(head, cluster);
            if (!cluster.isEmpty()) {
                clusters.add(cluster);
            }
        }
    }

    public JsonArrayBuilder dumpJson() {
        final JsonArrayBuilder array = Json.createArrayBuilder();
        for (final ArrayList<Trial> cluster : clusters) {
            final Map<Bug, Integer> exposures = new HashMap<>();
            final JsonObjectBuilder c = Json.createObjectBuilder();
            c.add("trials_number", cluster.size());
            final JsonArrayBuilder trials = Json.createArrayBuilder();
            for (final Trial trial : cluster) {
                trials.add(trial.dumpJson(spec));
                for (final Bug bug : spec.experiment.bugs) {
                    if (spec.experiment.exposures.get(bug).contains(trial))
                        exposures.merge(bug, 1, Integer::sum);
                }
            }
            for (final Map.Entry<Bug, Integer> entry : exposures.entrySet()) {
                c.add("bug_" + entry.getKey().name, entry.getValue());
            }
            c.add("trials", trials);
            array.add(c);
        }
        return array;
    }
}
