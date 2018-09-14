 #!/usr/bin/env bash

caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Iguana jdk7u-jdk 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Iguana elasticsearch 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Iguana guava 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Iguana RxJava 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Iguana junit4 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Antlr jdk7u-jdk 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Antlr elasticsearch 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Antlr guava 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Antlr RxJava 5 10 &&
caffeinate java -cp target/benchmarks.jar iguana.benchmark.ParserBenchmark Antlr junit4 5 10
