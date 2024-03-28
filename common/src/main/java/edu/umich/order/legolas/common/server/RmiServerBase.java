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
package edu.umich.order.legolas.common.server;

import edu.umich.order.legolas.common.api.RegistryFactory;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RMI server base
 */
public abstract class RmiServerBase implements Remote {
    private static final Logger LOG = LoggerFactory.getLogger(RmiServerBase.class);

    public final String rmiName;
    public final int rmiPort;
    public final String descName;

    private final Registry rmiRegistry;
    private volatile boolean started;

    public RmiServerBase(String name, int port, String desc, final Registry registry, boolean tryCreateReg)
            throws RemoteException {
        rmiName = name;
        rmiPort = port;
        descName = desc;
        if (registry == null) {
            // if the caller does not supply a registry, we are going to create one if requested
            rmiRegistry = RegistryFactory.getRegistry(port, tryCreateReg);
        } else {
            rmiRegistry = registry;
        }
        started = false;
    }

    public void start() throws Exception {
        Remote stub = UnicastRemoteObject.exportObject(this, 0);
        rmiRegistry.rebind(rmiName, stub);
        LOG.info(descName + " started");
        started = true;
    }

    public synchronized void shutdown() {
        if (started) {
            try {
                rmiRegistry.unbind(rmiName);
                UnicastRemoteObject.unexportObject(this, true);
            } catch (final Exception e) {
                LOG.error("Failed to unbind the " + descName, e);
            }
            LOG.info(descName + " is shut down");
            started = false;
        }
    }
}
