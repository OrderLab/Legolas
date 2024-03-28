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

import java.util.HashMap;
import java.util.Map;
import soot.SootClass;

/**
 * Analyze the inheritance relations for classes
 */
public class InheritanceDecider {
    private static final Map<SootClass, Integer> memoir = new HashMap<>();

    private static final String[] names = {
            "java.lang.Thread",
            "java.lang.Runnable",
            "org.apache.cassandra.io.IVersionedSerializer",
            "org.apache.cassandra.net.IVerbHandler",
            "org.apache.cassandra.utils.WrappedRunnable",
            "org.apache.cassandra.io.util.DiskAwareRunnable",
            "java.lang.Throwable",
            "java.lang.RuntimeException",
            "java.lang.Error",
            "java.io.IOException",
    };
    private static final int[] flags = {
            1<<0,
            1<<0,
            1<<1,
            1<<2,
            1<<3,
            1<<4,
            1<<5,
            1<<6,
            1<<7,
            1<<8,
    };

    private static int dfsWithMemoir(final SootClass sootClass) {
        if (memoir.containsKey(sootClass))
            return memoir.get(sootClass);
        int result = 0;
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(sootClass.getName()))
                result |= flags[i];
        }
        for (final SootClass c : sootClass.getInterfaces()) {
            result |= dfsWithMemoir(c);
        }
        try {
            // superclass may be null and throwing RuntimeException
            result |= dfsWithMemoir(sootClass.getSuperclass());
        } catch (final RuntimeException ignored) {}
        memoir.put(sootClass, result);
        return result;
    }

    public static boolean isThreadOrRunnable(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & (flags[0] | flags[1])) != 0;
    }

    public static boolean isSerializer(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[2]) != 0;
    }

    public static boolean isVerbHandler(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[3]) != 0;
    }

    public static boolean isWrappedRunnable(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[4]) != 0;
    }

    public static boolean isDiskAwareRunnable(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[5]) != 0;
    }

    public static boolean isThrowable(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[6]) != 0;
    }

    public static boolean isRuntimeException(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[7]) != 0;
    }

    public static boolean isError(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[8]) != 0;
    }

    public static boolean isIOException(final SootClass sootClass) {
        return (dfsWithMemoir(sootClass) & flags[9]) != 0;
    }

    public static boolean isInterruptException(final SootClass sootClass) {
        return sootClass.getName().equals("java.lang.InterruptedException");
    }

    public static boolean isSubtype(final SootClass exception, final SootClass baseException) {
        if (exception == baseException)
            return true;
        if (!InheritanceDecider.isThrowable(exception))
            return false;
        for (final SootClass i : exception.getInterfaces()) {
            if (isSubtype(i, baseException))
                return true;
        }
        try {
            if (isSubtype(exception.getSuperclass(), baseException))
                return true;
        } catch (final RuntimeException ignored) {}
        return false;
    }
}
