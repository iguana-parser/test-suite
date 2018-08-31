
To run benchmarks:

```bash
export SOURCE_DIR=/Users/Ali/workspace-thesis/

mvn clean install

java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark <Benchmark Name> <ProjectName> <WarmupInter> <MeasurementIter>

java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Iguana RxJava 5 10
```


