## legolas-env.sh

These variables will be defined in `legolas-env.sh`:

| Variable | Description |
| -------- | ----------- |
| `cur_dir` | the path of this `bin` directory |
| `JAVA_HOME` | home to Java |
| `JAVA` | `java` executable |
| `RMIREGISTRY` | `rmiregistry` executable |
| `VERSION` | the version of Legolas in Maven configuration |
| `LEGOLAS_HOME` | the parent path of this `bin` directory |
| `LEGOLAS_CONF_DIR` | the `conf` directory |
| `LEGOLAS_LOGS_DIR` | the `logs` directory |
| `LEGOLAS_JAVA_OPTS` | the general Java options used in common |
| `LEGOLAS_ANALYZER_JAR` | analyzer jar |
| `LEGOLAS_ANALYZER_MAIN` | analyzer main class |
| `LEGOLAS_ANALYZER_CLASSPATH` | analyzer classpath |
| `LEGOLAS_ANALYZER_JAVA_OPTS` | the Java options for analyzer |
| `LEGOLAS_INJECTOR_JAR` | injector jar |
| `LEGOLAS_INJECTOR_MAIN` | injector main class |
| `LEGOLAS_INJECTOR_CLASSPATH` | injector classpath |
| `LEGOLAS_INJECTOR_JAVA_OPTS` | the Java options for injector |
| `LEGOLAS_ORCHESTRATOR_JAR` | orchestrator jar |
| `LEGOLAS_ORCHESTRATOR_MAIN` | orchestrator main class |
| `LEGOLAS_ORCHESTRATOR_CLASSPATH` | orchestrator classpath |
| `LEGOLAS_ORCHESTRATOR_JAVA_OPTS` | the Java options for orchestrator |

## legolas.sh

These variables will be defined in `legolas.sh`:

| Variable | Usage |
| -------- | ----- |
| `bin_dir` | the path of this `bin` directory |
| `env_source` | `legolas-env.sh` in this `bin` directory |

Usage: `$0 {analyzer|injector|orchestrator|rmi|all}  [argument ...]`

Internal workflow example: `$JAVA -cp $LEGOLAS_ORCHESTRATOR_CLASSPATH $LEGOLAS_ORCHESTRATOR_JAVA_OPTS $LEGOLAS_ORCHESTRATOR_MAIN "$@"`, where `"$@"` is the arguments.
