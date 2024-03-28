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

# Run the ASM analysis on ZooKeeper and instrument ASM in ZooKeeper bytecode

my_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
. ${my_dir}/common.sh

target_dir_str="ZOOKEEPER_DIR"
parse_args "$@"
zk_dir=$analysis_target_dir

if [ ! -d ${zk_dir} ]; then
  echo "ZooKeeper dir does not exist ${zk_dir}"
  exit 1
fi
if [ -d ${zk_dir}/build/classes ]; then
  # zookeeper built using ant
  zk_build_dir_main=${zk_dir}/build/classes
  zk_build_dir_other=""
elif [ -d ${zk_dir}/zookeeper-server/target/classes ]; then
  # zookeeper (recent version) built using maven
  zk_build_dir_main=${zk_dir}/zookeeper-server/target/classes
  # jute classes should also be analyzed
  zk_build_dir_other="${zk_dir}/zookeeper-jute/target/classes"
else
  echo "No classes directory found, have you built ZooKeeper?"
  exit 1
fi

version=$target_sys_version
metadata_dir=$root_dir/conf/zookeeper/$version

if [ ! -d $metadata_dir ]; then
  echo "metadata dir $metadata_dir does not exist"
  exit 1
fi

echo "Analyzing ZooKeeper version $version..."

# clean up last result
rm -rf ${out_dir}/*
mkdir -p ${out_dir}

if [ $use_rule -eq 1 ]; then
  if [ -z "$rule_file" ]; then
    rule_file=${conf_dir}/injection.zookeeper
  fi
  if [ -f $rule_file ]; then
    position_args="--injection_file $rule_file $position_args"
  else
    echo "Injection spec file $rule_file does not exist"
    exit 1
  fi
fi

if [ -z "$exception_table_file" ]; then
  exception_table_file=${conf_dir}/exception_table.zookeeper
  if [ ! -f ${exception_table_file} ]; then
    exception_table_file=${conf_dir}/exception_table.javalib
    if [ ! -f ${exception_table_file} ]; then
      echo "Exception table file ${exception_table_file} does not exist"
      exit 1
    fi
  fi
fi

# Must do a clean-up of the build if the class files are already instrumented!!
# Without the clean-up, each time the analysis will incorrectly insert an 
# additional statement to an instrumentation point!
if [ -d ${zk_build_dir_main}/edu/umich/order/legolas ]; then
  echo "ZooKeeper already instrumented with Legolas. Must clean it first..."
  pushd ${zk_dir} > /dev/null
  if [ -d build/classes ]; then
    ant clean && ant jar
  elif [ -d zookeeper-server/target/classes ]; then
    mvn clean && mvn package -DskipTests
  else
    echo "Unsupported ZK version"
    exit 1
  fi
  rm -rf ${zk_build_dir_main}/edu
  popd
fi

# analyze all classes in zookeeper and specify the main class
run_legolas_analyzer -a $analysis -i ${zk_build_dir_main} ${zk_build_dir_other} -m org.apache.zookeeper.server.quorum.QuorumPeerMain \
  --prefix org.apache.zookeeper --excludes org.apache.zookeeper.ClientCnxn org.apache.zookeeper.Shell org.apache.zookeeper.Login \
  --exception_table_file $exception_table_file \
  $gen_code_option -p jb use-original-names:true $position_args

for i in `cat $metadata_dir/defaultList.txt`; do
  cp $zk_build_dir_main/$i $out_dir/$i
done

if [ $instrument -eq 1 ]; then
  echo "Copying instrumented classes back to ZooKeeper source in $zk_dir"
  mkdir -p ${zk_build_dir_main}/org
  if [ -z "$zk_build_dir_other" ]; then
    # When only one ZK build dir exists, we can simply do the cp -r
    cp -r ${out_dir}/org ${zk_build_dir_main}/
  else
    # Otherwise, we need to check each file to see where it should belong
    copy_soot_out_class_files $out_dir $zk_build_dir_main
    copy_soot_out_class_files $out_dir $zk_build_dir_other
  fi
  echo "Copying Legolas common code to ZooKeeper source in $zk_dir"
  cp -r ${root_dir}/common/target/classes/edu ${zk_build_dir_main}
else
  echo "Legolas analysis results are generated in $out_dir"
fi
