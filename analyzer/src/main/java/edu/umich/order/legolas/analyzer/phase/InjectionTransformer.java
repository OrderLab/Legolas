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

import edu.umich.order.legolas.analyzer.algorithm.ExceptionExtractor;
import edu.umich.order.legolas.analyzer.algorithm.InvalidInjectionFilter;
import edu.umich.order.legolas.analyzer.hook.InjectionHookInstrumentor;
import edu.umich.order.legolas.analyzer.hook.InjectionPoint;
import edu.umich.order.legolas.analyzer.hook.InjectionSpec;
import edu.umich.order.legolas.analyzer.option.AnalyzerOptions;
import edu.umich.order.legolas.analyzer.util.FileUtils;
import edu.umich.order.legolas.common.fault.ExceptionTable;
import edu.umich.order.legolas.common.fault.ExceptionTableParser;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

/**
 *
 */
public class InjectionTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(InjectionTransformer.class);

    private final AnalyzerOptions analyzerOptions;

    private final ExceptionTable exceptionTable;
    private final ExceptionExtractor exceptionExtractor;
    private final InvalidInjectionFilter invalidInjectionFilter;
    private final List<InjectionSpec> injectionSpecList;

    public InjectionTransformer() {
        analyzerOptions = AnalyzerOptions.getInstance();
        String[] package_prefix_list = analyzerOptions.system_package_prefix_list;
        Set<String> exclude_packages = analyzerOptions.exclude_packages;
        Set<String> classSet = FileUtils.getClassSetByFile(analyzerOptions.class_set_file);

        exceptionTable = new ExceptionTable(ExceptionTableParser.parse(
                analyzerOptions.exception_table_file_path));
        exceptionExtractor = new ExceptionExtractor(package_prefix_list,
                exceptionTable, false);
        if (analyzerOptions.invalid_injection_analysis)
            invalidInjectionFilter = new InvalidInjectionFilter(package_prefix_list);
        else
            invalidInjectionFilter = null;

        if (analyzerOptions.injection_specs_file != null) {
            injectionSpecList = InjectionSpec.parse(AnalyzerOptions.getInstance().injection_specs_file);
        } else if (analyzerOptions.injection_specs != null) {
            injectionSpecList = InjectionSpec.parse(AnalyzerOptions.getInstance().injection_specs);
        } else {
            injectionSpecList = new ArrayList<>();
        }
        LOG.info(injectionSpecList.size() + " injection spec(s)");
        for (InjectionSpec spec : injectionSpecList) {
            LOG.info("Injection spec: " + spec);
        }
    }

    public void transform() {
        instrumentFaultInjection(exceptionExtractor, invalidInjectionFilter,
                injectionSpecList, exceptionTable);
    }

    private void instrumentFaultInjection(ExceptionExtractor exceptionExtractor, InvalidInjectionFilter
            invalidInjectionFilter, List<InjectionSpec> injectionSpecList, ExceptionTable exceptionTable) {
        String[] package_prefix_list = AnalyzerOptions.getInstance().system_package_prefix_list;
        List<InjectionHookInstrumentor> instrumentors = new LinkedList<>();
        int instrumented_methods = 0;
        int total_injection_points = 0;
        int total_filtered_points = 0;

        // call extractor to extract exceptions first
        exceptionExtractor.extract(Scene.v().getApplicationClasses());
        // if the invalid injection filter is not null, analyze the classes
        if (invalidInjectionFilter != null) {
            invalidInjectionFilter.analyze(Scene.v().getApplicationClasses());
        }

        for (final SootClass sootClass : Scene.v().getApplicationClasses()) {
            String clzz = sootClass.getName();
            if (Arrays.stream(package_prefix_list).anyMatch(clzz::startsWith)) {
                boolean match_scope = injectionSpecList.isEmpty();
                for (InjectionSpec spec : injectionSpecList) {
                    if (spec.scopes.isEmpty() || clzz.equals(spec.scopes) || clzz.matches(spec.scopes)) {
                        match_scope = true;
                        break;
                    }
                }
                // if the scope does not match, skip instrumenting this class.
                if (!match_scope)
                    continue;
                for (final SootMethod sootMethod : sootClass.getMethods()) {
                    if (sootMethod.hasActiveBody()) {
                        InjectionHookInstrumentor instrumentor =
                                new InjectionHookInstrumentor(sootMethod, exceptionExtractor,
                                        injectionSpecList, exceptionTable, true);
                        instrumentors.add(instrumentor);
                    }
                }
            }
        }
        for (final InjectionHookInstrumentor instrumentor : instrumentors) {
            // do the actual instrumentation
            List<InjectionPoint> injections = instrumentor.instrument(invalidInjectionFilter);
            if (!injections.isEmpty()) {
                instrumented_methods++;
                total_injection_points += injections.size();
            }
            total_filtered_points += instrumentor.invalidInjectionCount();
        }
        LOG.info("Instrumented {} methods, {} points (filtered {} points)",
                instrumented_methods, total_injection_points, total_filtered_points);
        if (AnalyzerOptions.getInstance().dump_analysis_result) {
            dumpExceptions(exceptionExtractor);
            dumpInjectionPoints(instrumentors);
        }
    }

    protected void dumpExceptions(ExceptionExtractor exceptionExtractor) {
        File exceptionCsv = new File(AnalyzerOptions.getInstance().data_dir,
                "extracted_exceptions.csv");
        try (PrintWriter csvWriter = new PrintWriter(exceptionCsv)) {
            exceptionExtractor.dumpCSV(csvWriter, true);
        } catch (IOException e) {
            LOG.error("Failed to write extracted exception file", e);
        }
    }

    protected void dumpInjectionPoints(List<InjectionHookInstrumentor> instrumentors) {
        try {
            PrintWriter textWriter = new PrintWriter(new File(AnalyzerOptions.getInstance().data_dir,
                    "injection_points.txt"));
            PrintWriter csvWriter = new PrintWriter(new File(AnalyzerOptions.getInstance().data_dir,
                    "injection_points.csv"));
            PrintWriter invalidWriter = new PrintWriter(new File(AnalyzerOptions.getInstance().data_dir,
                    "invalid_injection_points.csv"));
            int maxInjections = 0;
            SootMethod maxInjectionMethod = null;
            boolean header_printed1 = false;
            boolean header_printed2 = false;
            for (final InjectionHookInstrumentor instrumentor : instrumentors) {
                if (instrumentor.injectionCount() > maxInjections) {
                    maxInjections = instrumentor.injectionCount();
                    maxInjectionMethod = instrumentor.getTargetMethod();
                }
                if (instrumentor.injectionCount() > 0) {
                    instrumentor.dumpInjectionsPlain(instrumentor.getInjectionPoints(), textWriter);
                    instrumentor.dumpInjectionsCSV(instrumentor.getInjectionPoints(),
                            csvWriter, !header_printed1);
                    header_printed1 = true;
                }
                if (instrumentor.invalidInjectionCount() > 0) {
                    instrumentor.dumpInjectionsCSV(instrumentor.getInvalidInjectionPoints(),
                            invalidWriter, !header_printed2);
                    header_printed2 = true;
                }
            }
            if (maxInjectionMethod != null)
                textWriter.write(maxInjections + " max injection points in " +
                        maxInjectionMethod.getSignature() + "\n");
            textWriter.close();
            csvWriter.close();
            invalidWriter.close();
        } catch (IOException e) {
            LOG.error("Failed to write injection points file", e);
        }
    }
}
