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
package edu.umich.order.legolas.injector.controller;

import edu.umich.order.legolas.common.fault.InjectionPolicy;
import edu.umich.order.legolas.injector.policy.PolicyFactory;
import java.util.Properties;

/**
 * Factory that produces supported types of controller
 */
public class ControllerFactory {

    public static InjectionController createController(Properties properties) {
        final InjectionPolicy policy = PolicyFactory.createPolicy(properties);
        String controllerType = properties.getProperty("injectionController");
        if (controllerType != null && controllerType.equals("Debug")) {
            return new DebugController(policy);
        }
        int maxTrials = Integer.parseInt(properties.getProperty("maxTrials",
                "2000"));
        return new InjectionController(policy, maxTrials);
    }
}
