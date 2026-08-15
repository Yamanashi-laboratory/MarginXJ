package com.ynu.marginx.infrastructure.result;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.shared.exception.MarginXException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FileMarginResultRepository implements MarginResultRepository {

    private final Path outputDirectory;

    public FileMarginResultRepository(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public void save(String baseName, MarginTable table) {
        List<String> csv = new ArrayList<>(table.size());
        List<String> detail = new ArrayList<>(table.size());
        for (ElementMargin entry : table.entries()) {
            csv.add(String.format(Locale.ROOT, "%s,%.4f,%.4f",
                    entry.displayName(), entry.margin().lowerPercent(), entry.margin().upperPercent()));
            detail.add(formatDetail(entry));
        }

        write(outputDirectory.resolve("result_" + baseName + ".csv"), csv);
        write(outputDirectory.resolve("result.csv"), csv);
        write(outputDirectory.resolve("result_" + baseName + ".txt"), detail);
    }

    private String formatDetail(ElementMargin entry) {
        return String.format(Locale.ROOT,
                "%6s : %6.3f %-5s Lower: %6.3f   Upper: %6.3f   Median: %6.3f  %7.2f %% ~ %6.2f %%",
                entry.displayName(), entry.element().value(), entry.element().unit(),
                entry.lowerValue(), entry.upperValue(), entry.medianValue(),
                entry.margin().lowerPercent(), entry.margin().upperPercent());
    }

    private void write(Path target, List<String> lines) {
        try {
            Files.write(target, lines);
        } catch (IOException e) {
            throw new MarginXException("Cannot write " + target, e);
        }
    }
}
