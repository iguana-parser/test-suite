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
import static iguana.Utils.getSourceDir;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openjdk.jmh.results.format.ResultFormatType.CSV;

public class ParserBenchmark {

    public static void main(String[] args) throws RunnerException, IOException {
        if (args.length == 0) {
            System.out.println("Missing parameters: ParserBenchmark <benchmarkName> <projectName> <warmupIter> <measurementIter>");
        }
        String benchmarkName = args[0];
        String projectName = args[1];

        requireNonNull(benchmarkName, "Benchmark name is empty, should be: Antlr, EclipseJDT or Iguana");
        requireNonNull(projectName, "Please provide a valid project name in the source folder");

        int warmupIterations = 5;
        if (args[2] != null) {
            warmupIterations = Integer.parseInt(args[2]);
        }

        int measurementIterations = 10;
        if (args[3] != null) {
            measurementIterations = Integer.parseInt(args[3]);
        }

        if (getSourceDir() == null) {
            throw new RunnerException("The environment variable 'SOURCE_DIR' is not set");
        }

        String[] params = getFiles(getSourceDir() + "/" + projectName, ".java")
                .stream()
                .map(Path::toString)
                .collect(toList()).toArray(new String[]{});

        Options options = new OptionsBuilder()
                .include(benchmarkName + "Benchmark")
                .mode(Mode.SingleShotTime)
                .param("path", params)
                .timeUnit(TimeUnit.MILLISECONDS)
                .forks(1)
                .warmupIterations(warmupIterations)
                .measurementIterations(measurementIterations)
                .resultFormat(CSV)
                .result(benchmarkName + "_" + projectName + ".csv")
                .output(benchmarkName + "_" + projectName + ".log")
                .jvmArgs("-Xss4m", "-XX:+UseG1GC")
                .build();

        new Runner(options).run();
    }
}
