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
 * List of common Java exception classes
 */
public final class BuiltInExceptions {
    public static final String[] exceptionNames = {
            "java.io.EOFException",
            "java.io.FileNotFoundException",
            "java.io.IOError",
            "java.io.IOException",
            "java.io.UTFDataFormatException",
            "java.io.UnsupportedEncodingException",
            "java.lang.ArrayIndexOutOfBoundsException",
            "java.lang.AssertionError",
            "java.lang.ClassCastException",
            "java.lang.ClassNotFoundException",
            "java.lang.CloneNotSupportedException",
            "java.lang.Error",
            "java.lang.Exception",
            "java.lang.ExceptionInInitializerError",
            "java.lang.IllegalAccessException",
            "java.lang.IllegalArgumentException",
            "java.lang.IllegalStateException",
            "java.lang.IndexOutOfBoundsException",
            "java.lang.InstantiationException",
            "java.lang.InterruptedException",
            "java.lang.NoSuchMethodException",
            "java.lang.NullPointerException",
            "java.lang.NumberFormatException",
            "java.lang.OutOfMemoryError",
            "java.lang.RuntimeException",
            "java.lang.SecurityException",
            "java.lang.Throwable",
            "java.lang.UnsupportedOperationException",
            "java.lang.reflect.InvocationTargetException",
            "java.net.ConnectException",
            "java.net.SocketException",
            "java.net.UnknownHostException",
            "java.nio.channels.ClosedChannelException",
            "java.security.NoSuchAlgorithmException",
            "java.security.PrivilegedActionException",
            "java.util.NoSuchElementException",
            "java.util.concurrent.ExecutionException",
            "java.util.concurrent.RejectedExecutionException",
            "java.util.concurrent.TimeoutException",
            "javax.management.MalformedObjectNameException",
            "javax.security.auth.callback.UnsupportedCallbackException",
            "javax.security.auth.login.LoginException",
            "javax.security.sasl.SaslException",
    };
}
