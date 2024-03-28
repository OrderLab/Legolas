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
package edu.umich.order.legolas.common.fault;

import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data structure that keeps track of fault information in all AbstractStateMachines in a system.
 * TODO: to be removed
 * TODO: to be used
 */
public class InjectionManager {
    private static final Logger LOG = LoggerFactory.getLogger(InjectionManager.class);
    private static final DateFormat PLOT_DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public final AbstractStateMachineManager asmManager;

    public InjectionManager(AbstractStateMachineManager manager) {
        asmManager = manager;
    }

    /**
     * Record an injection request to an instance id (ASM). Note that the injection request does not
     * mean it is granted.
     * TODO: we may need to add another api to record the states that some fault is injected
     */
}
