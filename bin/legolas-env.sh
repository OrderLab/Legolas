#!/usr/bin/env bash
#
# @author Ryan Huang <ryanph@umich.edu>
#
# The Legolas Project
#
# Copyright (c) 2024, University of Michigan, EECS, OrderLab.
#     All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Setup the environment variables necessary for Legolas to run. It should NOT be
# run directly but instead should be sourced by the main Legolas script.

cur_dir=$(dirname "${BASH_SOURCE-$0}")
cur_dir="$(cd "${cur_dir}"; pwd)"

# We prefer java from the JAVA_HOME
if [[ -z "$JAVA_HOME" ]]; then
  if [[ -z "$(which java)" ]]; then
    echo "java command not found" >&2
    exit 1
  fi
  JAVA=java
  RMIREGISTRY=rmiregistry
  # when JAVA_HOME is not set, we will set it
  JAVA_HOME=${JAVA_HOME:-"$(dirname $(which java))/.."}
else
  JAVA="$JAVA_HOME/bin/java"
  RMIREGISTRY="$JAVA_HOME/bin/rmiregistry"
fi

VERSION=1.0
LEGOLAS_HOME=$(dirname "${cur_dir}")
LEGOLAS_CONF_DIR="${LEGOLAS_CONF_DIR:-${LEGOLAS_HOME}/conf}"
LEGOLAS_LOGS_DIR="${LEGOLAS_LOGS_DIR:-${LEGOLAS_HOME}/logs}"
LEGOLAS_JAVA_OPTS+=" -Dlegolas.conf.dir=${LEGOLAS_CONF_DIR} -Dlegolas.logs.dir=${LEGOLAS_LOGS_DIR}"
LEGOLAS_JAVA_OPTS+=" -Dlog4j.configuration=file:${LEGOLAS_CONF_DIR}/log4j.properties"

LEGOLAS_ANALYZER_JAR="${LEGOLAS_HOME}/analyzer/target/analyzer-${VERSION}-jar-with-dependencies.jar"
LEGOLAS_INJECTOR_JAR="${LEGOLAS_HOME}/injector/target/injector-${VERSION}-jar-with-dependencies.jar"
LEGOLAS_ORCHESTRATOR_JAR="${LEGOLAS_HOME}/orchestrator/target/orchestrator-${VERSION}-jar-with-dependencies.jar"
LEGOLAS_REPORTER_JAR="${LEGOLAS_HOME}/reporter/target/reporter-${VERSION}-jar-with-dependencies.jar"

LEGOLAS_ANALYZER_MAIN=edu.umich.order.legolas.analyzer.AnalyzerMain
LEGOLAS_INJECTOR_MAIN=edu.umich.order.legolas.injector.InjectorMain
LEGOLAS_ORCHESTRATOR_MAIN=edu.umich.order.legolas.orchestrator.OrchestratorMain
LEGOLAS_REPORTER_MAIN=edu.umich.order.legolas.reporter.ReporterMain

LEGOLAS_ANALYZER_CLASSPATH="${LEGOLAS_CONF_DIR}:${LEGOLAS_ANALYZER_JAR}"
LEGOLAS_INJECTOR_CLASSPATH="${LEGOLAS_CONF_DIR}:${LEGOLAS_INJECTOR_JAR}"
LEGOLAS_ORCHESTRATOR_CLASSPATH="${LEGOLAS_CONF_DIR}:${LEGOLAS_ORCHESTRATOR_JAR}"
LEGOLAS_REPORTER_CLASSPATH="${LEGOLAS_CONF_DIR}:${LEGOLAS_REPORTER_JAR}"

LEGOLAS_ANALYZER_JAVA_OPTS=" $LEGOLAS_JAVA_OPTS -Dlegolas.log.file=legolas-analyzer.log"
LEGOLAS_INJECTOR_JAVA_OPTS=" $LEGOLAS_JAVA_OPTS -Dlegolas.log.file=legolas-injector.log"
LEGOLAS_ORCHESTRATOR_JAVA_OPTS=" $LEGOLAS_JAVA_OPTS -Dlegolas.log.file=legolas-orchestrator.log"
LEGOLAS_REPORTER_JAVA_OPTS=" $LEGOLAS_JAVA_OPTS -Dlegolas.log.file=legolas-reporter.log"
