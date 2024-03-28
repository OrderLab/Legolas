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

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;

/**
 *
 */
public final class Spec {
    public final String specPath;
    public Experiment experiment;

    public String systemName;
    public int severityThreshold;
    public final ArrayList<Server> servers = new ArrayList<>();
    public final ArrayList<Phase> phases = new ArrayList<>();
    public final ArrayList<Location> locations = new ArrayList<>();

    public boolean masterMode;

    public final ArrayList<InvalidInjection> invalidInjections = new ArrayList<>();
    public boolean benign = false;
    public boolean fp = false;
    public boolean invalid_injections_only_match_ending = false;

    public Spec(String specPath, Experiment experiment) {
        this.specPath = specPath;
        this.experiment = experiment;
    }

    public void parse() throws IOException {
        try (final InputStream inputStream = new FileInputStream(specPath);
                final JsonReader reader = Json.createReader(inputStream)) {
            final JsonObject json = reader.readObject();
            systemName = json.getString("system");
            if (json.containsKey("master_mode")) {
                masterMode = json.getBoolean("master_mode");
            } else {
                masterMode = false;
            }
            this.severityThreshold = json.getInt("severity_threshold", 200);
            // TODO: handle servers with restart
            final JsonArray servers = json.getJsonArray("servers");
            for (int i = 0; i < servers.size(); i++) {
                final JsonObject server = servers.getJsonObject(i);
                final Server s = new Server(i + 1, server.getString("type"),
                        server.getString("log"));
                if (server.containsKey("crash_regex")) {
                    s.crashPattern = Pattern.compile(server.getString("crash_regex"),
                            Pattern.CASE_INSENSITIVE);
                }
                if (server.containsKey("ready_regex")) {
                    s.readyPattern = Pattern.compile(server.getString("ready_regex"),
                            Pattern.CASE_INSENSITIVE);
                }
                if (server.containsKey("timestamp_regex")) {
                    s.timestampPattern = Pattern.compile(server.getString("timestamp_regex"),
                            Pattern.CASE_INSENSITIVE);
                }
                if (server.containsKey("role_regex")) {
                    s.rolePattern = Pattern.compile(server.getString("role_regex"),
                            Pattern.CASE_INSENSITIVE);
                }
                if (server.containsKey("role_group"))
                    s.roleGroup = server.getInt("role_group");
                this.servers.add(s);
            }
            final JsonArray phases = json.getJsonArray("workload");
            for (int i = 0; i < phases.size(); i++) {
                final JsonArray workloads = phases.getJsonArray(i);
                final Phase phase = new Phase(i, workloads.size());
                this.phases.add(phase);
                for (int j = 0; j < workloads.size(); j++) {
                    final JsonObject workload = workloads.getJsonObject(j);
                    phase.workloads[j] = new Workload(
                            workload.getString("type"),
                            workload.getInt("number"),
                            workload.getJsonArray("progress"),
                            workload.getJsonArray("target"));
                }
            }
            final JsonArray locations = json.getJsonArray("cluster");
            for (int i = 0; i < locations.size(); i++) {
                final JsonObject location = locations.getJsonObject(i);
                this.locations.add(new Location(location.getString("type"),
                        location.getJsonArray("pattern")));
            }
            final JsonArray invalidInjections = json.getJsonArray("invalid_injections");
            if (invalidInjections != null) {
                for (int i = 0; i < invalidInjections.size(); i++) {
                    this.invalidInjections.add(
                            new InvalidInjection(invalidInjections.getJsonArray(i)));
                }
            }
            if (json.containsKey("invalid_injections_only_match_ending") &&
                    json.getBoolean("invalid_injections_only_match_ending")) {
                this.invalid_injections_only_match_ending = true;
            }
        }
    }

    public static final class Server {
        public final int id;
        public final String type;

        public final Pattern logFilePattern;
        public Pattern crashPattern = null;
        public Pattern readyPattern = null;
        public Pattern timestampPattern = null;

        public Pattern rolePattern = null;

        public int roleGroup = 0;

        public Server(final int id, final String type, final String logFilePattern) {
            this.id = id;
            this.type = type;
            this.logFilePattern = Pattern.compile(logFilePattern, Pattern.CASE_INSENSITIVE);
        }
        public boolean matchCrash(final String line) {
            if (this.crashPattern == null) {
                return false;
            }
            return this.crashPattern.matcher(line).find();
        }
        public boolean matchReady(final String line) {
            if (this.readyPattern == null) {
                return false;
            }
            return this.readyPattern.matcher(line).find();
        }

        public String matchRole(final String line) {
            if (rolePattern == null) {
                return null;
            }
            final Matcher matcher = rolePattern.matcher(line);
            if (!matcher.find())
                return null;
            return matcher.group(roleGroup);
        }
    }

    public static final class Phase {
        public final int id;
        public final Workload[] workloads;
        public Phase(final int id, final int workloadNumber) {
            this.id = id;
            this.workloads = new Workload[workloadNumber];
        }
    }

    public static final class Workload {
        public final String type;
        public final int number;
        public final int[] progress;
        public final int[] target;
        public Workload(final String type, final int number,
                final JsonArray progress, final JsonArray target) {
            this.type = type;
            this.number = number;
            this.progress = new int[number];
            this.target = new int[number];
            for (int i = 0; i < number; i++) {
                this.progress[i] = progress.getInt(i);
                this.target[i] = target.getInt(i);
            }
        }
    }

    public static final class Location {
        public final String type;
        public final LocationPattern[] patterns;
        public Location(final String type, final JsonArray patterns) {
            this.type = type;
            this.patterns = new LocationPattern[patterns.size()];
            for (int i = 0; i < patterns.size(); i++) {
                final JsonObject pattern = patterns.getJsonObject(i);
                this.patterns[i] = new LocationPattern(pattern.getString("trace_regex"),
                        pattern.getInt("depth"));
            }
        }
        public boolean match(final String[] stacktrace) {
            for (final LocationPattern p : this.patterns) {
                if (!p.match(stacktrace)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class LocationPattern {
        public final int depth;
        public final Pattern p;
        public LocationPattern(final String trace_regex, final int depth) {
            this.p = Pattern.compile(trace_regex, Pattern.CASE_INSENSITIVE);
            this.depth = depth;
        }
        public boolean match(final String[] stacktrace) {
            if (stacktrace.length < this.depth) {
                return false;
            }
            return p.matcher(stacktrace[this.depth - 1]).find();
        }
    }

    public boolean matchInvalidInjection(final Trial trial) {
        if (trial.injectedServer == -1) {
            return false;
        }
        for (final InvalidInjection invalidInjection : this.invalidInjections) {
            if (invalidInjection.match(trial.injectionStacktrace)) {
                invalidInjection.trials.add(trial);
                return true;
            }
        }
        return false;
    }

    public void reportInvalidInjections(final String path) {
        final JsonArrayBuilder arr = Json.createArrayBuilder();
        int total = 0;
        for (final InvalidInjection invalidInjection : this.invalidInjections) {
            arr.add(invalidInjection.dump());
            total += invalidInjection.trials.size();
        }
        System.out.println("# of invalid injections = " + total);
        try (final FileWriter writer = new FileWriter(path);
                final JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(writer)) {
            jsonWriter.writeObject(Json.createObjectBuilder().add("invalid_injections", arr).build());
        } catch (final IOException ignored) { }
    }

    public final class InvalidInjection {
        public final String[] locations;
        public final ArrayList<Trial> trials = new ArrayList<>();
        public InvalidInjection(final JsonArray locations) {
            this.locations = new String[locations.size()];
            for (int i = 0; i < locations.size(); i++) {
                this.locations[i] = locations.getString(i);
            }
        }
        public boolean match(final String[] stacktrace) {
            if (stacktrace.length < locations.length) {
                return false;
            }
            for (int i = invalid_injections_only_match_ending ? stacktrace.length - locations.length : 0;
                    i + locations.length <= stacktrace.length; i++) {
                boolean match = true;
                for (int j = 0; j < locations.length; j++) {
                    if (!stacktrace[i + j].equals(locations[j])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            }
            return false;
        }
        public JsonObjectBuilder dump() {
            final JsonArrayBuilder pattern = Json.createArrayBuilder();
            for (final String location : this.locations) {
                pattern.add(location);
            }
            final JsonArrayBuilder trials = Json.createArrayBuilder();
            for (final Trial trial : this.trials) {
                trials.add(trial.dumpJson(Spec.this));
            }
            return Json.createObjectBuilder()
                    .add("pattern", pattern)
                    .add("trial_number", this.trials.size())
                    .add("trials", trials);
        }
    }
}
