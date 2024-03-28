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
package edu.umich.order.legolas.common.agent;

import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import edu.umich.order.legolas.common.fault.InjectionManager;

/**
 * This is the substrate for the global {@link LegolasAgent}, which maintains global data structures
 * to keep track of the information happening inside the target system.
 */
public final class LegolasAgentSubstrate {
    protected AbstractStateMachineManager asmManager;
    protected InjectionManager injectionManager;

    public LegolasAgentSubstrate(AbstractStateMachineManager asmm, InjectionManager sim) {
        asmManager = asmm;
        injectionManager = sim;
    }
}
