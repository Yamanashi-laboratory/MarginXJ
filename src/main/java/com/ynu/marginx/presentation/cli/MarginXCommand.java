package com.ynu.marginx.presentation.cli;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.application.CancellableRun;
import com.ynu.marginx.application.JudgeOperationUseCase;
import com.ynu.marginx.application.OptimizationReport;
import com.ynu.marginx.application.OptimizeCircuitUseCase;
import com.ynu.marginx.application.ScoreChoice;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementOutcome;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.JudgementSpecRepository;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.CriticalElementFinder;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.domain.service.CriticalMarginCalculator;
import com.ynu.marginx.domain.service.CriticalMarginMethod;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.domain.service.MarginTableCalculator;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.domain.service.RandomSource;
import com.ynu.marginx.domain.service.ScoreCalculator;
import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.judgement.FileJudgementSpecRepository;
import com.ynu.marginx.infrastructure.judgement.JudgementSpecParser;
import com.ynu.marginx.infrastructure.netlist.FileNetlistRepository;
import com.ynu.marginx.infrastructure.netlist.NetlistParser;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.FileMarginResultRepository;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.simulator.SimulatorRegistry;
import com.ynu.marginx.infrastructure.simulator.SimulatorWorkspaces;
import com.ynu.marginx.presentation.cli.view.DetailView;
import com.ynu.marginx.presentation.cli.view.MarginChartView;
import com.ynu.marginx.presentation.cli.view.OptimizationProgressBarView;
import com.ynu.marginx.presentation.cli.view.ProgressBarView;
import com.ynu.marginx.shared.exception.MarginXException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "marginx",
        description = "Margin analysis for superconducting circuits.",
        mixinStandardHelpOptions = true,
        versionProvider = BuildVersionProvider.class)
public final class MarginXCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "CIRCUIT",
            description = "Base name of the circuit file, without the .cir/.inp extension.")
    private String circuit;

    @Option(names = "-j", paramLabel = "JUDGEMENT",
            description = "Base name of the judgement file. Defaults to the circuit name.")
    private String judgement;

    @Option(names = "-d", description = "Print per-element margin details.")
    private boolean detail;

    @Option(names = {"-m", "--mode"}, description = "Skip the interactive menu and run this mode.")
    private Integer mode;

    @Option(names = "--simulator", paramLabel = "NAME",
            description = "Which simulator to run: josim, jsim or auto. Defaults to auto, which"
                    + " prefers JoSIM and warns before falling back to JSIM.")
    private String simulator = "auto";

    @Option(names = "--set-josim-path", paramLabel = "PATH",
            description = "Remember this JoSIM executable for future runs, then exit.")
    private String josimPath;

    @Option(names = "--set-jsim-path", paramLabel = "PATH",
            description = "Remember this JSIM executable for future runs, then exit.")
    private String jsimPath;

    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(10);

    /** The run the shutdown hook should stop, if any is in progress. */
    private volatile CancellableRun active;

    @Option(names = {"-s", "--score"},
            description = "What an optimisation maximises: 1 critical, 2 bias, 3 upper, 4 lower,"
                    + " 5 critical+bias, 6 critical+2*bias. Defaults to 1.")
    private int scoreCode = 1;

    public static void main(String[] args) {
        System.exit(new CommandLine(new MarginXCommand()).execute(args));
    }

    @Override
    public Integer call() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        SimulatorProperties properties = SimulatorProperties.load();

        NetlistRepository netlists = new FileNetlistRepository(workingDirectory, new NetlistParser());
        JudgementSpecRepository specs =
                new FileJudgementSpecRepository(workingDirectory, new JudgementSpecParser());
        UserSimulatorSettings userSettings = UserSimulatorSettings.inDefaultLocation();

        try {
            if (josimPath != null || jsimPath != null) {
                return remember(userSettings);
            }
            // JoSIM when it is installed, JSIM only when it is not: neither ships with MarginXJ.
            // Selecting inside the try keeps a missing simulator on the same one-line error path.
            ProcessExecutor executor = new ProcessExecutor();
            installShutdownHook(executor);
            SimulatorRegistry registry = new SimulatorRegistry(properties, userSettings,
                    new NetlistRenderer(), executor);
            SimulatorRegistry.Selection selection = registry.resolve(choice());
            CircuitSimulator chosen = selection.simulator();
            if (selection.fallback()) {
                // Never let a change of engine pass unremarked, and say so before the run starts.
                System.out.println(" WARNING : " + selection.warning());
                System.out.println();
            }
            MarginResultRepository results = new FileMarginResultRepository(workingDirectory,
                    new FileMarginResultRepository.Provenance(chosen.displayName(), chosen.name()));
            OperationEvaluator evaluator = new OperationEvaluator(chosen, new OperationJudge());

            Netlist netlist = netlists.load(circuit);
            JudgementSpec spec = specs.load(judgement != null ? judgement : circuit);
            System.out.printf(" Sum of Target      : %d%n", netlist.elementCount());
            System.out.printf(" Total of Judgement : %d%n%n", spec.totalRules());

            OperationMode selected = mode != null
                    ? OperationMode.fromCode(mode)
                    : new InteractiveMenu(System.in, System.out).select();

            if (selected == OperationMode.JUDGE) {
                return report(new JudgeOperationUseCase(evaluator).execute(netlist, spec));
            }
            if (selected == OperationMode.OPTIMIZE_CRITICAL_MARGIN) {
                return report(optimize(netlist, spec, evaluator, netlists, results));
            }
            if (selected == OperationMode.OPTIMIZE_CENTER_OF_GRAVITY
                    || selected == OperationMode.OPTIMIZE_SEQUENTIAL_CGM) {
                return report(optimizeYield(selected, netlist, spec, evaluator, netlists, results));
            }
            return report(calculateMargins(netlist, spec, searcher(selected, evaluator), results));
        } catch (MarginXException e) {
            System.err.println(" ERROR : " + e.getMessage());
            return 1;
        }
    }

    private SimulatorRegistry.Choice choice() {
        return switch (simulator.toLowerCase(Locale.ROOT)) {
            case "auto" -> SimulatorRegistry.Choice.AUTO;
            case "josim" -> SimulatorRegistry.Choice.JOSIM;
            case "jsim" -> SimulatorRegistry.Choice.JSIM;
            default -> throw new MarginXException("Unknown simulator: " + simulator
                    + ". Use josim, jsim or auto.");
        };
    }

    /** --set-*-path writes the setting and stops; it is configuration, not a run. */
    private int remember(UserSimulatorSettings userSettings) {
        if (josimPath != null) {
            userSettings.save(SimulatorKind.JOSIM, josimPath);
            System.out.println(" Saved JoSIM path : " + josimPath);
        }
        if (jsimPath != null) {
            userSettings.save(SimulatorKind.JSIM, jsimPath);
            System.out.println(" Saved JSIM path  : " + jsimPath);
        }
        System.out.println(" Settings file    : " + userSettings.file());
        return 0;
    }

    private MarginSearcher searcher(OperationMode selected, OperationEvaluator evaluator) {
        return switch (selected) {
            case MARGIN_BINARY -> new BinarySearchMarginSearcher(evaluator);
            case MARGIN_SYNCHRONIZED -> ExhaustiveMarginSearcher.synchronizingGroups(evaluator);
            default -> new ExhaustiveMarginSearcher(evaluator);
        };
    }

    private OptimizationOutcome optimize(Netlist netlist, JudgementSpec spec, OperationEvaluator evaluator,
                                         NetlistRepository netlists, MarginResultRepository results) {
        System.out.println(" ~ Critical Margin Method ~");
        OptimizeCircuitUseCase useCase = track(new OptimizeCircuitUseCase(netlists, results));
        // The C++ tool measures once with the exhaustive search and re-measures with the binary one.
        MarginTableCalculator initial = useCase.measurements(new ExhaustiveMarginSearcher(evaluator));
        MarginTableCalculator refined = useCase.measurements(new BinarySearchMarginSearcher(evaluator));
        CriticalMarginMethod method =
                new CriticalMarginMethod(initial, refined, new CriticalElementFinder());
        return useCase.withCriticalMarginMethod(method, netlist, spec);
    }

    private OptimizationOutcome optimizeYield(OperationMode selected, Netlist netlist, JudgementSpec spec,
                                              OperationEvaluator evaluator, NetlistRepository netlists,
                                              MarginResultRepository results) {
        boolean sequential = selected == OperationMode.OPTIMIZE_SEQUENTIAL_CGM;
        System.out.println(sequential
                ? " ~ Sequential Center of Gravity Method ~"
                : " ~ Center of Gravity Method ~");
        ScoreChoice score = ScoreChoice.fromCode(scoreCode);
        System.out.println(" Score : " + score.label());
        CriticalMarginCalculator criticalMargins = new CriticalMarginCalculator();
        CenterOfGravityOptimizer.Settings settings = CenterOfGravityOptimizer.Settings.defaults();
        OptimizeCircuitUseCase useCase = track(new OptimizeCircuitUseCase(netlists, results,
                new OptimizationProgressBarView(System.out)));
        CenterOfGravityOptimizer optimizer = new CenterOfGravityOptimizer(
                useCase.sampling(evaluator, settings.cycles()),
                useCase.measurements(new BinarySearchMarginSearcher(evaluator)), criticalMargins,
                new ScoreCalculator(criticalMargins), RandomSource.unseeded(), settings,
                sequential
                        ? CenterOfGravityOptimizer.Variant.SEQUENTIAL
                        : CenterOfGravityOptimizer.Variant.YIELD_UP);
        return useCase.withCenterOfGravity(optimizer, netlist, spec, score.weights());
    }

    /** Remembers the run now in progress so the shutdown hook can stop it. */
    private <T extends CancellableRun> T track(T useCase) {
        active = useCase;
        return useCase;
    }

    /**
     * Ctrl+C has to leave the machine as tidy as the cancel button does: stop the run, let the
     * workers kill their simulators, then sweep up any working directory whose thread never got
     * the chance to. A hook is the only place that can happen - the JVM is on its way out.
     */
    private void installShutdownHook(ProcessExecutor executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CancellableRun running = active;
            if (running != null) {
                running.cancel();
                running.awaitTermination(SHUTDOWN_GRACE);
            }
            executor.destroyLiveProcesses();
            SimulatorWorkspaces.deleteRemaining();
        }, "marginx-shutdown"));
    }

    private MarginTable calculateMargins(Netlist netlist, JudgementSpec spec, MarginSearcher searcher,
                                         MarginResultRepository results) {
        System.out.println(" Calculating Margins...");
        return track(new CalculateMarginUseCase(searcher, results))
                .execute(netlist, spec, new ProgressBarView(System.out));
    }

    private int report(MarginTable table) {
        new MarginChartView(System.out, new CriticalElementFinder()).print(table);
        if (detail) {
            new DetailView(System.out).print(table);
        }
        return 0;
    }

    private int report(OptimizationOutcome outcome) {
        System.out.printf(" Stopped after %d trial(s): %s%n", outcome.trials(), OptimizationReport.explain(outcome.reason()));
        return report(outcome.margins());
    }

    private int report(JudgementOutcome outcome) {
        if (outcome.passed()) {
            System.out.println(" ------PASS------");
            return 0;
        }
        System.out.println("  ------NOT PASSED------");
        System.out.println(" A Violation was detected in : " + outcome.violation());
        return 1;
    }
}
