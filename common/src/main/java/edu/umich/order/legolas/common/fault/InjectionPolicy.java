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
package edu.umich.order.legolas.common.fault;

import edu.umich.order.legolas.common.api.FaultInjectorRemote.InjectionRemoteCommand;
import edu.umich.order.legolas.common.event.ThreadInjectionRequest;

/**
 * The fault injection policy that decides whether to inject some faults or not and if so which exact
 * fault should be injected.
 * TODO: enable local injection
 */
public interface InjectionPolicy {
    /**
     * Decide whether to inject a delay or some exceptions for a given server and instance.
     *
     * @param request
     *
     * @return
     */
    default InjectionRemoteCommand inject(final ThreadInjectionRequest request) {
        return new InjectionRemoteCommand(0, -1, -1);
    }

    default void setupNewTrial() {};

    enum InjectionType {
        ALL,
        EXCEPTION,
        DELAY
    }
}