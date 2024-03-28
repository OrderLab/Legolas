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
package edu.umich.order.legolas.common.record;

import edu.umich.order.legolas.common.event.Event;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

/**
 * TODO: refactor into local and global stats
 */
public final class OrchestratorStats {
    private static final String[] EVENT_NAMES = {
            "state",
            "injectionRequest", // injection not granted
            "injectionEvent",  // granted injection
            "start",
            "shutdown",
            "ready"
    };

    private long startNano = 0;
    private final LinkedList<Event> events = new LinkedList<>();
    private String[] exceptionNames = null;

    public boolean recordStates = true;

    public final void init(final String[] exceptionNames) {
        this.exceptionNames = exceptionNames;
        this.events.clear();
        startNano = System.nanoTime();
    }

    public final synchronized void record(final Event e) {
        if (recordStates) {
            events.add(e);
        }
    }

    private static String[] indexMapToArray(final Map<String, Integer> map) {
        final String[] arr = new String[map.size()];
        for (final Map.Entry<String, Integer> entry : map.entrySet()) {
            arr[entry.getValue()] = entry.getKey();
        }
        return arr;
    }

    private void dumpJsonArray(final JsonObjectBuilder jsonBuilder, final String[] arr,
            final String name) {
        final JsonArrayBuilder arrBuilder = Json.createArrayBuilder();
        for (final String e : arr) {
            arrBuilder.add(e);
        }
        jsonBuilder.add(name, arrBuilder);
    }

    public void dump(final BufferedWriter csv, final BufferedWriter json,
            final JsonObjectBuilder jsonBuilder) throws IOException {
        jsonBuilder.add("start_time", startNano);
        dumpJsonArray(jsonBuilder, exceptionNames, "events");
        final Map<String, Integer> stateMachines = new HashMap<>();
        final Map<String, Integer> ops = new HashMap<>();
        final RecordWriter recordWriter = new RecordWriter(csv, startNano, stateMachines, ops);
        recordWriter.writeHeader();
        for (final Event e : events) {
            recordWriter.write(e);
        }
        dumpJsonArray(jsonBuilder, indexMapToArray(stateMachines), "state_machines");
        dumpJsonArray(jsonBuilder, indexMapToArray(ops), "ops");
        json.write(jsonBuilder.build().toString());
    }
}
