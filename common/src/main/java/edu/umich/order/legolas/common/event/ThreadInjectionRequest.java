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
package edu.umich.order.legolas.common.event;

import edu.umich.order.legolas.common.api.AbstractStateServerRemote.MetaInfoAccess;
import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteQuery;
import edu.umich.order.legolas.common.asm.AbstractState;
import edu.umich.order.legolas.common.record.RecordWriter;
import java.io.IOException;

/**
 * TODO: find a more general abstraction of injection
 */
public class ThreadInjectionRequest extends ThreadStateEvent {
    public final String className;
    public final String methodName;
    public final int lineNum;
    public final long stackTraceId;
    public final String op;
    public final boolean delay;
    public final int[] eids;

    public long failureId; // only used when evaluating FATE
    public MetaInfoAccess lastMetaInfoAccess; // only used when evaluating meta-info analysis

    public ThreadInjectionRequest(long nano, int serverId, String threadName,
            int instanceId, String className, String methodName, int lineNum, 
            long stackTraceId, long failureId, MetaInfoAccess lastMetaInfoAccess, 
            String stateMachineName, AbstractState state, 
            String op, boolean delay, int[] eids) {
        super(nano, serverId, threadName, instanceId, stateMachineName, state);
        this.className = className;
        this.methodName = methodName;
        this.lineNum = lineNum;
        this.stackTraceId = stackTraceId;
        this.op = op;
        this.failureId = failureId;
        this.lastMetaInfoAccess = lastMetaInfoAccess;
        this.delay = delay;
        this.eids = eids;
    }

    public ThreadInjectionRequest(ThreadInjectionRequest copy) {
        this(copy.nano, copy.serverId, copy.threadName, copy.instanceId,
                copy.className, copy.methodName, copy.lineNum,
                copy.stackTraceId, copy.failureId, copy.lastMetaInfoAccess,
                copy.stateMachineName, copy.state,
                copy.op, copy.delay, copy.eids);
    }

    public ThreadInjectionRequest(InjectionRemoteQuery query) {
        this(System.nanoTime(), query.serverId, query.threadName, 
            -1, query.location.className, query.location.methodName, 
            query.location.lineNum, query.location.stackTraceId, 
            query.location.failureId, null, "", null,
            query.location.op, query.delay==1, query.exceptionIds);
    }

    @Override
    public void dump(final RecordWriter writer) throws IOException {
        super.dump(writer);
        writer.append(writer.getOp(op));
        writer.append(delay ? 1 : 0);
        writer.appendExceptions(eids);
    }

    @Override
    public int getType() {
        return 4;
    }

    public long hashId() {
      long exception = 0;
        for (int eid : eids) {
            exception = eid * 31L + exception;
        }
      return serverId * 37L + threadName.hashCode() * 41L + className.hashCode() * 43L +
          methodName.hashCode() * 47L + lineNum * 53L + op.hashCode() * 59L +
          stackTraceId * 61L + exception * 67L + (delay ? 71L : 73L);
    }
}
