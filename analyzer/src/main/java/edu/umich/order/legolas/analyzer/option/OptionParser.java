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

import edu.umich.order.legolas.analyzer.AnalyzerMain;
import edu.umich.order.legolas.analyzer.phase.PhaseInfo;
import edu.umich.order.legolas.analyzer.phase.PhaseManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A parser for the command line arguments.
 */
public class OptionParser {

    private static final Logger LOG = LoggerFactory.getLogger(OptionParser.class);

    private Option analysisList = Option.builder("a").longOpt("analysis").hasArgs()
            .argName("name name ...")
            .desc("List of analysis names to run on the subject software").build();

    private Option phaseOptions = Option.builder("p").longOpt("phase_option").hasArgs()
            .argName("phase key:val,key:val, ...").desc("List of phase option to be passed to Soot")
            .build();

    private Option injectionSpecs = Option.builder().longOpt("injection").hasArgs()
            .argName("spec spec ...").desc("List of injection specifications")
            .build();

    private Option injectionSpecsFile = Option.builder().longOpt("injection_file").hasArgs()
            .argName("file").desc("A file that contains the injection specifications")
            .build();

    private Option inputDirectoryList = Option.builder("i").longOpt("indir").hasArgs()
            .argName("directory directory ...")
            .desc("List of input directories that contain the class files of a subject software")
            .build();

    private Option inputJarList = Option.builder("j").longOpt("jar").hasArgs()
            .argName("file file ...")
            .desc("List of input jar files of the subject software to be analyzed").build();

    private Option classPathList = Option.builder("x").longOpt("extra_classpath").hasArgs()
            .argName("classpath classpath ...")
            .desc("List of additional classpaths (directory or jar)").build();

    private Option classList = Option.builder("c").longOpt("class").hasArgs()
            .argName("class class ...")
            .desc("List of classes to be analyzed").build();

    private Option classSetFile = Option.builder().longOpt("class_set").hasArgs()
            .argName("file")
            .desc("A file that contains the set of the class names to be analyzed").build();

    private Option exceptionTableFile = Option.builder().longOpt("exception_table_file").hasArgs()
            .argName("file")
            .desc("A file that contains the set of the class names to be analyzed").build();

    private Option systemPackagePrefixList = Option.builder("pf").longOpt("prefix").hasArgs()
            .argName("prefix prefix ...")
            .desc("prefix of the package name for the target system").build();

    private Option excludePackages = Option.builder().longOpt("excludes").hasArgs()
            .argName("package package ...")
            .desc("List of package name that should be excluded from analysis").build();

    private Option mainClass = Option.builder("m").longOpt("main").hasArg()
            .desc("Main class of package to be analyzed").build();

    private Option secondaryMainClassList = Option.builder("sm").longOpt("secmain").hasArgs()
            .argName("class class ...")
            .desc("List of secondary main classes").build();

    private Option outputDirectory = Option.builder("o").longOpt("outdir").hasArg()
            .argName("directory")
            .desc("Directory to output the instrumented programs").build();

    private Option dataDirectory = Option.builder().longOpt("data_dir").hasArg()
            .argName("directory")
            .desc("Directory to the data result generated from the analyzer").build();

    private Option invalidInjectionAnalysis = Option.builder().longOpt("invalid_filter")
            .desc("Enable invalid injection analysis and filter").build();

    private Option dumpAnalysis = Option.builder("d").longOpt("dump")
            .desc("Dump analysis result and statistics").build();

    private Option genExecutable = Option.builder("e").longOpt("executable")
            .desc("Generate executable class files instead of Soot IRs").build();

    private Option noOutput = Option.builder("n").longOpt("no_output")
            .desc("Do not generate output for parsing").build();

    private Option wholeProgram = Option.builder("w").longOpt("whole")
            .desc("Whole program analysis").build();

    private Option noDebugInfo = Option.builder().longOpt("no_debug")
            .desc("Do not keep debug information in analysis").build();

    private Option listAnalysis = Option.builder().longOpt("list")
            .desc("List analysis available").build();

    private Option overrideProperties = Option.builder().longOpt("config").hasArgs()
            .argName("key:value key:value ...")
            .desc("List of key value configs, which will override the settings in the config file").build();

    private Option help = Option.builder("h").longOpt("help").desc("Print this help message")
            .build();

    private Option helpWithSootHelp = Option.builder("H").longOpt("Help")
            .desc("Print this help message along " + "with Soot help message").build();

    private Options mOptions;

    public OptionParser() {
        mOptions = new Options();

        // accept a list of analysis names
        mOptions.addOption(analysisList);
        mOptions.addOption(phaseOptions);
        mOptions.addOption(injectionSpecs);
        mOptions.addOption(injectionSpecsFile);
        mOptions.addOption(inputDirectoryList);
        mOptions.addOption(inputJarList);
        mOptions.addOption(classList);
        mOptions.addOption(classSetFile);
        mOptions.addOption(exceptionTableFile);
        mOptions.addOption(systemPackagePrefixList);
        mOptions.addOption(excludePackages);
        mOptions.addOption(secondaryMainClassList);
        mOptions.addOption(mainClass);
        mOptions.addOption(outputDirectory);
        mOptions.addOption(dataDirectory);
        mOptions.addOption(classPathList);
        mOptions.addOption(noOutput);
        mOptions.addOption(genExecutable);
        mOptions.addOption(dumpAnalysis);
        mOptions.addOption(invalidInjectionAnalysis);
        mOptions.addOption(wholeProgram);
        mOptions.addOption(noDebugInfo);
        mOptions.addOption(listAnalysis);
        mOptions.addOption(overrideProperties);
        mOptions.addOption(help);
        mOptions.addOption(helpWithSootHelp);
    }

    public AnalyzerOptions parse(String[] args) throws OptionError {
        try {
            CommandLine cmd = new DefaultParser().parse(mOptions, args);
            AnalyzerOptions options = AnalyzerOptions.getInstance();

            /* Parsing input and output options */
            String[] jars = cmd.getOptionValues(inputJarList.getOpt());
            String[] indirs = cmd.getOptionValues(inputDirectoryList.getOpt());
            List<String> inputList = new ArrayList<String>();
            boolean outputJar = false;
            if (jars != null && jars.length > 0) {
                if (indirs != null && indirs.length > 0) {
                    throw new OptionError("Can only accept either a jar input or a directory input.");
                }
                for (String jar : jars) {
                    if (!new File(jar).isFile()) {
                        throw new OptionError(jar + " does not exist or is not a file");
                    }
                    inputList.add(jar);
                }
                // when the input contains a jar, also output a jar
                outputJar = true;
            } else {
                if (indirs != null && indirs.length > 0) {
                    for (String indir : indirs) {
                        if (!new File(indir).isDirectory()) {
                            throw new OptionError(indir + " does not exist or is not a directory");
                        }
                        inputList.add(indir);
                    }
                }
            }
            options.setInputList(inputList);
            String[] classes = cmd.getOptionValues(classList.getLongOpt());
            if (classes != null) {
                if (!inputList.isEmpty()) {
                    throw new OptionError("When classes are specified, must pass input jar or dir as argument to -x");
                }
                options.setClasses(classes);
            }
            if (cmd.hasOption(classSetFile.getLongOpt())) {
                File class_file = new File(cmd.getOptionValue(classSetFile.getLongOpt()));
                if (!class_file.exists()) {
                    throw new OptionError("Class set file " + class_file.getName() + " does not exist");
                }
                options.setClassSetFile(class_file);
            }
            options.setExceptionTableFile(cmd.getOptionValue(exceptionTableFile.getLongOpt()));
            options.setSystemPackagePrefix(cmd.getOptionValues(systemPackagePrefixList.getLongOpt()));
            options.setExcludePackages(cmd.getOptionValues(excludePackages.getLongOpt()));
            options.setMainClass(cmd.getOptionValue(mainClass.getLongOpt()));
            String[] sec_main_classes = cmd.getOptionValues(secondaryMainClassList.getLongOpt());
            List<String> secMainClassList = new ArrayList<String>();
            if (sec_main_classes != null) {
                for (String str : sec_main_classes) {
                    secMainClassList.add(str);
                }
            }
            options.setSecondaryMainClassList(secMainClassList);

            boolean no_output = cmd.hasOption(noOutput.getLongOpt());
            boolean gen_executable = cmd.hasOption(genExecutable.getLongOpt());
            String outdir = cmd.getOptionValue(outputDirectory.getLongOpt());
            if (no_output) {
                if (gen_executable) {
                    throw new OptionError("Specified both no output and gen executable");
                }
            }
            options.setOutputJar(outputJar);
            options.setOutputDir(outdir);
            options.setGenExecutable(gen_executable);
            options.setNoOutput(no_output);
            options.setDumpAnalysisResult(cmd.hasOption(dumpAnalysis.getLongOpt()));
            options.setInvalidInjectionAnalysis(cmd.hasOption(invalidInjectionAnalysis.getLongOpt()));

            String datadir = cmd.getOptionValue(dataDirectory.getLongOpt());
            if (datadir == null) {
                datadir = "data"; // default data directory
            }
            options.data_dir = new File(datadir);
            if (!options.data_dir.exists()) {
                if (!options.data_dir.mkdirs()) {
                    throw new OptionError("Failed to create data directory " + datadir);
                }
            }

            /* Parsing and setting up class path */
            String java_home = System.getProperty("java.home");
            if (java_home == null || java_home.isEmpty()) {
                throw new OptionError("JAVA_HOME environment variable is not set. Make sure you set it.");
            }
            Path rt = Paths.get(java_home, "lib", "rt.jar");
            Path jce = Paths.get(java_home, "lib", "jce.jar");
            if (!Files.exists(rt) || !Files.exists(jce)) {
                throw new OptionError("rt.jar or jce.jar not found within " + java_home);
            }
            String[] extrac_cps = cmd.getOptionValues(classPathList.getLongOpt());
            String cp = "";
            if (extrac_cps != null) {
                cp = String.join(":", extrac_cps);
            }
            String path = AnalyzerMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            LOG.debug("Append " + path + " to Soot class path");
            cp = path + ":" + cp;
            if (!inputList.isEmpty()) {
                options.setClassPath(
                        String.format("%s:%s:%s:%s", cp, String.join(":", inputList),
                                rt.toString(), jce.toString()));
            } else {
                options.setClassPath(String.format("%s:%s:%s", cp, rt.toString(), jce.toString()));
            }

            /* Setup analyses */
            String[] analyses = cmd.getOptionValues(analysisList.getLongOpt());
            boolean whole = cmd.hasOption(wholeProgram.getLongOpt());
            if (analyses != null) {
                for (String analysis : analyses) {
                    PhaseInfo phase_info = PhaseManager.getInstance().getPhaseInfo(analysis);
                    if (phase_info == null) {
                        throw new OptionError(analysis + " is not a recognized analysis");
                    }
                    if (phase_info.isWholeProgram()) {
                        whole = true; // is a whole program analysis
                    }
                }
            }
            options.setIsWholeProgram(whole);
            options.setAnalyses(analyses);
            String[] phase_option_strs = cmd.getOptionValues(phaseOptions.getLongOpt());
            Map<String, List<String>> all_phase_options = new HashMap<>();
            if (phase_option_strs != null) {
                String phase_name = null;
                boolean last_is_option = false;
                for (String option_str : phase_option_strs) {
                    // Each phase option should be either a phase name or
                    // key:value,key:value,key:value,...
                    // And they must be in this order
                    String[] components = option_str.split(",");
                    for (String component : components) {
                        String[] parts = component.split(":");
                        if (parts.length == 1) {
                            if (phase_name != null && !phase_name.isEmpty() && !last_is_option) {
                                throw new OptionError("Missing phase option for " + phase_name);
                            }
                            phase_name = component;
                            last_is_option = false;
                        } else if (parts.length == 2) {
                            if (phase_name == null || phase_name.isEmpty()) {
                                throw new OptionError(
                                        "Missing phase name for phase option " + component);
                            }
                            List<String> option_list = all_phase_options.get(phase_name);
                            if (option_list == null) {
                                option_list = new ArrayList<>();
                                all_phase_options.put(phase_name, option_list);
                            }
                            option_list.add(component);
                            last_is_option = true;
                        }
                    }
                }
                if (phase_name != null && !phase_name.isEmpty() && !last_is_option) {
                    throw new OptionError("Missing phase option for " + phase_name);
                }
            }
            options.setPhaseOptions(all_phase_options);

            /* Parse configs that are specified as the command line arguments */
            String[] new_properties = cmd.getOptionValues(overrideProperties.getLongOpt());
            if (new_properties != null) {
                Properties prop = new Properties();
                for (String property : new_properties) {
                    String[] parts = property.split(":");
                    if (parts.length != 2) {
                        throw new OptionError("Invalid property format: " + property);
                    } else {
                        // put the key value pair into properties
                        prop.setProperty(parts[0], parts[1]);
                    }
                }
                options.setOverrideProperties(prop);
            }

            /* Exception and operation spec option */
            options.injection_specs = cmd.getOptionValues(injectionSpecs.getLongOpt());
            if (cmd.hasOption(injectionSpecsFile.getLongOpt())) {
                File injection_file = new File(cmd.getOptionValue(injectionSpecsFile.getLongOpt()));
                if (!injection_file.exists()) {
                    throw new OptionError("Injection specification file " + injection_file.getName()
                            + " does not exist");
                }
                options.injection_specs_file = injection_file;
            }

            /* Setup other option */
            options.setListAnalysis(cmd.hasOption(listAnalysis.getLongOpt()));
            options.setKeepDebug(!cmd.hasOption(noDebugInfo.getLongOpt()));
            options.setIsHelp(cmd.hasOption(help.getLongOpt()));
            options.setIsSootHelp(cmd.hasOption(helpWithSootHelp.getLongOpt())); // soot help

            /* Extract the non-positional arguments */
            options.setArgs(cmd.getArgs());
            return options;
        } catch (ParseException e) {
            throw new OptionError("Failed to parse command line arguments: " + e.getMessage() + "\n");
        }
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(80, "legolas-analyzer.jar [OPTIONS] -- [SOOT OPTIONS]",
                null, mOptions, null, false);
        String example = "\nExample:\n"
                + "\tlegolas-analyzer.jar -a wjtp.modloc -i target/test-classes\n";
        System.out.println(example);
    }

    public void listAnalyses() {
        System.out.println("Available Legolas analyses:");
        Iterator<PhaseInfo> infoIterator = PhaseManager.getInstance().phaseInfoIterator();
        while (infoIterator.hasNext()) {
            PhaseInfo info = infoIterator.next();
            System.out.println("\t" + info.getFullName() + " - " + info.getHelp());
        }
    }

    /**
     * Exception in parsing command line options
     */
    public static class OptionError extends Exception {

        private static final long serialVersionUID = 4614793168350802013L;

        public OptionError(String message) {
            super(message);
        }
    }
}