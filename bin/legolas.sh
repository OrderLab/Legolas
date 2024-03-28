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

# The main Legolas script

bin_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
env_source="${bin_dir}/legolas-env.sh"

if [ ! -f ${env_source} ]; then
  echo "Could not find the env source ${env_source}" >&2
  exit 1
fi
# source the envs
. ${env_source}

if [ ! -d ${LEGOLAS_LOGS_DIR} ]; then
  mkdir -p ${LEGOLAS_LOGS_DIR}
fi

if [ $# -lt 1 ]; then
  echo "Usage: $0 {analyzer|injector|reporter|orchestrator|rmi|all}  [argument ...]" >&2
  exit 1
fi
command=$1
shift
case "$command" in
  analyzer)
    "${JAVA}" -cp ${LEGOLAS_ANALYZER_CLASSPATH} ${LEGOLAS_ANALYZER_JAVA_OPTS} ${LEGOLAS_ANALYZER_MAIN} "$@"
    ;;
  injector)
    "${JAVA}" -cp ${LEGOLAS_INJECTOR_CLASSPATH} ${LEGOLAS_INJECTOR_JAVA_OPTS} ${LEGOLAS_INJECTOR_MAIN} "$@"
    ;;
  reporter)
    "${JAVA}" -cp ${LEGOLAS_REPORTER_CLASSPATH} ${LEGOLAS_REPORTER_JAVA_OPTS} ${LEGOLAS_REPORTER_MAIN} "$@"
    ;;
  orchestrator)
    "${JAVA}" -cp ${LEGOLAS_ORCHESTRATOR_CLASSPATH} ${LEGOLAS_ORCHESTRATOR_JAVA_OPTS} ${LEGOLAS_ORCHESTRATOR_MAIN} "$@"
    ;;
  rmi)
    CLASSPATH=${LEGOLAS_ORCHESTRATOR_CLASSPATH} $RMIREGISTRY $@ &
    if [ $? -eq 0 ]; then
      echo "rmiregistry daemon started with PID $!"
    else
      echo "failed to start the rmiregistry daemon"
    fi
    ;;
  all)
    "${JAVA}" -cp ${LEGOLAS_ANALYZER_CLASSPATH} ${LEGOLAS_ANALYZER_JAVA_OPTS} ${LEGOLAS_ANALYZER_MAIN} "$@"
    "${JAVA}" -cp ${LEGOLAS_INJECTOR_CLASSPATH} ${LEGOLAS_INJECTOR_JAVA_OPTS} ${LEGOLAS_INJECTOR_MAIN} "$@"
    "${JAVA}" -cp ${LEGOLAS_ORCHESTRATOR_CLASSPATH} ${LEGOLAS_ORCHESTRATOR_JAVA_OPTS} ${LEGOLAS_ORCHESTRATOR_MAIN} "$@"
    ;;
  -h|--help|help)
    echo "Usage: $0 {analyzer|injector|reporter|orchestrator|rmi|all} [argument ...]"
    exit 0
    ;;
  *)
    echo "Usage: $0 {analyzer|injector|reporter|orchestrator|rmi|all} [argument ...]" >&2
    exit 1
esac
