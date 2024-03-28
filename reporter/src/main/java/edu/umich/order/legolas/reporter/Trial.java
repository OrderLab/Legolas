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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class Trial {
    private static final Logger LOG = LoggerFactory.getLogger(Trial.class);

    public int id;
    public long time;
    public long duration;
    public int injectedServer = -1;
    public long injectionTime = -1;
    public int severityScore = -1;
    public String[] injectionStacktrace = null;

    public boolean invalid = false;

    public final Set<Bug> clientExceptions = new HashSet<>();
    public Spec spec;

    // TODO: consider servers of restart
    public ArrayList<ServerLog> servers = new ArrayList<>();
    // Each phase has one or more workload, and each workload has one or more clients.
    // The client logs are stored in this 3-d array.
    public ArrayList<ClientLog[][]> phaseClients = new ArrayList<>();

    // Store the workload results for all completed phases.
    public WorkloadResultType[][] phaseResultTypes = null;

    public Map<String, WorkloadResultType> workloadResultTypeMap = new HashMap();

    public String phaseResultTypesStrs;

    public void load(final Spec spec, final String trialPath) throws IOException {
        this.spec = spec;
        for (final Spec.Server server : spec.servers) {
            final ServerLog serverLog = ServerLog.parse(spec, server.id,
                    trialPath + "/logs-" + server.id, server.logFilePattern);
            this.servers.add(serverLog);
            if (serverLog.injectionStacktrace != null) {
                if (this.injectionStacktrace == null) {
                    this.injectionStacktrace = serverLog.injectionStacktrace;
                    this.injectionTime = serverLog.injectionTime;
                    if (this.injectedServer == -1) {
                        this.injectedServer = server.id;
                    }
                } else {
                    LOG.error("injection in multiple servers in trial {}", id);
                }
            }
        }
        int counter = 0;
        int is = (spec.masterMode && injectedServer != -1) ? 1: injectedServer;
        outerloop:
        for (final Spec.Phase phase : spec.phases) {
            final ClientLog[][] pclients = new ClientLog[phase.workloads.length][];
            boolean existValidClient = false;
            for (int i = 0 ; i < phase.workloads.length; i++) {
                final Spec.Workload workload = phase.workloads[i];
                pclients[i] = new ClientLog[workload.number];
                for (int j = 0; j < workload.number; j++) {
                    try {
                        ClientLog clientLog = ClientLog.parse(spec, trialPath + "/client-" + counter + ".out");
                        clientExceptions.addAll(clientLog.clientExceptions);
                        pclients[i][j] = clientLog;
                        existValidClient = true;
                    } catch (final IOException ignored) {
                        pclients[i][j] = ClientLog.emptyClient;
                    }
                    counter++;
                }
                workloadResultTypeMap.put(workload.type,
                        WorkloadResultType.computeResultType(pclients[i], workload, is));
            }
            if (!existValidClient) {
                break;
            }
            phaseClients.add(pclients);
            for (int i = 0; i < pclients.length; i++) {
                for (int j = 0; j < pclients[i].length; j++) {
                    if (pclients[i][j].progress.size() < phase.workloads[i].progress[j]) {
                        break outerloop;
                    }
                }
            }
        }
        phaseResultTypes = computeWorkloadResultTypes();
        if (phaseResultTypes.length == 0) {
            LOG.debug("Trial {} has no workload result", id);
        } else {
            severityScore = computeSeverityScore(phaseResultTypes);
        }
        phaseResultTypesStrs = getWorkloadResultTypeValuesStr(phaseResultTypes);
    }

    public static String getWorkloadResultTypeValuesStr(WorkloadResultType[][] levels) {
        final StringBuilder sb = new StringBuilder();
        for (final WorkloadResultType[] ls : levels) {
            sb.append('_');
            boolean firstLevel = true;
            for (final WorkloadResultType level : ls) {
                if (!firstLevel) {
                    sb.append("-");
                }
                firstLevel = false;
                sb.append(level.getValue());
            }
        }
        return sb.toString();
    }

    public WorkloadResultType[][] computeWorkloadResultTypes() {
        WorkloadResultType[][] result = new WorkloadResultType[phaseClients.size()][];
        int is = (spec.masterMode && injectedServer != -1) ? 1 : injectedServer;
        for (int i = 0; i < phaseClients.size(); i++) {
            final ClientLog[][] clients = phaseClients.get(i);
            final Spec.Phase phase = spec.phases.get(i);
            result[i] = new WorkloadResultType[clients.length];
            for (int j = 0; j < clients.length; j++) {
                result[i][j] = WorkloadResultType.computeResultType(clients[j],
                        phase.workloads[j], is);
            }
        }
        return result;
    }

    public static int computeSeverityScore(WorkloadResultType[][] resultTypes) {
        if (resultTypes.length == 0)
            return 0;
        int score = 0;
        int c = 0;
        for (final WorkloadResultType[] level : resultTypes) {
            for (final WorkloadResultType value : level) {
                c++;
                switch (value) {
                    case COMPLETE:
                        break;
                    case LOCAL_SINGLE_ZERO:
                    case LOCAL_SINGLE_PARTIAL:
                        score += 100;
                        break;
                    case LOCAL_ALL_ZERO:
                    case REMOTE_SINGLE_ZERO:
                    case REMOTE_SINGLE_PARTIAL:
                        score += 200;
                        break;
                    case LOCAL_MULTI_CLIENTS:
                        score += 300;
                        break;
                    case REMOTE_ALL_ZERO:
                    case REMOTE_MULTI_CLIENTS:
                        score += 500;
                        break;
                    case ALL_ZERO:
                    case LOCAL_MULTI_SERVERS:
                        score += 700;
                        break;
                    case REMOTE_MULTI_SERVERS:
                        score += 1000;
                        break;
                    default:
                        throw new RuntimeException("Invalid suspicious level");
                }
            }
        }
        return score / c;
    }

    public JsonObjectBuilder dumpWorkloadJson(final Spec spec) {
        JsonObjectBuilder workloads = Json.createObjectBuilder();
        for (int i = 0; i < this.phaseClients.size(); i++) {
            final ClientLog[][] clients = this.phaseClients.get(i);
            final Spec.Phase phase = spec.phases.get(i);
            WorkloadResultType[] rts = phaseResultTypes[i];

            final JsonObjectBuilder workload = Json.createObjectBuilder();
            for (int j = 0; j < clients.length; j++) {
                final JsonArrayBuilder arr = Json.createArrayBuilder();
                for (int k = 0; k < clients[j].length; k++) {
                    arr.add(clients[j][k].progress.size());
                }
                final JsonObjectBuilder result = Json.createObjectBuilder();
                result.add("progress", arr);
                result.add("result_type", rts[j].toString());
                workload.add(phase.workloads[j].type, result);
            }
            workloads.add("Phase " + i, workload);
        }
        return workloads;
    }

    public JsonObjectBuilder dumpJson(final Spec spec) {
        final JsonObjectBuilder trialtime = Json.createObjectBuilder();
        trialtime.add("start", time);
        trialtime.add("duration", duration);
        trialtime.add("since_experiment", time - spec.experiment.experimentStartTime);

        final JsonObjectBuilder workloads = dumpWorkloadJson(spec);
        final JsonArrayBuilder stacktrace = Json.createArrayBuilder();
        if (this.injectionStacktrace != null) {
            for (String trace : this.injectionStacktrace) {
                stacktrace.add(trace);
            }
        }
        final JsonArrayBuilder bugs = Json.createArrayBuilder();
        List<Bug> exposed = spec.experiment.trialExposures.get(this);
        if (exposed != null) {
            for (Bug bug : exposed) {
                bugs.add(bug.name);
            }
        }

        final JsonObjectBuilder injected = Json.createObjectBuilder();
        if (injectedServer > 0) {
            injected.add("id", injectedServer);
            injected.add("time", injectionTime);
            injected.add("time_since_trial", injectionTime > 0 ? injectionTime - time : -1);
            injected.add("time_since_experiment", injectionTime > 0 ?
                    injectionTime - spec.experiment.experimentStartTime : -1);
            ServerLog server = servers.get(injectedServer - 1);
            injected.add("role", server.role != null ? server.role : "");
            injected.add("ready", server.ready);
            injected.add("crashed", server.crashed);
        } else {
            injected.add("id", -1);
        }

        return Json.createObjectBuilder()
                .add("id", this.id)
                .add("valid", !invalid)
                .add("time", trialtime)
                .add("workload", workloads)
                .add("stacktrace", stacktrace)
                .add("injected_server", injected.build())
                .add("exposed_bugs", bugs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Trial trial = (Trial) o;
        return id == trial.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
