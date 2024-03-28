#!/usr/bin/env bash
#
# The Legolas Project
#
# Copyright (c) 2018, Johns Hopkins University - Order Lab.
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

# Environment variables to use

function check_dir() {
  if [ ! -d $1 ]; then
    echo "Could not find directory $1"
    exit 1
  fi
}

scripts_common_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
root_dir=$(dirname $(dirname "${scripts_common_dir}"))  # root is ../..
bin_dir=${root_dir}/bin
out_dir=${root_dir}/sootOutput
conf_dir=${root_dir}/conf
workspace_dir=${root_dir}/workspace
legolas_env=${bin_dir}/legolas-env.sh

if [ ! -f ${legolas_env} ]; then
  echo "Could not find ${legolas_env}"
  exit 1
fi
# source the legolas-env will give us envs like JAVA and JAVA_HOME
. ${legolas_env}

check_dir ${bin_dir}
