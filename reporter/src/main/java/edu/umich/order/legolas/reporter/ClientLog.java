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
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class ClientLog {
    private static final Pattern progressPattern1 =
            Pattern.compile("progress = [0-9]+, time = [0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern progressPattern2 =
            Pattern.compile("progress = [0-9]+, time = [0-9]+, result = .*, client = [0-9]+",
                    Pattern.CASE_INSENSITIVE);

    public final ArrayList<Integer> progress = new ArrayList<>();
    public final ArrayList<Long> time = new ArrayList<>();
    public final ArrayList<String> results = new ArrayList<>();
    public final Set<Bug> clientExceptions = new HashSet<>();

    public static ClientLog parse(final Spec spec, final String path) throws IOException {
        ClientLog instance = new ClientLog();
        try (final BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                final Matcher matcher = progressPattern1.matcher(line);
                if (matcher.find()) {
                    final String s = line.substring(matcher.start(), matcher.end());
                    final int i = s.indexOf('=') + 2;
                    instance.progress.add(Integer.parseInt(s.substring(i, s.indexOf(',', i))));
                    final int j = s.indexOf('=', i) + 2;
                    instance.time.add(Long.parseLong(s.substring(j)));
                }
                for (final Bug bug : spec.experiment.bugs) {
                    if (bug.clientException != null) {
                        if (line.contains(bug.clientException)) {
                            instance.clientExceptions.add(bug);
                        }
                    }
                }
            }
        }
        return instance;
    }

    public static ClientLog parse(final Spec spec, final String path, final int clientId) throws IOException {
        ClientLog instance = new ClientLog();
        try (final BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                final Matcher matcher = progressPattern2.matcher(line);
                if (matcher.find()) {
                    final String s = line.substring(matcher.start(), matcher.end());
                    final int i = s.indexOf('=') + 2;
                    final int progress = Integer.parseInt(s.substring(i, s.indexOf(',', i)));
                    final int j = s.indexOf('=', i) + 2;
                    final long time = Long.parseLong(s.substring(j, s.indexOf(',', j)));
                    final int k = s.indexOf('=', j) + 2;
                    final int t = s.lastIndexOf('=');
                    final int id = Integer.parseInt(s.substring(t + 2));
                    if (id != clientId) {
                        continue;
                    }
                    instance.progress.add(progress);
                    instance.time.add(time);
                    final String result = s.substring(k, t - ", result ".length());
                    instance.results.add(result);
                }
            }
        }
        return instance;
    }

    public ClientLog() { }

    public enum ClientProgressType {
        ZERO,
        PARTIAL,
        COMPLETE
    }

    public ClientProgressType getProgressType(int expected) {
        if (progress.size() == 0)
            return ClientProgressType.ZERO;
        if (progress.size() == expected)
            return ClientProgressType.COMPLETE;
        return ClientProgressType.PARTIAL;
    }

    public static final ClientLog emptyClient = new ClientLog();
}
