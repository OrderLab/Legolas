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
package edu.umich.order.legolas.injector.policy;

import edu.umich.order.legolas.common.fault.InjectionPolicy.InjectionType;
import edu.umich.order.legolas.common.fault.InjectionPolicy;
import java.util.Properties;

/**
 *
 */
public final class PolicyFactory {
    public static InjectionPolicy createPolicy(final Properties properties) {
        InjectionType injectionType;
        switch (properties.getProperty("injectionType", "all")) {
            case "delay"     : injectionType = InjectionType.DELAY;     break;
            case "exception" : injectionType = InjectionType.EXCEPTION; break;
            default          : injectionType = InjectionType.ALL;
        }
        final String randomRate = properties.getProperty("randomRate");
        if (randomRate != null) {
            RandomPolicy.rate = Integer.parseInt(randomRate);
        }
        final String metaInfoWindow = properties.getProperty("metaInfoWindow");
        if (metaInfoWindow != null) {
            MetaInfoPolicy.accessTimeWindow = Integer.parseInt(metaInfoWindow);
        }
        switch (properties.getProperty("injectionPolicy", "")) {
            case "StateOp"           : return new StateOpPolicy(injectionType);
            case "RoundRobinStateOp" : return new RoundRobinStateOpPolicy(injectionType);
            case "Random"            : return new RandomPolicy(injectionType);
            case "NewState"          : return new NewStatePolicy(injectionType);
            case "NewStateOp"        : return new NewStateOpPolicy(injectionType);
            case "Exhaustive"        : return new ExhaustivePolicy(injectionType);
            case "Fate"              : return new FatePolicy(injectionType);
            case "MetaInfo"          : return new MetaInfoPolicy(injectionType);
            case "FocusedRoundRobinStateOp" :
                return new FocusedStateOpPolicy(
                        properties.getProperty("targetThread"), injectionType);
            case "None"              :
            default                  : return new Policy(injectionType);
        }
    }
}
