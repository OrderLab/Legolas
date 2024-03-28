/*
 *  @author Haoze Wu <haoze@jhu.edu>
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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class ExceptionTableParser {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionTableParser.class);

    public static String[] parse(final String exceptionTablePath) {
        if (exceptionTablePath == null || exceptionTablePath.isEmpty()) {
            LOG.warn("ExceptionTable path does not exist, use built-in exceptions {}",
                Arrays.toString(BuiltInExceptions.exceptionNames));
            return BuiltInExceptions.exceptionNames;
        }
        LOG.debug("Parsing exceptions from file {}", exceptionTablePath);
        // avoid duplicate exception names, but preserve insertion order
        final LinkedHashSet<String> exceptionNames = new LinkedHashSet<>();
        File exceptionFile = new File(exceptionTablePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(exceptionFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#include")) {
                    String includePath = line.substring(8).trim();
                    // include path is relative to current file
                    File includeFile = new File(exceptionFile.getParent(), includePath);
                    LOG.debug("Parsing exceptions from include file {}", includeFile);
                    if (!includePath.isEmpty() && includeFile.isFile()) {
                        String [] included = parse(includeFile.getPath());
                        exceptionNames.addAll(Arrays.asList(included));
                    } else {
                        LOG.error("Invalid include directive: {}", line);
                    }
                } else if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                } else {
                    exceptionNames.add(line);
                }
            }
        } catch (FileNotFoundException e) {
            LOG.error("Failed to find exception table file " + exceptionTablePath, e);
        } catch (IOException e) {
            LOG.error("I/O error in reading exception table file" + exceptionTablePath, e);
        }
        final String[] arr = new String[exceptionNames.size()];
        exceptionNames.toArray(arr);
        LOG.debug("Parsed {} exceptions from exception table {}: {}",
            arr.length, exceptionTablePath, Arrays.toString(arr));
        return arr;
    }
}
