## common.sh

These variables will be defined in `common.sh`:

| Variable | Description |
| -------- | ----------- |
| `this_dir` | this directory |
| `env_src` | `../lib/env.sh` |
| `legolas` | `$bin_dir/legolas.sh` |
| `instrument` | 0 or 1, whether to copy the instrumented code to the original location |
| `use_rule` | 0 or 1, whether to use injection rules |
| `analysis_target_dir` | the path to the target system |
| `target_dir_str` | a string showing the target system name, not used in Java code |
| `position_args` | store the extra arguments that will be appended to the original command |
| `gen_code_option` | `-e` means generating Jimple code |


Usage of `parse_args`:
`analysis_target_dir=$1`
| `-i, --instrument` | `instrument=1` |
| `-r, --rule` | `use_rule=1` |
| `-j` | `gen_code_option=` |
| `-h, --help` | display the usage |
The remaining arguments not parsed will goes to `position_args`.



Usage of `run_legolas_analyzer`:
`$legolas analyzer -o $out_dir "$@"`

## analyze-zookeeper.sh

These variables will be defined in `common.sh`:

| Variable | Description |
| -------- | ----------- |
| `my_dir` | this directory |
| `zk_dir` | `$analysis_target_dir` |
| `zk_build_dir` | the `build` directory in ZooKeeper directory |
| `zk_quorum_dir` | `org/apache/zookeeper/server/quorum` directory in ZooKeeper binary code |
| `position_args` | set accordingly if `$use_rule` |
