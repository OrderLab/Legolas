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
package edu.umich.order.legolas.analyzer;

import edu.umich.order.legolas.analyzer.option.AnalyzerOptions;
import edu.umich.order.legolas.analyzer.option.OptionParser;
import edu.umich.order.legolas.analyzer.option.OptionParser.OptionError;
import edu.umich.order.legolas.analyzer.phase.AbstractStateTransformer;
import edu.umich.order.legolas.analyzer.phase.MetaInfoTransformer;
import edu.umich.order.legolas.analyzer.phase.PhaseInfo;
import edu.umich.order.legolas.analyzer.phase.PhaseManager;
import edu.umich.order.legolas.common.agent.LegolasAgent;
import edu.umich.order.legolas.common.asm.AbstractStateMachine;
import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.G;
import soot.PackManager;
import soot.PhaseOptions;
import soot.Scene;
import soot.SootClass;
import soot.Timers;
import soot.options.Options;

/**
 * Entry of the Legolas analyzer
 */
public class AnalyzerMain {
    private static final Logger LOG = LoggerFactory.getLogger(AnalyzerMain.class);
    
    // Arguments passed through the command line
    private final AnalyzerOptions options;
    private boolean initialized;

    public AnalyzerMain(AnalyzerOptions options) {
        this.options = options;
        initialized = false;
    }

    /**
     * Invoke Soot with our customized options and additional arguments.
     */
    protected boolean run() {
        if (!initialized) {
            System.err.println("Legolas analyzer is not initialized");
            return false;
        }
        Options.v().warnNonexistentPhase();
        if (Options.v().phase_list()) {
            System.out.println(Options.v().getPhaseList());
            return true;
        }
        if (!Options.v().phase_help().isEmpty()) {
            for (String phase : Options.v().phase_help()) {
                System.out.println(Options.v().getPhaseHelp(phase));
            }
            return true;
        }
        if (PhaseManager.getInstance().enabledAnalyses().isEmpty()) {
            System.err.println("No analysis is specified.");
            System.err.println("Run with --list to see the list of analyses available");
            return false;
        }
        // Invoke Soot's pack manager to run the packs
        try {
            Date start = new Date();
            LOG.info("Legolas analyzer started on " + start);
            Timers.v().totalTimer.start();
            Scene.v().loadNecessaryClasses();
            PackManager.v().runPacks();
            if (!Options.v().oaat()) {
                PackManager.v().writeOutput();
            }
            Timers.v().totalTimer.end();
            // Print out time stats.
            if (Options.v().time())
                Timers.v().printProfilingInformation();
            Date finish = new Date();
            LOG.info("Legolas analyzer finished on " + finish);
            long runtime = finish.getTime() - start.getTime();
            LOG.info("Legolas analyzer has run for " + (runtime / 60000) + " min. "
                    + ((runtime % 60000) / 1000) + " sec. " + (runtime % 1000) + " ms.");
        } catch (StackOverflowError e ) {
            LOG.error( "Legolas analyzer has run out of stack memory." );
            throw e;
        } catch (OutOfMemoryError e) {
            LOG.error( "Soot has run out of the memory allocated to it by the Java VM." );
            throw e;
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return true;
    }

    /**
     * Register the analyses to run with Soot pack manager
     */
    private void registerAnalyses() {
        PhaseManager.getInstance().registerAnalysis(new AbstractStateTransformer(),
                AbstractStateTransformer.PHASE_INFO);
        PhaseManager.getInstance().registerAnalysis(new MetaInfoTransformer(),
                MetaInfoTransformer.PHASE_INFO);
    }

    /**
     * Load basic classes to Soot
     */
    private void loadClasses() {
        // add input classes
        String[] classes = options.classes;
        if (classes != null) {
            for (String cls : classes) {
                Options.v().classes().add(cls); // all to Soot class to be loaded
            }
        }

        // add basic classes
        Class<?>[] basicClasses = {java.io.PrintStream.class, java.lang.System.class,
                java.lang.Thread.class, AbstractStateMachine.class, AbstractStateMachineManager.class,
                LegolasAgent.class};
        for (Class<?> cls : basicClasses) {
            LOG.debug("Adding basic class " + cls.getCanonicalName() + " to Soot");
            Scene.v().addBasicClass(cls.getCanonicalName(), SootClass.SIGNATURES);
            for (Class<?> innerCls : cls.getDeclaredClasses()) {
                // Must use getName instead of getCanonicalName for inner class
                LOG.debug("- inner class " + innerCls.getName() + " added");
                Scene.v().addBasicClass(innerCls.getName(), SootClass.SIGNATURES);
            }
        }
    }

    /**
     * Reset the analysis
     */
    public void reset() {
        // Don't put the G.reset() at the beginning of the initialize() as in the Unit test
        // we may register some special phases, e.g., TestHelper, before calling initialize().
        // They will get cleared if we do reset()...
        G.reset(); // reset Soot
    }

    /**
     * Prepare environment to run auto-watchdog: set options, register analyses,
     * load classes, initialize Soot, etc.
     *
     * @return true if the initialization is successful; false otherwise
     */
    public boolean initialize() {
        registerAnalyses(); // register analyses with Soot

        /* Setup Soot options */
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_soot_classpath(options.class_path);
        if (options.no_output) {
            Options.v().set_output_format(Options.output_format_none);
        } else {
            if (!options.gen_executable && !options.output_jar) {
                // If the output format is not a jar or .class files,
                // we output Jimple by default
                Options.v().set_output_format(Options.output_format_J);
            }
            Options.v().set_output_jar(options.output_jar);
        }
        // Well, the truth is even if we specify output format as none
        // AutoWatchdog still relies on the output dir option to decide
        // where to write its intermediate results... :|
        if (options.output_dir != null) {
            Options.v().set_output_dir(options.output_dir);
        }
        Options.v().set_keep_line_number(options.keep_debug);
        Options.v().set_main_class(options.main_class);
        if (!options.isInputListEmpty()) {
            Options.v().set_process_dir(options.input_list);
        }
        String[] analyses = options.analyses;
        if (analyses == null || analyses.length == 0) {
            // if no analyses are listed, enable all available registered analyses by default
            analyses = PhaseManager.getInstance().getAnalyses();
        }
        boolean need_call_graph = false;
        boolean is_whole_program = options.is_whole_program;
        for (String analysis : analyses) {
            // Enable the analysis in the manager
            PhaseManager.getInstance().enableAnalysis(analysis);
            PhaseInfo phaseInfo = PhaseManager.getInstance().getPhaseInfo(analysis);
            // if any phase needs call graph, we should enable it
            if (phaseInfo.needCallGraph()) {
                need_call_graph = true;
            }
            if (phaseInfo.isWholeProgram()) {
                // even if the option disable whole program option, we should enable it
                is_whole_program = true;
            }

        }
        Options.v().set_whole_program(is_whole_program);
        if (is_whole_program && !need_call_graph) {
            // if it is whole program analysis and we don't need call graph analysis
            // we should explicitly disable it
            PhaseOptions.v().setPhaseOption("cg", "off");
        }

        // We enable spark to get on-the-fly callgraph
        Options.v().setPhaseOption("cg.spark","enabled:true");
        // We enable context-sensitive points-to analysis to better achieve
        Options.v().setPhaseOption("cg.spark","cs-demand:true");
        Options.v().setPhaseOption("cg.spark","apponly:true");

        Map<String, List<String>> all_phase_options = options.phase_options;
        Set<String> analysis_with_options = new HashSet<>();
        if (all_phase_options != null) {
            for (Map.Entry<String, List<String>> entry : all_phase_options.entrySet()) {
                String phase = entry.getKey();
                String option_str = String.join(",", entry.getValue());
                // If the option from command line is for our custom analysis,
                // we must both enable it and add the custom option str
                if (PhaseManager.getInstance().isAnalysiEnabled(phase)) {
                    analysis_with_options.add(phase);
                    option_str = "enabled:true," + option_str;
                }
                // Otherwise, the option from command line is for a standard Soot phase
                // e.g., -p jb use-original-names:true, we will just pass it along to Soot
                PhaseOptions.v().setPhaseOption(phase, option_str);
            }
        }

        for (String analysis : analyses) {
            // For any specified analysis that does not have an option from command line
            // We must at least enable it in Soot
            if (!analysis_with_options.contains(analysis)) {
                PhaseOptions.v().setPhaseOption(analysis, "enabled:true");
            }
        }

        String[] args = options.args;
        if (args == null) {
            args = new String[]{};
        }
        if (args.length == 0) {
            Options.v().set_unfriendly_mode(true); // allow no arguments to be specified for Soot
        }

        // load classes
        loadClasses();

        if (!Options.v().parse(options.args)) {
            System.err.println("Error in parsing Soot options");
            return false;
        }
        if (Options.v().on_the_fly()) {
            Options.v().set_whole_program(true);
            PhaseOptions.v().setPhaseOption("cg", "off");
        }

        initialized = true;
        LOG.info("Legolas analyzer initialization finished");
        return true;
    }

    public static void main(String[] args) {
        addScalaDependencies();
        addFlinkDependencies();
        OptionParser parser = new OptionParser();
        AnalyzerOptions options = null;
        try {
            options = parser.parse(args);
        } catch (OptionError optionError) {
            System.err.println("Error in parsing options: " + optionError.getMessage() + "\n");
            parser.printHelp();
            System.exit(1);
        }
        if (options.is_soot_help) {
            parser.printHelp();
            System.out.println("\n*********************************************");
            System.out.println("Soot OPTIONS:\n");
            System.out.println(Options.v().getUsage());
            System.exit(0);
        } else if (options.is_help) {
            parser.printHelp();
            System.exit(0);
        }
        if (options.list_analysis) {
            parser.listAnalyses();
            System.exit(0);
        }
        if (options.isInputListEmpty() && options.classes == null) {
            System.err.println("Must set either a jar file/input directory or a list of classes as input.");
            parser.printHelp();
            System.exit(1);
        }
        LOG.debug("Parsed options: " + options);
        // Create AutoWatchdog now with the parsed options
        AnalyzerMain main = new AnalyzerMain(options);
        if (!main.initialize() || !main.run()) {
            System.exit(1);
        }
    }

    // in alphabetical order
    private static final String[] scalaDependencies = new String[]{
            "scala.runtime.java8.JFunction0$mcB$sp",
            "scala.runtime.java8.JFunction0$mcD$sp",
            "scala.runtime.java8.JFunction0$mcI$sp",
            "scala.runtime.java8.JFunction0$mcJ$sp",
            "scala.runtime.java8.JFunction0$mcV$sp",
            "scala.runtime.java8.JFunction0$mcZ$sp",
            "scala.runtime.java8.JFunction1$mcID$sp",
            "scala.runtime.java8.JFunction1$mcJI$sp",
            "scala.runtime.java8.JFunction1$mcJJ$sp",
            "scala.runtime.java8.JFunction1$mcVD$sp",
            "scala.runtime.java8.JFunction1$mcVI$sp",
            "scala.runtime.java8.JFunction1$mcVJ$sp",
            "scala.runtime.java8.JFunction1$mcZI$sp",
            "scala.runtime.java8.JFunction1$mcZJ$sp",
            "scala.runtime.java8.JFunction2$mcIII$sp",
            "scala.runtime.java8.JFunction2$mcZII$sp",
    };

    private static void addScalaDependencies() {
        for (final String d : scalaDependencies) {
            Scene.v().addBasicClass(d, SootClass.HIERARCHY);
        }
    }

    private static void addFlinkDependencies() {
        Scene.v().addBasicClass("org.junit.jupiter.api.extension.AfterTestExecutionCallback", SootClass.HIERARCHY);
    }
}
