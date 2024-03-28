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
import java.util.Map;

/**
 *
 */
public final class RecordWriter {
    final private static String[] FIELD_NAMES = {
            "event-id",
            "time",
            "server-id",
            "sm-id",
            "state-op-id",
            "state-id",
            "instance-id",
            "op-id",
            "delay",
            "exceptions",
            "grant-delay",
            "grant-exception",
            "injection-id",
    };
    private final static String CSV_HEADER = String.join(",", FIELD_NAMES);

    private final Map<String, Integer> stateMachines;
    private final Map<String, Integer> ops;
    private final long startNano;
    private final BufferedWriter csv;

    protected RecordWriter(final BufferedWriter csv,final long startNano,
            final Map<String, Integer> stateMachines, final Map<String, Integer> ops) {
        this.csv = csv;
        this.startNano = startNano;
        this.stateMachines = stateMachines;
        this.ops = ops;
    }

    public void writeHeader() throws IOException {
        csv.write(CSV_HEADER + "\n");
    }

    public void write(final Event e) throws IOException {
        csv.write(Long.toString(e.nano - startNano));
        append(e.getType());
        e.dump(this);
        csv.write('\n');
    }

    public void append(int v) throws IOException {
        csv.write(',');
        csv.write(String.valueOf(v));
    }

    public void appendExceptions(final int[] eids) throws IOException {
        if (eids.length == 0) {
            csv.write(',');
            return;
        }
        append(eids[0]);
        for (int i = 1; i < eids.length; i++) {
            csv.write('|');
            csv.write(String.valueOf(eids[i]));
        }
    }

    public int getStateMachine(final String stateMachineName) {
        if (stateMachines.containsKey(stateMachineName)) {
            return stateMachines.get(stateMachineName);
        }
        final int result = stateMachines.size();
        stateMachines.put(stateMachineName, result);
        return result;
    }

    public int getOp(final String op) {
        if (ops.containsKey(op)) {
            return ops.get(op);
        }
        final int result = ops.size();
        ops.put(op, result);
        return result;
    }
}
