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

import edu.umich.order.legolas.reporter.Spec.Server;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class ServerLog {
    private static final Logger LOG = LoggerFactory.getLogger(ServerLog.class);

    public boolean valid = false;
    public int id;
    public String role;

    public boolean ready = false;

    public boolean crashed = false;

    public String[] injectionStacktrace = null;

    public long injectionTime = -1;

    public static final Pattern stacktracePattern =
            Pattern.compile("\\[(\\([^(]+,.+,-*[0-9]+\\), )+\\]", Pattern.CASE_INSENSITIVE);

    private ServerLog() {
    }

    public static ServerLog parse(final Spec spec, final int id, final String path, final Pattern logFilePattern)
            throws IOException {
        ServerLog instance = new ServerLog();
        instance.id = id;
        if (spec.servers.get(id - 1).readyPattern == null) {
            instance.ready = true;
        }
        final File[] logFiles = new File(path)
                .listFiles((dir, name) -> logFilePattern.matcher(name).find());
        if (logFiles == null) {
            LOG.warn("Non-exist log directory: {}", path);
            return instance;
        }
        if (logFiles.length == 0) {
            LOG.warn("No log file at: {}", path);
            return instance;
        }
        if (logFiles.length > 1) {
            LOG.warn("More than 1 log files detected at: {}", path);
            return instance;
        }
        try (final BufferedReader br = new BufferedReader(new FileReader(logFiles[0]))) {
            String line;
            String previousLine = null;
            Server server = spec.servers.get(id - 1);
            while ((line = br.readLine()) != null) {
                if (server.matchReady(line)) {
                    instance.ready = true;
                }
                if (server.matchCrash(line)) {
                    instance.ready = false;
                    instance.crashed = true;
                }
                String role = server.matchRole(line);
                if (role != null)
                    instance.role = role;
                // determined in LegolasAgent
                if (line.contains("stack trace") && line.contains("injection")) {
                    if (instance.injectionStacktrace != null) {
                        LOG.error("More than one injections found in {}", path);
                        continue;
                    }
                    final Matcher matcher = stacktracePattern.matcher(line);
                    if (matcher.find()) {
                        final ArrayList<String> stacktrace = new ArrayList<>();
                        for (final String trace :
                                line.substring(matcher.start() + 1, matcher.end() - 1)
                                        .split(", ")) {
                            if (trace.length() > 2) {
                                stacktrace.add(trace.substring(1, trace.length() - 1));
                            }
                        }
                        Collections.reverse(stacktrace);
                        instance.injectionStacktrace = new String[stacktrace.size()];
                        for (int i = 0; i < instance.injectionStacktrace.length; i++) {
                            instance.injectionStacktrace[i] = stacktrace.get(i);
                        }
                    }
                    try {
                        instance.injectionTime = Experiment.getTime(line, server.timestampPattern);
                    } catch (ParseException e) {
                        LOG.error("Failed to extract injection time from {}: {}", line, e);
                    }
                } else if (line.contains("LegolasAgent injecting") &&
                        instance.injectionStacktrace == null && previousLine != null) {
                    // for incomplete stack trace due to log length constraint
                    int p = previousLine.length() - 1;
                    if (p > 0 && previousLine.charAt(p) == ']') {
                        final ArrayList<String> stacktrace = new ArrayList<>();
                        while (p > 4 && previousLine.charAt(p - 1) == ' ' &&
                                previousLine.charAt(p - 2) == ',' &&
                                previousLine.charAt(p - 3) == ')') {
                            final int ending = p - 2;
                            p -= 4;
                            // match a line number
                            while (Character.isDigit(previousLine.charAt(p)) ||
                                    previousLine.charAt(p) == '-') {
                                p--;
                            }
                            if (!Character.isDigit(previousLine.charAt(p + 1)) &&
                                    previousLine.charAt(p + 1) != '-') {
                                break;
                            }
                            if (previousLine.charAt(p) != ',') {
                                break;
                            }
                            p--;
                            // match a method name
                            while (previousLine.charAt(p) != ',' && previousLine.charAt(p) != ' ') {
                                p--;
                            }
                            if (previousLine.charAt(p + 1) == ',' ||
                                    previousLine.charAt(p + 1) == ' ') {
                                break;
                            }
                            if (previousLine.charAt(p) != ',') {
                                break;
                            }
                            p--;
                            while (previousLine.charAt(p) != '(' && previousLine.charAt(p) != ' ') {
                                p--;
                            }
                            if (previousLine.charAt(p + 1) == ',' ||
                                    previousLine.charAt(p + 1) == ' ') {
                                break;
                            }
                            if (previousLine.charAt(p) != '(') {
                                break;
                            }
                            stacktrace.add(previousLine.substring(p + 1, ending - 1));
                        }
                        if (!stacktrace.isEmpty()) {
                            instance.injectionStacktrace = new String[stacktrace.size()];
                            for (int i = 0; i < instance.injectionStacktrace.length; i++) {
                                instance.injectionStacktrace[i] = stacktrace.get(i);
                            }
                        }
                    }
                }
                if (line.contains("LegolasAgent")) {
                    previousLine = line;
                }
            }
        }
        instance.valid = true;
        LOG.debug("Server {}: role={}, ready={}, crashed={}", instance.id, instance.role,
                instance.ready, instance.crashed);
        return instance;
    }
}
