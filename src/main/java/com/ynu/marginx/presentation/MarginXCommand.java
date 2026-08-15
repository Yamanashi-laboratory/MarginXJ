package com.ynu.marginx.presentation;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.application.JudgeOperationUseCase;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementOutcome;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.JudgementSpecRepository;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.CriticalElementFinder;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.judgement.FileJudgementSpecRepository;
import com.ynu.marginx.infrastructure.judgement.JudgementSpecParser;
import com.ynu.marginx.infrastructure.netlist.FileNetlistRepository;
import com.ynu.marginx.infrastructure.netlist.NetlistParser;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.FileMarginResultRepository;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.infrastructure.simulator.JosimSimulator;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
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
        CircuitSimulator simulator = new JosimSimulator(
                properties, new NetlistRenderer(), new JosimCsvReader(), new ProcessExecutor());
        OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());

        try {
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
            return report(calculateMargins(netlist, spec, searcher(selected, evaluator), results));
        } catch (MarginXException e) {
            System.err.println(" ERROR : " + e.getMessage());
            return 1;
        }
    }

    private MarginSearcher searcher(OperationMode selected, OperationEvaluator evaluator) {
        return selected == OperationMode.MARGIN_BINARY
                ? new BinarySearchMarginSearcher(evaluator)
                : new ExhaustiveMarginSearcher(evaluator);
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
