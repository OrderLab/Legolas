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
package edu.umich.order.legolas.analyzer.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: to be tested
 */
public final class FileUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

    public static Set<String> getClassSetByFile(final File classSetFile) {
        if (classSetFile == null) {
           return null;
        }
        Set<String> classSet = new HashSet<>();
        try {
            final Scanner scanner = new Scanner(classSetFile);
            while (scanner.hasNext()) {
                final String s = scanner.nextLine().trim().replace("\n\r ", "");
                if (s.isEmpty() || s.startsWith("#")) continue;
                classSet.add(s);
            }
        } catch (FileNotFoundException e) {
            LOG.error("Class set file not found " + e);
            return null;
        }
        return classSet;
    }
}
