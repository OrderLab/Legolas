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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class Bug {
    private static final Logger LOG = LoggerFactory.getLogger(Bug.class);

    private final ArrayList<String[]> stacktracePatterns = new ArrayList<>();

    public final String name;
    public final Boolean crash;
    public final int phaseThreshold;
    public final List<Map<String, String[]>> workloadResultTypeMaps
            = new ArrayList<>();
    public final String clientException;

    public final int injectionServer;

    public final String injectionServerRole;

    public Bug(final JsonObject json) {
        name = json.getString("name");
        if (json.containsKey("crash")) {
            crash = json.getBoolean("crash");
        } else {
            crash = null;
        }
        phaseThreshold = json.getInt("phase_threshold", -1);
        if (json.containsKey("workload_result_types")) {
            JsonArray ja = json.getJsonArray("workload_result_types");
            for (int i = 0; i < ja.size(); i++) {
                Map<String, String[]> resultTypeMap = new HashMap<>();
                JsonObject jo = ja.getJsonObject(i);
                for (String key : jo.keySet()) {
                    String[] typestrs = jo.getString(key).split("\\s+");
                    resultTypeMap.put(key, typestrs);
                }
                workloadResultTypeMaps.add(resultTypeMap);
            }
        } else {
            LOG.warn("Bug " + name + "'s spec does not specify expected workload results");
        }

        clientException = json.getString("client_exception", null);
        injectionServer = json.getInt("injection_server", -1);
        injectionServerRole = json.getString("injection_server_role", null);

        final JsonArray stacktraces = json.getJsonArray("stacktraces");
        for (int i = 0; i < stacktraces.size(); i++) {
            final JsonArray stacktrace = stacktraces.getJsonArray(i);
            final String[] s = new String[stacktrace.size()];
            for (int j = 0; j < stacktrace.size(); j++) {
                s[j] = stacktrace.getString(j);
            }
            stacktracePatterns.add(s);
        }
    }

    public boolean match(final Trial trial) {
        if (crash != null) {
            if (crash) {
                boolean crashed = false;
                for (final ServerLog serverLog : trial.servers) {
                    if (serverLog.crashed) {
                        crashed = true;
                        break;
                    }
                }
                if (!crashed) {
                    return false;
                }
            } else {
                for (final ServerLog serverLog : trial.servers) {
                    if (serverLog.crashed) {
                        return false;
                    }
                }
            }
        }
        if (phaseThreshold >= 0 && trial.phaseClients.size() < phaseThreshold) {
            return false;
        }
        if (clientException != null && !trial.clientExceptions.contains(this)) {
            return false;
        }
        if (injectionServer > 0 && trial.injectedServer != injectionServer) {
            LOG.debug("Expecting injected server {}, actual {}", injectionServer, trial.injectedServer);
            return false;
        }
        if (injectionServerRole != null) {
            if (trial.injectedServer <= 0)
                return false;
            String actualRole = trial.servers.get(trial.injectedServer - 1).role;
            LOG.debug("Expecting injected server role {}, actual role {}", injectionServerRole, actualRole);
            if (!injectionServerRole.equals(actualRole)) {
                return false;
            }
        }
        if (!matchStacktrace(trial)) {
            return false;
        }
        // only match workload result after injection stack traces match
        return matchWorkloadResult(trial);
    }

    public boolean matchWorkloadResult (final Trial trial) {
        // If no expected workload results are specified, there is no point to match.
        // We should specify expected workload results for all bugs. The constructor will
        // generate warnings when this config is missing.
        if (workloadResultTypeMaps.isEmpty()) {
            return true;
        }
        LOG.debug("Trial {}'s workload result types: {}", trial.id, trial.phaseResultTypesStrs);
        for (String workload : trial.workloadResultTypeMap.keySet()) {
            WorkloadResultType resultType = trial.workloadResultTypeMap.get(workload);
            LOG.debug("- workload {}'s result type: {}", workload, resultType);
            boolean ok = false;
            StringBuilder expectedStr = new StringBuilder();
            for (Map<String, String[]> map : workloadResultTypeMaps) {
                String[] expectedList = map.get(workload);
                if (expectedList == null) {
                    LOG.warn("No expected result for workload {}", workload);
                    continue;
                }
                List<WorkloadResultType> notTypes = new ArrayList<>();
                List<WorkloadResultType> orTypes = new ArrayList<>();
                for (String expected : expectedList) {
                    WorkloadResultType expectedType;
                    if (expected.charAt(0) == '!') {
                        expectedType = WorkloadResultType.valueOf(expected.substring(1));
                        notTypes.add(expectedType);
                    } else if (expected.equals("*")) {
                        // match any, the following logic will handle this properly
                        continue;
                    } else {
                        expectedType = WorkloadResultType.valueOf(expected);
                        orTypes.add(expectedType);
                    }
                    expectedStr.append(expected).append(" ");
                }
                boolean matchNot = false;
                for (WorkloadResultType rtype : notTypes) {
                    if (rtype == resultType) {
                        matchNot = true;
                        break;
                    }
                }
                if (matchNot)
                    break;
                if (orTypes.isEmpty()) {
                    ok = true;
                } else {
                    for (WorkloadResultType rtype : orTypes) {
                        if (rtype == resultType) {
                            ok = true;
                            break;
                        }
                    }
                }
                if (ok)
                    break;
            }
            if (!ok) {
                LOG.debug("-- does not match expected result '{}'", expectedStr);
                return false;
            } else {
                LOG.debug("-- match expected result!");
            }
        }
        return true;
    }

    public boolean matchStacktrace(final Trial trial) {
        LOG.debug("Matching stack traces for trial {}", trial.id);
        LOG.debug("======");
        for (final String[] stacktrace : stacktracePatterns) {
            LOG.debug("  lengths of stack traces: {} vs {}", trial.injectionStacktrace.length,
                  stacktrace.length);
            if (trial.injectionStacktrace.length < stacktrace.length) {
                continue;
            }
            boolean match = true;
            LOG.debug("======");
            for (int i = 0; i < stacktrace.length; i++) {
                LOG.debug("- stack trace entry {}: {} vs {}", i, 
                    trial.injectionStacktrace[i], stacktrace[i]);
                if (stacktrace[i].equals("*")) {
                    // match any stack trace
                    continue;
                }
                if (!trial.injectionStacktrace[i].contains(stacktrace[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                LOG.debug("Found match!");
                return true;
            }
            LOG.debug("======");
        }
        LOG.debug("Did not find match");
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Bug bug = (Bug) o;
        return Objects.equals(name, bug.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
