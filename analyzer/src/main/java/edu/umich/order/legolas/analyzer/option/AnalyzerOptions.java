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
package edu.umich.order.legolas.analyzer.option;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Parsed command line arguments for the analyzer (main code snippets are from AutoWatchdog project).
 */
public class AnalyzerOptions {
    public boolean is_help = false;
    public boolean is_soot_help = false;
    public boolean keep_debug = false;
    public boolean is_whole_program = true;
    public boolean gen_executable = false;
    public boolean no_output = false;
    public boolean output_jar = true;
    public boolean list_analysis = true;
    public boolean dump_analysis_result = false;
    public boolean invalid_injection_analysis = false;

    /* Class path needed to resolve the classes */
    public String class_path;
    /* The list of input directories or jars */
    public List<String> input_list;
    /* Main class to start analysis */
    public String main_class;
    /* List of secondary main classes (a distributed system like HDFS can have multiple main classes as entries) */
    public List<String> secondary_main_classes;

    /* List of exception specifications to instrument */
    public String[] injection_specs;
    /* File to the injection specifications */
    public File injection_specs_file;

    /* Data directory */
    public File data_dir;

    /* Output directory */
    public String output_dir;
    /* List of analyses to execute */
    public String[] analyses;
    /* List of class names to be analyzed */
    public String[] classes;
    /* File that specifies the set of class names to be analyzed */
    public File class_set_file;
    /* File that specifies the set of exceptions to be analyzed and injected */
    public String exception_table_file_path;

    /* Prefix of the package name for the target system */
    public String[] system_package_prefix_list;
    /* List of package prefixes that should be excluded from analysis */
    public Set<String> exclude_packages;
    /* List of options passed to one or more Soot phases */
    public Map<String, List<String>> phase_options;
    /* Additional arguments to pass directly to Soot */
    public String[] args;
    /* Settings that override the values from the config file */
    public Properties override_props;

    private static AnalyzerOptions instance = new AnalyzerOptions();
    public static AnalyzerOptions getInstance() {
        return instance;
    }

    private AnalyzerOptions() {
    }

    void setIsHelp(boolean is_help) {
        this.is_help = is_help;
    }

    void setIsSootHelp(boolean is_soot_help) {
        this.is_soot_help = is_soot_help;
    }

    void setKeepDebug(boolean keep_debug) {
        this.keep_debug = keep_debug;
    }

    void setIsWholeProgram(boolean is_whole_program) {
        this.is_whole_program = is_whole_program;
    }

    void setGenExecutable(boolean gen_executable) {
        this.gen_executable = gen_executable;
    }

    void setDumpAnalysisResult(boolean enable) {
        dump_analysis_result = enable;
    }

    void setInvalidInjectionAnalysis(boolean enable) {
        invalid_injection_analysis = enable;
    }

    void setNoOutput(boolean no_output) {
        this.no_output = no_output;
    }

    void setOutputJar(boolean output_jar) {
        this.output_jar = output_jar;
    }

    void setListAnalysis(boolean list_analysis) {
        this.list_analysis = list_analysis;
    }

    void setClassPath(String class_path) {
        this.class_path = class_path;
    }

    void setExceptionTableFile(String exception_table_file) {
        this.exception_table_file_path = exception_table_file;
    }

    void setInputList(List<String> input_list) {
        this.input_list = input_list;
    }

    public boolean isInputListEmpty() {
        return input_list == null || input_list.isEmpty();
    }

    void setMainClass(String main_class) {
        this.main_class = main_class;
    }

    void setSecondaryMainClassList(List<String> secondary_main_classes) {
        this.secondary_main_classes = secondary_main_classes;
    }

    public boolean isSecondaryMainClassListEmpty() {
        return secondary_main_classes == null || secondary_main_classes.isEmpty();
    }

    void setOutputDir(String output_dir) {
        this.output_dir = output_dir;
    }

    void setAnalyses(String[] analyses) {
        this.analyses = analyses;
    }

    public void setClasses(String[] classes) {
        this.classes = classes;
    }

    public void setSystemPackagePrefix(String[] system_package_prefix_list) {
        this.system_package_prefix_list = system_package_prefix_list;
    }

    public void setClassSetFile(File class_set_file) {
        this.class_set_file = class_set_file;
    }

    public void setExcludePackages(String [] excludes) {
        if (excludes != null) {
            this.exclude_packages = new HashSet<>(Arrays.asList(excludes));
        }
    }

    public void setPhaseOptions(Map<String, List<String>> phase_options) {
        this.phase_options = phase_options;
    }

    public void setOverrideProperties(Properties override_props) {
        this.override_props = override_props;
    }

    void setArgs(String[] args) {
        this.args = args;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("- keep_debug? ").append(keep_debug).append("\n");
        sb.append("- whole_program? ").append(is_whole_program).append("\n");
        sb.append("- gen_executable? ").append(gen_executable).append("\n");
        sb.append("- no_output? ").append(no_output).append("\n");
        sb.append("- output_jar? ").append(output_jar).append("\n");
        sb.append("- list_analysis? ").append(list_analysis).append("\n");
        sb.append("- class_path: ").append(class_path).append("\n");
        sb.append("- input_list: ").append(String.join(",", input_list))
                .append("\n");
        sb.append("- main_class: ").append(main_class).append("\n");
        sb.append("- output_dir: ").append(output_dir).append("\n");
        sb.append("- analyses: ").append(String.join(",", analyses)).append("\n");
        sb.append("- classes: ").append(classes == null? "" : String.join(",", classes)).append("\n");
        sb.append("- phase_options: ");
        if (phase_options != null) {
            for (Map.Entry<String, List<String>> entry : phase_options.entrySet()) {
                sb.append(entry.getKey()).append(" ").append(String.join(",",
                        entry.getValue())).append("; ");
            }
            sb.append("\n");
        } else {
            sb.append("null\n");
        }

        sb.append("- ARGS: ").append(String.join(" ", args)).append("\n");
        return sb.toString();
    }
}
