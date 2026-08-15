package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.shared.exception.MarginXException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class CalculateMarginUseCase {

    private final MarginSearcher searcher;
    private final MarginResultRepository resultRepository;

    public CalculateMarginUseCase(MarginSearcher searcher, MarginResultRepository resultRepository) {
        this.searcher = searcher;
        this.resultRepository = resultRepository;
    }

    public MarginTable execute(Netlist netlist, JudgementSpec spec, ProgressListener listener) {
        int total = netlist.elementCount();
        listener.started(total);
        AtomicInteger completed = new AtomicInteger();

        List<Margin> margins;
        try (ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(total, Runtime.getRuntime().availableProcessors()))) {
            List<CompletableFuture<Margin>> futures = new ArrayList<>(total);
            for (int index = 0; index < total; index++) {
                int elementIndex = index;
                futures.add(CompletableFuture.supplyAsync(
                        () -> searcher.search(netlist, elementIndex, spec), pool)
                        .whenComplete((margin, error) -> listener.advanced(completed.incrementAndGet(), total)));
            }
            margins = join(futures);
        }
        listener.finished();

        List<ElementMargin> entries = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            entries.add(new ElementMargin(netlist.element(index), margins.get(index)));
        }
        MarginTable table = new MarginTable(entries);
        resultRepository.save(netlist.baseName(), table);
        return table;
    }

    private List<Margin> join(List<CompletableFuture<Margin>> futures) {
        List<Margin> margins = new ArrayList<>(futures.size());
        for (CompletableFuture<Margin> future : futures) {
            try {
                margins.add(future.get());
            } catch (ExecutionException e) {
                throw e.getCause() instanceof MarginXException cause
                        ? cause
                        : new MarginXException("Margin search failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MarginXException("Interrupted while calculating margins", e);
            }
        }
        return margins;
    }
}
