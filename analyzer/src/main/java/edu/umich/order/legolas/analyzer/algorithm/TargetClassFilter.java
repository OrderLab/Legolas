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
package edu.umich.order.legolas.analyzer.algorithm;

import java.util.Arrays;
import java.util.Set;
import soot.SootClass;

/**
 * Check if a class belongs to a target system
 */
public final class TargetClassFilter {

    private final String[] system_package_prefix_list;
    private final Set<String> exclude_packages;
    private final Set<String> classSet;

    public TargetClassFilter(final String[] system_package_prefix_list,
            final Set<String> exclude_packages, Set<String> classSet) {
        this.system_package_prefix_list = system_package_prefix_list;
        this.exclude_packages = exclude_packages;
        this.classSet = classSet;
    }

    public boolean filter(final SootClass sootClass) {
        String class_name = sootClass.getName();
        // if system package prefix is specified, we should only analyze classes that start
        // with the system package prefix
        if (Arrays.stream(system_package_prefix_list).noneMatch(class_name::startsWith))
            return true;
        if (exclude_packages != null) {
            for (String exclude_package : exclude_packages) {
                // if this class is in the excluded package, e.g., the client code,
                // we should not analyze it
                if (class_name.startsWith(exclude_package))
                    return true;
            }
        }
        if (classSet != null && !classSet.contains(class_name)) {
            // if we specified a class set file, we should skip classes that are not in this set
            // FIXME: should just use --class A B C instead
            return true;
        }
        return false;
    }
}
