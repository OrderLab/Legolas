/*
 *  @author Ryan Huang <ryanph@umich.edu>
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
package edu.umich.order.legolas.injector.policy;

import edu.umich.order.legolas.common.api.AbstractStateServerRemote.MetaInfoAccess;
import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteCommand;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Approximae Meta-Info (SOSP '19) injection policy: granting injection requests when there are
 *  meta info accesses within the last N ms.
 */
public class MetaInfoPolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(MetaInfoPolicy.class);
    protected volatile boolean injected = false;

    public static long accessTimeWindow = 5; // access within 5 ms

    protected MetaInfoAccess lastMetaInfoAccess;

    protected Set<Long> grantedAccessId = new HashSet<>();

    public MetaInfoPolicy(InjectionType injectionType) {
        super(injectionType);
    }

    @Override
    public void setupNewTrial() {
        LOG.info("last meta-info access: {}, granted access ids: {}", lastMetaInfoAccess,
                grantedAccessId.size());
        injected = false;
    }

    @Override
    public InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        if (injected || request.lastMetaInfoAccess == null) {
            return new InjectionRemoteCommand(0, -1, -1);
        }
        long timeDiff = request.lastMetaInfoAccess.accessTime > 0 ?
                System.currentTimeMillis() - request.lastMetaInfoAccess.accessTime : -1;
        // keep a copy of the last access
        lastMetaInfoAccess = new MetaInfoAccess(request.lastMetaInfoAccess);
        String msg = "injected failure in {server=" + request.serverId + ", op='" + request.op + "'}";
        if (injectionType != InjectionType.EXCEPTION && request.delay) {
            if (timeDiff > 0 && timeDiff < accessTimeWindow) {
                if (!grantedAccessId.contains(lastMetaInfoAccess.accessId)) {
                    grantedAccessId.add(lastMetaInfoAccess.accessId);
                    LOG.info(msg);
                    injected = true;
                    return new InjectionRemoteCommand(1, -1, 0);
                }
            }
        }
        if (injectionType != InjectionType.DELAY && request.eids.length > 0) {
            for (final int eid : request.eids) {
                if (timeDiff > 0 && timeDiff < accessTimeWindow) {
                    if (!grantedAccessId.contains(lastMetaInfoAccess.accessId)) {
                        grantedAccessId.add(lastMetaInfoAccess.accessId);
                        LOG.info(msg);
                        injected = true;
                        return new InjectionRemoteCommand(0, eid, 0);
                    }
                }
            }
        }
        return new InjectionRemoteCommand(0, -1, -1);
    }
}
