#!/usr/bin/env bash
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

# Find the env.sh in lib; to be sourced by scripts inside the experiment
# directory. Should NOT be directly executed.

function display_usage()
{
  cat <<EOF
Usage: 
  $0 [options] $target_dir_str version

  -h: display this message

EOF
}

function parse_args()
{
  if [ "$1" == "-h" -o "$1" == "--help" ]; then
    display_usage
    exit 0
  else
    if [ $# -le 1 ]; then
      >&2 display_usage
      exit 1
    fi
    legolas_target_dir=$1
    target_sys_version=$2
  fi
}

function gen_dummy_injection_scripts() 
{
  local workspace_local=$1
  echo "#!/bin/bash
echo \"no injection in trial \$1\"
" > $workspace_local/injection.sh
  chmod +x $workspace_local/injection.sh

  echo "#!/bin/bash
echo \"no injection\"
" > $workspace_local/injection-trialend.sh
  chmod +x $workspace_local/injection-trialend.sh

  echo "#!/bin/bash
echo \"empty injection driver setup\"
" > $workspace_local/injection-setup.sh
  chmod +x $workspace_local/injection-setup.sh

  echo "#!/bin/bash
echo \"empty injection driver cleanup\"
" > $workspace_local/injection-destroy.sh
  chmod +x $workspace_local/injection-destroy.sh
}

this_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
env_src=$(dirname "${this_dir}")/lib/env.sh
if [ ! -f ${env_src} ]; then
  echo "Could not find ${env_src}"
  exit 1
fi
. ${env_src}

# after sourcing the env, we get all the core env variables we needed. test one..
if [ -z "${root_dir}" -o -z "${workspace_dir}" ]; then
  echo "root dir or workspace dir is not set"
  exit 1
fi
legolas=${bin_dir}/legolas.sh

legolas_target_dir=
target_dir_str="TARGET_DIR"
target_sys_version=
