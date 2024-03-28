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

# The main script to launch MC for any target system

my_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
. ${my_dir}/common.sh

workspace=$workspace_dir/legolas-zk

if [ ! -d ${workspace} ]; then
  echo "workspace ${workspace} does not exist"
  exit 1
fi

if [ ! -f ${workspace}/legolas-zk.properties ]; then
  echo "orchestrator configuration does not exist in ${workspace}"
  exit 1
fi

$bin_dir/legolas.sh orchestrator $workspace/legolas-zk.properties
