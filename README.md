# Source code repository for the Legolas project

## Overview

Legolas is an end-to-end fault injection framework designed to efficiently
expose partial failure bugs in large distributed systems. It uses program
analysis to perform system-specific fault injection *without* requiring a
special environment (e.g., a special file system for injecting I/O errors).  It
also automatically infers the abstract states from concrete system code and
uses the abstract states to guide efficient exploration of large fault
injection space.

Legolas contains a static analyzer, fault injector, system orchestrator,
workload driver, and result reporter.

## Requirements

Legolas is mainly written in Java and tested under Ubuntu 18.04 to 22.04 with
JDK 8 to JDK 11. We use [Apache Maven](https://maven.apache.org/) to manage the 
project modules and compilation.

## Download
```
git clone https://github.com/OrderLab/Legolas
```

## Build

```
$ mvn package
```

## Usage

Applying Legolas to a system involves two steps: (1) invoking the static
analyzer, which will both instrument fault injection hooks and infer the
abstract system states; (2) starting fault injection testing experiment under
the Legolas orchestrator.

The main script for running Legolas is `bin/legolas.sh`.

```bash
bin/legolas.sh

Usage: bin/legolas.sh {analyzer|injector|reporter|orchestrator|rmi|all}  [argument ...]
```

Each sub-command has additional usage help:

```bash
bin/legolas.sh analyzer

usage: legolas-analyzer.jar [OPTIONS] -- [SOOT OPTIONS]
 -a,--analysis <name name ...>                    List of analysis names to run
                                                  on the subject software
...
```

In the `scripts` directory, we also provide a set of wrapper scripts for running 
`bin/legolas.sh` on the systems we evaluated.

### 1. Static analysis and instrumentation

#### 1.1 Out-of-place analysis

Run the static analysis and instrumentation outside the target system source tree:

```
scripts/analysis/analyze-zookeeper.sh ../zookeeper 3.6.2
```

#### 1.2 Instrumentation result inspection

The analysis results are generated in the `sootOutput` directory under the current directory, which 
contain the analyzed and instrumented Java classes for the target system.

For example, `sootOutput/org/apache/zookeeper/server/SyncRequestProcessor.class` contains the 
Legolas-instrumented `SyncRequestProcessor` class for ZooKeeper. You can use a Java class 
decompilation tool to inspect the source code of this class.

The wrapper script also by default dumps the injection points (by passing the
`--dump` option to the `bin/legolas.sh` script) to a file `data/injection_points.csv`.
It is recommended that you inspect this file to check if the instrumentation is
reasonable.

#### 1.3 In-place instrumentation

To analyze and copy the instrumented code back to target system, use the `-i` flag. 
**This step is required before running the fault injection experiment, so that Legolas 
runs the instrumented target system.**

```
scripts/analysis/analyze-zookeeper.sh -i ../zookeeper 3.6.2
```

Note that the script will modify the classes in the target directory. If you 
run the script multiple times on the target system, it will take care of
cleaning the build before running the analyzer to avoid incorrectly instrumenting
the target multiple times.

### 2. Fault injection testing

#### 2.1 Setup experiment

Prepare the workspace, experiment scripts, and configurations for a fault injection experiment.

```bash
scripts/experiment/setup-legolas-zookeeper.sh ../zookeeper 3.6.2
```

The setup scripts and configurations are generated in `workspace/legolas-zk/`.

#### 2.2 Update experiment configuration

Check the generated configuration file `workspace/legolas-zk/legolas-zk.properties`
and customize it if necessary.

For example, you can update the `maxTrials` parameter (number of fault
injection testing trials).

#### 2.3 Run experiment

```bash
scripts/experiment/start-legolas-zookeeper.sh 2>&1 | tee workspace/legolas-zk/result.txt
```

Legolas orchestrator will emit logs for the experiment in `logs/legolas-orchestrator.log`.

The experiment data is stored in `workspace/legolas-zk/trials/`. For example, 
`workspace/legolas-zk/trials/0` stores the clients outputs and ZooKeeper system logs
for the fault injection trial 0.

### 3. Analyze testing results

Invoke the Legolas reporter on the experiment data directory to analyze the 
testing results.

```
bin/legolas.sh reporter -s conf/zookeeper/3.6.2/reporter.json -e workspace/legolas-zk
```

## Troubleshooting

If all fault injection trials fail with zero progress in the first workload phase 
(e.g., `[0/3, 0/3, 0/4]` for Phase 0 in ZooKeeper testing), there is likely something
wrong with the instrumentation. Check the system log in one of the trials:

```
cd workspace/legolas-zk/trials/0/logs-1/
vim zookeeper-*-server-*.out

2024-03-27 23:08:02,835 [myid:1] - INFO  [main:QuorumPeerMain@151] - Starting quorum peer, myid=1
Exception in thread "main" java.lang.NoClassDefFoundError: org/apache/zookeeper/metrics/impl/DefaultMetricsProvider$DefaultMetricsContext$lambda_getSummary_1__117
  at org.apache.zookeeper.metrics.impl.DefaultMetricsProvider$DefaultMetricsContext.getSummary(DefaultMetricsProvider.java:115)
  at org.apache.zookeeper.server.ServerMetrics.<init>(ServerMetrics.java:70)
  at org.apache.zookeeper.server.ServerMetrics.<clinit>(ServerMetrics.java:44)
  at org.apache.zookeeper.server.quorum.QuorumPeerMain.runFromConfig(QuorumPeerMain.java:161)
  at org.apache.zookeeper.server.quorum.QuorumPeerMain.initializeAndRun(QuorumPeerMain.java:136)
  at org.apache.zookeeper.server.quorum.QuorumPeerMain.main(QuorumPeerMain.java:90)
...
```

## Publication

```
@inproceedings{Legolas2024NSDI,
  author = {Wu, Haoze and Pan, Jia and Huang, Peng},
  title = {Efficient Exposure of Partial Failure Bugs in Distributed Systems with Inferred Abstract States},
  booktitle = {Proceedings of the 21st USENIX Symposium on Networked Systems Design and Implementation},
  series = {NSDI '24},
  month = {April},
  year = {2024},
  location = {Santa Clara, CA, USA},
}
```
