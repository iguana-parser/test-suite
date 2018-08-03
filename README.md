
To run benchmarks:

```bash
export JDK7_DIR=/Users/Ali/workspace-thesis/jdk7u-jdk

mvn clean install

java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark <Benchmark Name>
```

Benchmark name can be `Iguana`, `Antlr`, or `EclipseJDT`.