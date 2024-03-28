#!/usr/bin/env bash
#
# @author Haoze Wu <haoze@jhu.edu>
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

# The script to set up MC workspace for ZK

my_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
. ${my_dir}/common.sh

target_dir_str="ZOOKEEPER_DIR"
parse_args "$@"
# the dir argument may be a relative path, turn it into a full path
zk_dir="$(cd "$legolas_target_dir"; pwd)"

if [ ! -d ${zk_dir} ]; then
  echo "ZooKeeper dir does not exist ${zk_dir}"
  exit 1
fi

if [ -d ${zk_dir}/build/classes ]; then
  # zookeeper built using ant
  zk_build_dir=${zk_dir}/build
elif [ -d ${zk_dir}/zookeeper-server/target/classes ]; then
  # zookeeper (recent version) built using maven
  zk_build_dir=${zk_dir}/zookeeper-server/target
else
  echo "No classes directory found, have you built ZooKeeper?"
  exit 1
fi

version=$target_sys_version

metadata_dir=$root_dir/conf/zookeeper/$version
if [ ! -d ${metadata_dir} ]; then
  echo "ZooKeeper metadata dir ${metadata_dir} does not exist"
  exit 1
fi

case $version in
  3.4.* | 3.5.*)
    # sanity check
    if [[ $zk_build_dir == */target ]]; then
      echo "ZooKeeper version $version should not have build dir $zk_build_dir"
      exit 1
    fi
    zk_client_jar_file=$root_dir/driver/zookeeper/3.4.6/target/zk_3_4_6-1.0.jar
    zk_client_jar_main=edu.umich.order.legolas.zk_3_4_6.ZooKeeperGrayClientMain
    ;;
  3.6.*)
    # sanity check
    if [[ $zk_build_dir == */build ]]; then
      echo "ZooKeeper version $version should not have build dir $zk_build_dir"
      exit 1
    fi
    zk_client_jar_file=$root_dir/driver/zookeeper/3.6.2/target/zk_3_6_2-1.0-jar-with-dependencies.jar
    zk_client_jar_main=edu.umich.order.legolas.zk_3_6_2.ZooKeeperGrayClientMain
    ;;
  *)
    echo "unsupported version: $version"
    exit 1
    ;;
esac

echo "Setting up legolas workspace for ZooKeeper $version..."

workspace=$workspace_dir/legolas-zk

targetSystem=zookeeper
workload=CreateReadWrite

injectionController=Blind
injectionPolicy=RoundRobinStateOp
injectionType=exception
snapCount=100

clientPortBase=10711
peerPortBase=10813
electionPortBase=10915

statPort=11111

rm -rf $workspace
mkdir -p $workspace

for i in 1 2 3; do
  mkdir -p $workspace/store-$i
  echo $i > $workspace/store-$i/myid
  mkdir $workspace/conf-$i
  cp $zk_dir/conf/log4j.properties $workspace/conf-$i/
  cp $zk_dir/conf/configuration.xsl $workspace/conf-$i/
  if [ -d $metadata_dir/store-$i ]; then
    cp -r $metadata_dir/store-$i/* $workspace/store-$i/
  fi

echo "
dataDir=$workspace/store-$i
tickTime=2000
initLimit=10
syncLimit=5
snapCount=$snapCount
clientPort=$(($clientPortBase + $i))
" > $workspace/conf-$i/zoo.cfg;

  for j in 1 2 3; do
    echo "server.$j=localhost:$(($peerPortBase + $j)):$(($electionPortBase + $j))" >> $workspace/conf-$i/zoo.cfg;
  done;
done

echo "
workspacePathName=$workspace
targetSystem=$targetSystem
targetSystemPathName=$zk_dir
initDataPathName=$metadata_dir
version=$version
workload=$workload
injectionController=$injectionController
injectionPolicy=$injectionPolicy
injectionType=$injectionType
exceptionTableFilePath=$root_dir/conf/exception_table.zookeeper
snapCount=$snapCount
trialTimeout=7000
warmupMillis=1000
useLogMonitor=true
maxTrials=3
failTrialRetries=2
maxTotalRetries=10
" > $workspace/legolas-zk.properties

for i in 1 2 3; do
echo "
clientPort.$i=$(($clientPortBase + $i))
peerPort.$i=$(($peerPortBase + $i))
electionPort.$i=$(($electionPortBase + $i))
" >> $workspace/legolas-zk.properties;
done

mkdir $workspace/trials

echo "#!/bin/bash
ZOOCFGDIR=$workspace/conf-\$2 ZOO_LOG_DIR=$workspace/trials/\$1/logs-\$2 $zk_dir/bin/zkServer.sh start
" > $workspace/server.sh
chmod +x $workspace/server.sh

echo "#!/bin/bash
java -cp $workspace/conf-1:$zk_client_jar_file $zk_client_jar_main \$@
" > $workspace/client.sh
chmod +x $workspace/client.sh

echo "#!/bin/bash
if [ \$# -eq 1 ]; then
  port=\$(($clientPortBase+\$1))
else
  port=$(($clientPortBase+1))
fi
$zk_dir/bin/zkCli.sh -server localhost:\$port \$@
" > $workspace/zkCli.sh
chmod +x $workspace/zkCli.sh

gen_dummy_injection_scripts $workspace

echo "#!/bin/bash
for i in 1 2 3; do
  ZOOCFGDIR=$workspace/conf-\$i ZOO_LOG_DIR=$workspace/trials/0/logs-\$i $zk_dir/bin/zkServer.sh start
done
" > $workspace/start-cluster.sh
chmod +x $workspace/start-cluster.sh

echo "#!/bin/bash
for i in 1 2 3; do
  ZOOCFGDIR=$workspace/conf-\$i ZOO_LOG_DIR=$workspace/trials/0/logs-\$i $zk_dir/bin/zkServer.sh stop
done
" > $workspace/stop-cluster.sh
chmod +x $workspace/stop-cluster.sh
