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

# Find the env.sh in lib; to be sourced by scripts inside the reporter
# directory. Should NOT be directly executed.

this_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
env_src=$(dirname "${this_dir}")/lib/env.sh
if [ ! -f ${env_src} ]; then
  echo "Could not find ${env_src}"
  exit 1
fi
. ${env_src}

legolas=${bin_dir}/legolas.sh
