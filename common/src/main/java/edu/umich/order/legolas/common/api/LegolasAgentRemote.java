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

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The RPC interface to interact with the Legolas agent inside a target system
 */
public interface LegolasAgentRemote extends Remote {
    String REMOTE_NAME = "LegolasAgent";
    int REMOTE_PORT = 1099;

    /**
     * Enable the abstract state tracking for a given class and instance
     * TODO: usage
     *
     * @param className
     * @param instanceId
     * @return
     * @throws RemoteException
     */
    boolean enableASM(final String className, final int instanceId) throws RemoteException;


    /**
     * Disable the abstract state tracking for a given class and instance
     * TODO: usage
     *
     * @param className
     * @param instanceId
     * @return
     * @throws RemoteException
     */
    boolean disableASM(final String className, final int instanceId) throws RemoteException;
}