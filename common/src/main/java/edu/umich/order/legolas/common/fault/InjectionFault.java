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

/**
 * TODO: move to analyzer
 */
public class InjectionFault {
    public enum FaultType {
        EXCEPTION,
        DELAY
    }
    public FaultType type;
    public Object exceptionClass;
    public String exceptionName;
    public int exceptionId;
    public InjectionFault(FaultType type, Object clz, String name, int eid) {
        this.type = type;
        exceptionClass = clz;
        this.exceptionName = name;
        this.exceptionId = eid;
    }

    @Override
    public String toString() {
        if (type == FaultType.EXCEPTION)
            return "<" + "eid " + exceptionId + "-" + exceptionName + ">";
        else if (type == FaultType.DELAY)
            return "<delay>";
        return "unknown";
    }
}
