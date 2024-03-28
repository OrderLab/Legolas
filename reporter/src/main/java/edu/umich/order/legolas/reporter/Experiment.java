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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Experiment {
    private static final Logger LOG = LoggerFactory.getLogger(Experiment.class);

    public static final JsonWriterFactory jsonWriterFactory;

    static {
        final Map<String, Object> options = new HashMap<>();
        options.put(JsonGenerator.PRETTY_PRINTING, true);
        jsonWriterFactory = Json.createWriterFactory(options);
    }

    // determined by the orchestrator log4j.properties
    public final static SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    public final static SimpleDateFormat dateFormat2 =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS");
    public final static Pattern injectionServerPattern =
            Pattern.compile("server=[0-9]+\\D", Pattern.CASE_INSENSITIVE);

    public final static String TRIAL_ID_LINE = "starting trial ";

    public final static int TRIAL_ID_LINE_LEN = TRIAL_ID_LINE.length();

    public final ArrayList<Trial> trials = new ArrayList<>();
    final ArrayList<Integer> invalidTrials = new ArrayList<>();

    public final ArrayList<Bug> bugs = new ArrayList<>();
    public final Map<Bug, ArrayList<Trial>> exposures = new HashMap<>();

    public final Map<Trial, List<Bug>> trialExposures = new HashMap<>();

    public long experimentStartTime = -1;
    public long experimentEndTime = -1;
    public int trialNumber;
    public int startTrialId;
    public int endTrialId;

    public String experimentDir;
    public String trialsDir;
    public Spec spec;

    public Experiment(String experimentDir, String trialsDir, int trialNumber,
            int startTrialId, int endTrialId) {
        this.experimentDir = experimentDir;
        this.trialsDir = trialsDir;
        this.trialNumber = trialNumber;
        this.startTrialId = startTrialId;
        this.endTrialId = endTrialId;
        if (this.trialNumber <= 0) {
            this.trialNumber = 0;
            File trialsDirFile = new File(trialsDir);
            for (File f : trialsDirFile.listFiles()) {
                if (f.isDirectory()) {
                    try {
                        Integer.parseInt(f.getName());
                        this.trialNumber++;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (this.endTrialId < 0)
            this.endTrialId = this.trialNumber - 1;
        LOG.debug("Parsing experiment under {}, start: {}, end: {}, total: {}", experimentDir,
                startTrialId, this.endTrialId, this.trialNumber);
    }

    public void parseExperimentLog(String logFileName) throws IOException, ParseException {
        try (final BufferedReader br = new BufferedReader(new FileReader(logFileName))) {
            String line;
            Trial trial = null;
            while ((line = br.readLine()) != null) {
                if (line.contains("Bootstrapping Legolas orchestrator server")) {
                    experimentStartTime = getTime(line, null);
                }
                int idx = line.indexOf(TRIAL_ID_LINE);
                if (idx >= 0) {
                    String idstr = line.substring(idx + TRIAL_ID_LINE_LEN).trim();
                    idx = idstr.indexOf(' ');
                    if (idx >= 0) {
                        idstr = idstr.substring(0, idx);
                    }
                    trial = new Trial();
                    trial.id = Integer.parseInt(idstr);
                    trial.time = getTime(line, null);
                    if (trials.size() > trial.id) {
                        // This must happen because of some failed trial is being retried...
                        if (!line.contains("retry")) {
                            LOG.error("Trial {} already exists and is not retried", trial.id);
                        } else {
                            LOG.warn("Replacing failed trial {} with retried trial", trial.id);
                            trials.set(trial.id, trial);
                        }
                    } else {
                        trials.add(trial);
                    }
                }
                if (line.contains("Legolas orchestrator server ends")) {
                    experimentEndTime = getTime(line, null);
                }
                // determined by orchestrator & its log4j.properties
                if (line.contains(" - injected in ")) {
                    final Matcher matcher = injectionServerPattern.matcher(line);
                    if (matcher.find()) {
                        trial.injectedServer = Integer.parseInt(line.substring(
                                matcher.start() + "server=".length(), matcher.end() - 1));
                    }
                }
            }
        }

        LOG.debug("Parsed {} trials from log", trials.size());
        if (trialNumber > trials.size()) {
            LOG.error("Expecting {} trials, only found {} trials from log", trialNumber, trials.size());
        } else {
            for (int i = 0; i < trialNumber - 1; i++) {
                trials.get(i).duration = trials.get(i + 1).time - trials.get(i).time;
            }
            if (trialNumber < trials.size()) {
                trials.get(trialNumber - 1).duration = trials.get(trialNumber).time -
                        trials.get(trialNumber - 1).time;
            } else {
                trials.get(trialNumber - 1).duration = experimentEndTime -
                        trials.get(trialNumber - 1).time;
            }
        }
    }

    public void parseTrials() throws IOException {
        for (int i = startTrialId; i <= endTrialId; i++) {
            final Trial trial = trials.get(i);
            trial.load(spec, trialsDir + "/" + i);
            if (trial.injectionStacktrace == null && trial.injectedServer != -1) {
                LOG.warn("invalid trial {}: injected server {} but no injection stack trace found",
                        trial.id, trial.injectedServer);
                trial.invalid = true;
                invalidTrials.add(i);
            }
        }
        if (invalidTrials.size() == (endTrialId - startTrialId + 1)) {
            LOG.warn("all {} trials are invalid", invalidTrials.size());
            return;
        }
        LOG.debug("Loaded {} valid trials, {} invalid trials", trials.size(), invalidTrials.size());
    }

    public void matchBugs() {
        // Since we only parsed trials in [start, end], we should only match bugs for those trials
        for (int i = startTrialId; i <= endTrialId; i++) {
            final Trial trial = trials.get(i);
            if (trial.invalid || trial.injectedServer <= 0) {
                LOG.debug("Skip matching bug for trial {}: {}", trial.id, trial.invalid ?
                        "invalid" : "no injection");
                continue;
            }
            for (final Bug bug : bugs) {
                if (bug.match(trial)) {
                    exposures.get(bug).add(trial);
                    if (!trialExposures.containsKey(trial)) {
                        trialExposures.put(trial, new ArrayList<>());
                    }
                    trialExposures.get(trial).add(bug);
                }
            }
        }
        for (final Bug bug : bugs) {
            final ArrayList<Trial> trials = exposures.get(bug);
            trials.sort(ArtificialGroup.idComparator);
        }
    }

    public void parseSpec(String specPath, boolean benign, boolean fp) throws IOException{
        spec = new Spec(specPath, this);
        spec.parse();
        spec.benign = benign;
        spec.fp = fp;
    }

    public void loadBugs(final String bugsSpec) throws IOException {
        final File file = new File(bugsSpec);
        if (!file.exists()) {
            LOG.warn("bug file {} not exists", bugsSpec);
            return;
        }
        try (final InputStream inputStream = new FileInputStream(file);
                final JsonReader reader = Json.createReader(inputStream)) {
            final JsonObject json = reader.readObject();
            final JsonArray array = json.getJsonArray("bugs");
            for (int i = 0; i < array.size(); i++) {
                bugs.add(new Bug(array.getJsonObject(i)));
            }
        }
        for (final Bug bug : bugs) {
            exposures.put(bug, new ArrayList<>());
        }
    }

    public void reportBugs() {
        System.out.println("Bugs:");
        for (final Bug bug : bugs) {
            final ArrayList<Trial> trials = exposures.get(bug);
            if (trials.isEmpty()) {
                System.out.printf("|- %-18s: %-10s %-6s %-8s\n", bug.name, "N/A", "N/A", "N/A");
            } else {
                System.out.printf("|- %-18s: %-10s %-6s %-8s\n",
                        bug.name, 100.0 * trials.size() / trialNumber + "%",
                        trials.get(0).id + 1,
                        (trials.get(0).time - experimentStartTime) / (60 * 1000.0));
            }
            System.out.print("|\t|- hit trials: [ ");
            for (final Trial trial : trials) {
                System.out.print(trial.id);
                System.out.print(", ");
            }
            System.out.println("]");
        }
    }

    public void dumpBugs(String dirPath) {
        if (dirPath == null || dirPath.isEmpty())
            return;
        JsonObjectBuilder allBugsHits = Json.createObjectBuilder();
        String bugsPath = dirPath + "/exposed_bugs";
        for (final Bug bug : bugs) {
            final ArrayList<Trial> trials = exposures.get(bug);
            JsonObjectBuilder bugHits = Json.createObjectBuilder();
            JsonArrayBuilder hits = Json.createArrayBuilder();
            JsonObjectBuilder hitTrialWorkloads = Json.createObjectBuilder();
            JsonObjectBuilder hitTrialInjections = Json.createObjectBuilder();
            if (!trials.isEmpty()) {
                File dir = new File(bugsPath, bug.name);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                for (final Trial trial : trials) {
                    hits.add(trial.id);
                    File trialPath = new File(dir, "trial_" + trial.id + ".json");
                    try (final FileWriter writer = new FileWriter(trialPath);
                            final JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(
                                    writer)) {
                        jsonWriter.writeObject(trial.dumpJson(spec).build());
                    } catch (IOException e) {
                        LOG.error("Failed to write trial {} to {}", trial.id, trialPath, e);
                    }
                    JsonObjectBuilder workload = trial.dumpWorkloadJson(spec);
                    hitTrialWorkloads.add(Integer.toString(trial.id), workload);
                    JsonObjectBuilder injection = Json.createObjectBuilder();
                    int isid = trial.injectedServer;
                    injection.add("injected_server_id", isid);
                    String isrole = null;
                    if (isid > 0) {
                      isrole = trial.servers.get(isid - 1).role;
                    }
                    injection.add("injected_server_role", isrole == null ? "" : isrole);
                    hitTrialInjections.add(Integer.toString(trial.id), injection);
                }
                bugHits.add("hit_trials", hits);
                bugHits.add("exposure_rate", 100.0 * trials.size() / trialNumber);
                bugHits.add("first_exposure_trial", trials.get(0).id + 1);
                bugHits.add("first_exposure_minutes", (trials.get(0).time -
                        experimentStartTime) / (60 * 1000.0));
                bugHits.add("hit_trial_workloads", hitTrialWorkloads);
                bugHits.add("hit_trial_injections", hitTrialInjections);

            } else {
                bugHits.add("hit_trials", hits);
                bugHits.add("exposure_rate", 0.0);
                bugHits.add("first_exposure_trial", -1);
                bugHits.add("first_exposure_minutes", -1);
                bugHits.add("hit_trial_workloads", hitTrialWorkloads);
                bugHits.add("hit_trial_injections", hitTrialInjections);
            }
            allBugsHits.add(bug.name, bugHits);
        }
        try (final FileWriter writer = new FileWriter(dirPath + "/bug-hits.json");
                final JsonWriter jsonWriter = Experiment.jsonWriterFactory.createWriter(writer)) {
            jsonWriter.writeObject(allBugsHits.build());
        } catch (IOException e) {
            LOG.error("Failed to write bug-hits.json", e);
        }
    }

    public static long getTime(String line, Pattern pattern) throws ParseException {
        String dateLiteral = "";
        if (pattern == null) {
            dateLiteral = line.substring(0,
                    line.indexOf(' ', line.indexOf(' ') + 1));
            if (!dateLiteral.isEmpty() && dateLiteral.charAt(0) == '['
                  && dateLiteral.charAt(dateLiteral.length() - 1) == ']') {
                dateLiteral = dateLiteral.substring(1, dateLiteral.length() - 1);
            }
        } else {
            final Matcher matcher = pattern.matcher(line);
            if (!matcher.find())
                throw new ParseException("Cannot find timestamp", 0);
            dateLiteral = matcher.group(1);
        }
        try {
            return dateFormat.parse(dateLiteral).getTime();
        } catch (java.text.ParseException ignored) {
            return dateFormat2.parse(dateLiteral).getTime();
        }
    }
}
