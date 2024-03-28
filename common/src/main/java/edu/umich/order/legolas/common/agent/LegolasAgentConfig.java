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
package edu.umich.order.legolas.common.agent;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class LegolasAgentConfig {
    private static final Logger LOG = LoggerFactory.getLogger(LegolasAgentConfig.class);

    /**
     * We support two types of Legolas agent: stateful and stateless.
     *
     * In a stateful agent, the agent will maintain some global data structures to keep track of the information
     * happening inside the target system. These global data structures are encapsulated in the
     * {@link LegolasAgentSubstrate} class. A stateful agent will try to use the local information
     * to make decision if possible and only lazily contact the servers for global decisions when
     * needed. The benefit is efficiency and the disadvantage is complexity.
     *
     * In a stateless agent, the agent will not store any states itself. Whenever it gets some updates
     * from the target system, it will pass the information to some external server, e.g., the
     * orchestrator. This was essentially what our initial version of the ASM manager did.
     */
    public enum LegolasAgentType {
        STATEFUL,
        STATELESS
    }

    // TODO: add feature
//    public final int socketAgentPort = AsmManagerSocketAgent.DEFAULT_PORT;
//    private static boolean socketAgentEnabled = false;
//    private static AsmManagerSocketAgent socketAgent = null;

    // in pure local mode, the agent does not interact with the orchestrator at all, useful for testing
    public final boolean localMode;

    // by default we use stateless agent (for now) for backward "compatibility"
    public final LegolasAgentType agentType;

    public final String exceptionTablePath;

    // TODO: specify port for each service
    public final int rmiPort;

    // config keys
    private static final String KEY_AGENT_TYPE = "agent_type";
    private static final String KEY_SOCKET_AGENT_ENABLE = "socket_agent";
    private static final String KEY_SOCKET_AGENT_PORT = "socket_agent_port";
    private static final String KEY_PURE_LOCAL_MODE = "pure_local_mode";
    private static final String KEY_EXCEPTION_TABLE_PATH = "exception_table_path";

    /*
     * TODO: read content from configPath
     * @param configPath
     */
    public LegolasAgentConfig(final String configPath) {
        final File configFile = new File(configPath);
        // TODO: refactor
        localMode = false;
        agentType = LegolasAgentType.STATELESS;
        exceptionTablePath = "##"; // to be modified
        rmiPort = 1099;
        if (!configFile.exists()) {
            LOG.warn("No configuration file for the agent found, use default configs");
            return;
        }
//        try {
//            LOG.info("Found agent config file " + configPath);
//            final InputStream is = new FileInputStream(configFile);
//            final Properties properties = new Properties();
//            properties.load(is);
//            String config = properties.getProperty(KEY_AGENT_TYPE, "");
//            if (config.equals("stateless")) {
//                agentType = LegolasAgentType.STATELESS;
//            } else if (config.equals("stateful")) {
//                agentType = LegolasAgentType.STATEFUL;
//            }
//            config = properties.getProperty(KEY_SOCKET_AGENT_PORT, "");
//            try {
//                socketAgentPort = Integer.parseInt(config);
//            } catch (NumberFormatException e) {
//                LOG.error("Invalid agent socket port " + config);
//            }
//            config = properties.getProperty(KEY_SOCKET_AGENT_ENABLE, "");
//            socketAgentEnabled = config.equals("enable");
//            config = properties.getProperty(KEY_PURE_LOCAL_MODE, "");
//            localMode = config.equals("true");
//        } catch (IOException e) {
//            LOG.warn("Failed to parse the config for agent, use default configs");
//        }
//        LOG.info("Successfully parsed and initialized agent configs");
    }
}
