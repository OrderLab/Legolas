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

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

import java.util.Collection;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Result {
    private static final Logger LOG = LoggerFactory.getLogger(Result.class);

    public final Spec spec;

    public final ArrayList<Trial> allTrials = new ArrayList<>();

    public final ArrayList<Trial> invalidTrials = new ArrayList<>();

    public final ArrayList<Trial> successWithoutInjection = new ArrayList<>();
    public final ArrayList<Trial> failureWithoutInjection = new ArrayList<>();

    public final ArrayList<Trial> successWithInjection = new ArrayList<>();
    public final ArrayList<Trial> failureWithInjection = new ArrayList<>();

    public final ArrayList<Trial> crashFailures = new ArrayList<>();
    public final ArrayList<Trial> localFailures = new ArrayList<>();
    public final ArrayList<Trial> distributedFailures = new ArrayList<>();
    public final ArrayList<Trial> otherFailures = new ArrayList<>();

    public final Group grouping;

    public int invalidInjectionTrials = 0;

    public Result(final Spec spec) {
        this.spec = spec;
        this.grouping = new Group(spec, "auto");
    }

    private boolean isSuccess(final Trial trial) {
        if (trial.phaseClients.size() < spec.phases.size()) {
            return false;
        }
        for (int i = 0; i < spec.phases.size(); i++) {
            final Spec.Phase phase = spec.phases.get(i);
            final ClientLog[][] clients = trial.phaseClients.get(i);
            for (int j = 0; j < phase.workloads.length; j++) {
                for (int k = 0; k < phase.workloads[j].number; k++) {
                    if (clients[j][k].progress.size() < phase.workloads[j].progress[k]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean existCrashFailure(final Trial trial) {
        for (int i = 0; i < trial.phaseClients.size(); i++) {
            final Spec.Phase phase = spec.phases.get(i);
            final ClientLog[][] clients = trial.phaseClients.get(i);
            for (int j = 0; j < phase.workloads.length; j++) {
                for (int k = 0; k < phase.workloads[j].number; k++) {
                    if (clients[j][k].progress.size() < phase.workloads[j].progress[k] &&
                            !trial.servers.get(phase.workloads[j].target[k] - 1).ready) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
     * exists a client failure that can't be explained by server crash
     */
    private boolean existLocalFailure(final Trial trial) {
        for (int i = 0; i < trial.phaseClients.size(); i++) {
            final Spec.Phase phase = spec.phases.get(i);
            final ClientLog[][] clients = trial.phaseClients.get(i);
            for (int j = 0; j < phase.workloads.length; j++) {
                for (int k = 0; k < phase.workloads[j].number; k++) {
                    if (clients[j][k].progress.size() < phase.workloads[j].progress[k] &&
                            trial.servers.get(phase.workloads[j].target[k] - 1).ready) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
     * exists a client failure whose server doesn't get the fault injection
     */
    private boolean existDistributedFailure(final Trial trial) {
        int injectionServer = trial.injectedServer;
        if (spec.masterMode && injectionServer != -1) {
            injectionServer = 1;
        }
        for (int i = 0; i < trial.phaseClients.size(); i++) {
            final Spec.Phase phase = spec.phases.get(i);
            final ClientLog[][] clients = trial.phaseClients.get(i);
            for (int j = 0; j < phase.workloads.length; j++) {
                for (int k = 0; k < phase.workloads[j].number; k++) {
                    if (clients[j][k].progress.size() < phase.workloads[j].progress[k] &&
                            phase.workloads[j].target[k] != injectionServer) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void add(final Trial trial) {
        if (trial.invalid) {
            invalidTrials.add(trial);
            return;
        }
        allTrials.add(trial);
        if (spec.fp && spec.matchInvalidInjection(trial)) {
            invalidInjectionTrials++;
            return;
        }
        if (isSuccess(trial)) {
            if (trial.injectedServer == -1) {
                successWithoutInjection.add(trial);
            } else {
                successWithInjection.add(trial);
                if (spec.benign) {
                    grouping.add(trial, "benig");
                }
            }
        } else {
            if (trial.injectedServer == -1) {
                failureWithoutInjection.add(trial);
            } else {
                failureWithInjection.add(trial);
                if (existDistributedFailure(trial)) {
                    distributedFailures.add(trial);
                    grouping.add(trial, "distr");
                } else {
                    if (existLocalFailure(trial)) {
                        localFailures.add(trial);
                        grouping.add(trial, "local");
                    } else {
                        if (existCrashFailure(trial)) {
                            crashFailures.add(trial);
                            grouping.add(trial, "crash");
                        } else {
                            otherFailures.add(trial);
                            grouping.add(trial, "other");
                        }
                    }
                }
            }
        }
    }

    public void summarize() {
        grouping.cluster();
        grouping.highlight();
    }

    public void dump(String dirPath) throws IOException {
        if (dirPath == null || dirPath.isEmpty())
            return;
        try (final FileWriter writer = new FileWriter(
                dirPath + "/success-without-injection.txt")) {
            for (final Trial trial : successWithoutInjection) {
                writer.write(String.valueOf(trial.id));
                writer.write('\n');
            }
        }
        try (final FileWriter writer = new FileWriter(
                dirPath + "/failure-without-injection.txt")) {
            for (final Trial trial : failureWithoutInjection) {
                writer.write("trial id: ");
                writer.write(String.valueOf(trial.id));
                writer.write('\n');
                for (int i = 0; i < trial.phaseClients.size(); i++) {
                    final ClientLog[][] clients = trial.phaseClients.get(i);
                    final Spec.Phase phase = spec.phases.get(i);
                    for (int j = 0; j < clients.length; j++) {
                        writer.write("Phase ");
                        writer.write(String.valueOf(i));
                        writer.write(' ');
                        writer.write(phase.workloads[j].type);
                        writer.write(": [");
                        for (int k = 0; k < clients[j].length; k++) {
                            writer.write(String.valueOf(clients[j][k].progress.size()));
                            writer.write(", ");
                        }
                        writer.write("]\n");
                    }
                }
            }
        }
        new ArtificialGroup(spec, successWithInjection, "benign").dump(dirPath);
        new ArtificialGroup(spec, distributedFailures, "distributed").dump(dirPath);
        new ArtificialGroup(spec, localFailures, "local").dump(dirPath);
        new ArtificialGroup(spec, crashFailures, "crash").dump(dirPath);
        new ArtificialGroup(spec, otherFailures, "other").dump(dirPath);
        grouping.dump(dirPath);
        spec.experiment.dumpBugs(dirPath);
        if (spec.fp) {
            spec.reportInvalidInjections(dirPath + "/invalid_injections.json");
        }
        if (!invalidTrials.isEmpty()) {
            writeTrialsToJson(invalidTrials, dirPath + "/invalid_trials.json");
        }
    }

    public void enumerateTrials(String dirPath) {
        if (dirPath != null && !dirPath.isEmpty()) {
            writeTrialsToJson(allTrials, dirPath + "/trials.json");
        } else {
            writeTrialsToJson(allTrials, null);
        }
    }

    public void printStats() {
        System.out.println("Total trials: " + allTrials.size());
        System.out.println("|");
        System.out.println("|- invalid injections: " + invalidInjectionTrials);
        System.out.println("|");
        System.out.println("|- w/o injections: " + (successWithoutInjection.size()
              + failureWithoutInjection.size()));
        System.out.println("|       |- success: " + successWithoutInjection.size());
        System.out.println("|       |- failure: " + failureWithoutInjection.size());
        System.out.println("|");
        System.out.println("|- w/ injections: " + (successWithInjection.size()
              + failureWithInjection.size()));
        System.out.println("|       |- success: " + successWithInjection.size());
        System.out.println("|       |- failure: " + failureWithInjection.size());
        System.out.println("|               |- types");
        System.out.println("|                       |- crash: " + crashFailures.size());
        System.out.println("|                       |- local: " + localFailures.size());
        System.out.println("|                       |- distributed: " + distributedFailures.size());
        System.out.println("|                       |- other: " + otherFailures.size());
        System.out.println("|               |-- clusters");
        System.out.println("|                       |- total: " + grouping.clusterNumber);
        System.out.println("|                       |- highlight: " + grouping.highlightCluster);
        System.out.println("|                       |- f/p: " + grouping.falseHighlightCluster);
        spec.experiment.reportBugs();
    }

    private void writeTrialsToJson(Collection<Trial> trials, String fileName) {
        JsonArrayBuilder outArray = Json.createArrayBuilder();
        for (Trial trial : trials) {
            JsonObject json = trial.dumpJson(spec).build();
            outArray.add(json);
        }
        if (fileName != null && !fileName.isEmpty()) {
            try (final FileWriter writer = new FileWriter(fileName);
                    final JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(writer)) {
                jsonWriter.writeArray(outArray.build());
            } catch (IOException e) {
                LOG.error("Fail to write trials.json", e);
            }
        } else {
            StringWriter sw = new StringWriter();
            JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(sw);
            jsonWriter.writeArray(outArray.build());
            jsonWriter.close();
            System.out.println(sw);
        }
    }
}
