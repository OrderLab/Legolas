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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lookup table for fault injection, including both exception and delay fault.
 */
public final class ExceptionTable {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionTable.class);

    private final Map<String, Integer> exceptionIdMap;

    private final String[] exceptionNames;

    /*
     * some exceptions may be be imported in this package, but in runtime it will be imported by
     * the target system
     */
    private final Throwable[] exceptionInstances;

    /*
     * TODO: add another constructor getting the exception names from system properties
     */
    public ExceptionTable(final String[] exceptionNames) {
        // Initialize the exception table with the target system's class loader, which is
        // obtained from the current thread's context class loader. The classloader is
        // necessary to resolve system-specific exceptions such as
        // org.apache.zookeeper.server.RequestProcessor$RequestProcessorException
        // it should happen after register() because it's a little bit slow
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        this.exceptionNames = exceptionNames;
        this.exceptionInstances = new Throwable[exceptionNames.length];
        for (int i = 0; i < exceptionNames.length; i++) {
            exceptionInstances[i] = newException(exceptionNames[i], loader);
        }
        exceptionIdMap = new HashMap<>();
        for (int i = 0; i < exceptionNames.length; i++) {
            exceptionIdMap.put(exceptionNames[i], i);
        }
        LOG.info("Exception instance table initialized");
    }

    /**
     * Get the ID in the exception table for a given exception string.
     *
     * @param s exception name string
     * @return id if the string exists in the table and there is an exception instance
     */
    public int getExceptionId(String s) {
        Integer eid = exceptionIdMap.get(s);
        if (eid != null) {
            return eid;
        }
        return -1;
    }

    public Throwable getException(int eid) {
        if (eid >= 0 && eid < exceptionInstances.length)
            return exceptionInstances[eid];
        return null;
    }

    public String getFaultName(int fid) {
        if (fid >= 0 && fid < exceptionNames.length)
            return exceptionNames[fid];
        return null;
    }

    /**
     * Construct an exception instance for a given exception name.
     * TODO: make more robust and enable more types of exceptions
     * @param exceptionName
     * @param loader
     * @return
     */
    private static Throwable newException(String exceptionName, ClassLoader loader) {
        try {
            final Class<?> exceptionClass = Class.forName(exceptionName, true, loader);
            final Constructor<?>[] cons = exceptionClass.getDeclaredConstructors();
            LOG.debug("Constructor for exception " + exceptionName);
            for (int j = 0; j < cons.length; j++) {
                LOG.debug("= " + cons[j]);
                cons[j].setAccessible(true);
            }
            /* Try new the instance in the order of Throwable(String msg), Throwable(String msg,
               Throwable cause), Throwable(Throwable cause) */
            Constructor<?> ctor;
            try {
                ctor = exceptionClass.getConstructor(String.class);
                return (Throwable) ctor.newInstance("Legolas injected exception");
            } catch (Exception ignored) { }
            try {
                ctor = exceptionClass.getConstructor(String.class, Throwable.class);
                return (Throwable) ctor.newInstance("Legolas injected exception",
                        new Exception("From Legolas"));
            } catch (Exception ignored) { }
            try {
                ctor = exceptionClass.getConstructor(Throwable.class);
                return (Throwable) ctor.newInstance(new Exception("Injected exception from Legolas"));
            } catch (Exception ignored) { }
            try {
                ctor = exceptionClass.getConstructor();
                return (Throwable) ctor.newInstance();
            } catch (Exception ignored) { }
            for (final Constructor<?> con : cons) {
                ctor = con;
                /* create the list of arguments for the constructor */
                final List<Object> params = new ArrayList<>();
                try {
                    for (Class<?> pType : ctor.getParameterTypes()) {
                        // if the argument is a primitive type (i.e., int/double/short/byte/long/bool,
                        // etc., we initialize using 0, else we initialize using null.
                        params.add(pType.isPrimitive() ? 0 : null);
                    }
                    return (Throwable) ctor.newInstance(params.toArray());
                } catch (Exception ignored) {
                    LOG.warn("Failed to create instance for exception " + exceptionName);
                }
            }
            LOG.warn("Cannot find suitable constructor for exception " + exceptionName);
            return null;
        } catch (final ClassNotFoundException e) {
            LOG.warn("Cannot find class name " + exceptionName);
            return null;
        }
    }
}
