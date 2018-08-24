package iguana.benchmark;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static iguana.Utils.getFiles;
import static iguana.Utils.getJDK7SourceLocation;
import static java.util.stream.Collectors.toList;
import static org.openjdk.jmh.results.format.ResultFormatType.CSV;

public class ParserBenchmark {

    public static void main(String[] args) throws RunnerException, IOException {
        String benchmarkName = args[0];
        if (benchmarkName == null) {
            throw new RuntimeException("Benchmark name is empty, should be: Antlr, EclipseJDT or Iguana");
        }

        String[] params = getFiles(getJDK7SourceLocation(), ".java")
                .stream()
                .map(Path::toString)
                .collect(toList()).toArray(new String[]{});

        Options options = new OptionsBuilder()
                .include(benchmarkName + "Benchmark")
                .mode(Mode.SingleShotTime)
                .param("path", params)
                .timeUnit(TimeUnit.MILLISECONDS)
                .forks(1)
                .warmupIterations(5)
                .measurementIterations(10)
                .resultFormat(CSV)
                .result(benchmarkName + ".csv")
                .jvmArgs("-Xss4m")
                .build();

        new Runner(options).run();
    }
}
