
To run benchmarks:

```bash
export SOURCE_DIR=/Users/Ali/workspace-thesis/

mvn clean install

java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark <Benchmark Name> <ProjectName> <WarmupInter> <MeasurementIter>
```

Benchmark name can be `Iguana`, `Antlr`, or `EclipseJDT`.

