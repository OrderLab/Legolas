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

# Find the env.sh in lib; to be sourced by scripts inside the analysis
# directory. Should NOT be directly executed.

function run_legolas_analyzer() {
  ${legolas} analyzer -o ${out_dir} "$@"
  if [ $? -ne 0 ]; then
    exit $?
  fi
}

function display_usage()
{
  cat <<EOF
Usage: 
  $0 [options] $target_dir_str version [args]

  -h: display this message

  -j: generate Jimple code

  -a, --analysis: analysis to run. supported analyses:
                  * 'wjtp.asmi' - abstract state analysis (default)
                  * 'wjtp.metainfo' - meta-info analysis

  -i, --instrument: instrument the abstracte states back to the $target_dir_str. 
                    If this option is not specified, the generated AS will reside
                    in sootOutput dir.

  -r, --rule FILE: use the injection rule specified in FILE

  -e, --exception FILE: use the exception table specified in FILE

EOF
}

function parse_args()
{
  while (( "$#" )); do
    case "$1" in
      -i|--instrument)
        instrument=1
        shift
      ;;
      -a|--analysis)
        analysis=$2
        shift 2
      ;;
      -r|--rule)
        use_rule=1
        rule_file=$2
        shift 2
      ;;
      -e|--exception)
        exception_table_file=$2
        shift 2
      ;;
      -j)
        gen_code_option=
        shift
      ;;
      -h|--help)
        display_usage
        exit 0
      ;;
      *)
      break
      ;;
    esac
  done
  if [ $# -le 1 ]; then
    >&2 display_usage 
    exit 1
  fi
  if [ ! -d $1 ]; then
    echo "Target dir $1 does not exist"
    exit 1
  fi
  analysis_target_dir=$(cd $1 && pwd)
  target_sys_version=$2
  shift 2
  position_args="$@ --dump"
  if [ -z "$analysis" ]; then
    analysis="wjtp.asmi"
  fi
}

function copy_soot_out_class_files()
{
  if [ ! -z "$1" -a ! -z "$2" ]; then
    local soot_output_dir=$1
    local dest_dir=$2
    local full_classes=$(find $soot_output_dir -name "*.class")
    local fc=""
    local relative_class=""
    local dest_class=""
    local log_file=$soot_output_dir/copied_classes.txt
    local do_copy=0
    for fc in $full_classes;
    do
      relative_class=${fc#$soot_output_dir}
      dest_class=$dest_dir/$relative_class
      do_copy=0
      if [ -f $dest_class ]; then
        do_copy=1
      else
        dest_class_dir=$(dirname $dest_class)
        # As long as the class file directory exists in the target system, we copy 
        # the class from soot output dir. This is because Soot may generate aux
        # classes for a complex class, which will not exist in the target directory.
        # For example, Soot generates `DefaultMetricsProvider$DefaultMetricsContext$lambda_dump_*.class`
        # for ZooKeeper, while the target directory only has a single `DefaultMetricsProvider$DefaultMetricsContext.class`.
        if [ -d $dest_class_dir ]; then
          do_copy=1
        fi
      fi
      if [ $do_copy -eq 1 ]; then
        cp $fc $dest_class
        echo $dest_class >> $log_file
      fi
    done
  fi
}

this_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
env_src=$(dirname "${this_dir}")/lib/env.sh
if [ ! -f ${env_src} ]; then
  echo "Could not find ${env_src}"
  exit 1
fi
. ${env_src}

# after sourcing the env, we get all the core env variables we needed. test one..
if [ -z "${out_dir}" -o ${out_dir} == "." -o ${out_dir} == "/" ]; then
  echo "Output dir set to '${out_dir}', will shoot ourselves in the foot..."
  exit 1
fi
legolas=${bin_dir}/legolas.sh

instrument=0
use_rule=0
analysis_target_dir=
analysis=
target_dir_str="TARGET_DIR"
target_sys_version=
position_args=
gen_code_option=-e
