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
package edu.umich.order.legolas.analyzer.phase;

/**
 * Information about a custom phase
 */
public class PhaseInfo {
    private String pack;
    private String name;
    private String full_name;
    private String help;
    private boolean whole_program;
    private boolean need_call_graph;

    public PhaseInfo(String pack, String name, String help, boolean whole_program, boolean need_call_graph) {
        this.pack = pack;
        this.name = name;
        this.full_name = pack + "." + name;
        this.help = help;
        // enable whole program option is the pack is w*
        this.whole_program = whole_program || pack.equals("wjtp") || pack.equals("wjop") || pack.equals("wjap");
        // call graph option is only applicable for whole program analysis phase
        this.need_call_graph = this.whole_program && need_call_graph;
    }

    public String getPack() {
        return pack;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return full_name;
    }

    public String getHelp() {
        return help;
    }

    public boolean isWholeProgram() {
        return whole_program;
    }

    public boolean needCallGraph() {
        return need_call_graph;
    }
}