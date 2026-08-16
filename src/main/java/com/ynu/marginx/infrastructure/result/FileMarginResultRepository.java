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

    /**
     * Which simulator produced a result file, recorded at the top of it. JoSIM and JSIM do not
     * share a numerical engine, so a file of margins is only meaningful next to the name of the
     * engine that measured them.
     */
    public record Provenance(String simulator, String executable) {

        public List<String> asComments() {
            return List.of("# simulator: " + simulator, "# executable: " + executable);
        }
    }

    private final Path outputDirectory;
    private final Provenance provenance;

    public FileMarginResultRepository(Path outputDirectory) {
        this(outputDirectory, null);
    }

    public FileMarginResultRepository(Path outputDirectory, Provenance provenance) {
        this.outputDirectory = outputDirectory;
        this.provenance = provenance;
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

        write(outputDirectory.resolve("result_" + baseName + ".csv"), withProvenance(csv));
        write(outputDirectory.resolve("result.csv"), withProvenance(csv));
        write(outputDirectory.resolve("result_" + baseName + ".txt"), withProvenance(detail));
    }

    private String formatDetail(ElementMargin entry) {
        return String.format(Locale.ROOT,
                "%6s : %6.3f %-5s Lower: %6.3f   Upper: %6.3f   Median: %6.3f  %7.2f %% ~ %6.2f %%",
                entry.displayName(), entry.element().value(), entry.element().unit(),
                entry.lowerValue(), entry.upperValue(), entry.medianValue(),
                entry.margin().lowerPercent(), entry.margin().upperPercent());
    }

    /** Comment lines, so a reader that skips # still sees the same columns it always did. */
    private List<String> withProvenance(List<String> lines) {
        if (provenance == null) {
            return lines;
        }
        List<String> annotated = new ArrayList<>(provenance.asComments());
        annotated.addAll(lines);
        return annotated;
    }

    private void write(Path target, List<String> lines) {
        try {
            Files.write(target, lines);
        } catch (IOException e) {
            throw new MarginXException("Cannot write " + target, e);
        }
    }
}
