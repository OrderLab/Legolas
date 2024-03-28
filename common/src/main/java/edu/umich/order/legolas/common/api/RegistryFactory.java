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
package edu.umich.order.legolas.common.api;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * A simple factory to handle the RMI registry business for the services we provide.
 */
public final class RegistryFactory {
    public static Registry getRegistry(int port, boolean tryCreate) throws RemoteException {
        if (tryCreate) {
            try {
                // We'll try to create the registry by calling createRegistry. This allows us to skip
                // invoking the rmiregistry bin. Doing so will also setup the classpath properly for
                // the rmiregistry (otherwise, we may encounter ClassNotFound exception)
                return LocateRegistry.createRegistry(port);
            } catch (RemoteException e) {
                // We may have already created the registry before. And the registry will only be destroyed
                // when the JVM exits. In this case, if we call the createRegistry again (e.g., when
                // running multiple server related unit tests, we may get the exception that ObjID is in use.
                // In this case, we should get the registry
                return LocateRegistry.getRegistry(port);
            }
        } else {
            return LocateRegistry.getRegistry(port);
        }
    }
}
