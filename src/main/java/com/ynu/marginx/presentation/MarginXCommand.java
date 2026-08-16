package com.ynu.marginx.presentation;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.application.ProgressListener;
import com.ynu.marginx.application.JudgeOperationUseCase;
import com.ynu.marginx.application.OptimizeCircuitUseCase;
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
import com.ynu.marginx.domain.service.CriticalMarginMethod;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.domain.service.MarginTableCalculator;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.judgement.FileJudgementSpecRepository;
import com.ynu.marginx.infrastructure.judgement.JudgementSpecParser;
import com.ynu.marginx.infrastructure.netlist.FileNetlistRepository;
import com.ynu.marginx.infrastructure.netlist.NetlistParser;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.FileMarginResultRepository;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.infrastructure.simulator.SimulatorSelector;
import com.ynu.marginx.presentation.view.DetailView;
import com.ynu.marginx.presentation.view.MarginChartView;
import com.ynu.marginx.presentation.view.ProgressBarView;
import com.ynu.marginx.shared.exception.MarginXException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "marginx",
        description = "Margin analysis for superconducting circuits.",
        mixinStandardHelpOptions = true,
        version = "MarginXJ 0.1.0")
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
        MarginResultRepository results = new FileMarginResultRepository(workingDirectory);

        try {
            // JoSIM when it is installed, JSIM only when it is not: neither ships with MarginXJ.
            // Selecting inside the try keeps a missing simulator on the same one-line error path.
            CircuitSimulator simulator = new SimulatorSelector(
                    properties, new NetlistRenderer(), new ProcessExecutor()).select();
            OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());

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
            return report(calculateMargins(netlist, spec, searcher(selected, evaluator), results));
        } catch (MarginXException e) {
            System.err.println(" ERROR : " + e.getMessage());
            return 1;
        }
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
        // The C++ tool measures once with the exhaustive search and re-measures with the binary one.
        MarginTableCalculator initial = measurement(new ExhaustiveMarginSearcher(evaluator));
        MarginTableCalculator refined = measurement(new BinarySearchMarginSearcher(evaluator));
        CriticalMarginMethod method =
                new CriticalMarginMethod(initial, refined, new CriticalElementFinder());
        return new OptimizeCircuitUseCase(method, netlists, results)
                .withCriticalMarginMethod(netlist, spec);
    }

    /** Every re-measurement inside an optimisation loop runs the elements in parallel too. */
    private MarginTableCalculator measurement(MarginSearcher searcher) {
        return (netlist, spec) -> new CalculateMarginUseCase(searcher, MarginResultRepository.NONE)
                .execute(netlist, spec, ProgressListener.NOOP);
    }

    private MarginTable calculateMargins(Netlist netlist, JudgementSpec spec, MarginSearcher searcher,
                                         MarginResultRepository results) {
        System.out.println(" Calculating Margins...");
        return new CalculateMarginUseCase(searcher, results)
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
        System.out.printf(" Stopped after %d trial(s): %s%n", outcome.trials(), explain(outcome.reason()));
        return report(outcome.margins());
    }

    private String explain(OptimizationOutcome.StopReason reason) {
        return switch (reason) {
            case SAME_CRITICAL_ELEMENT -> "the same element came up critical again";
            case CRITICAL_ELEMENT_IS_FIXED -> "the critical element is marked *FIX";
            case NOTHING_TO_OPTIMIZE -> "no element could be measured";
            case TRIALS_EXHAUSTED -> "the trial limit was reached";
        };
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
