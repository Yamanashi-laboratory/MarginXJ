package com.ynu.marginx.presentation.gui.export;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.shared.exception.MarginXException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javax.imageio.ImageIO;

/**
 * Writes what is on screen to a file the user picked.
 *
 * <p>The CSV is the same three columns file_out.cpp wrote, with the same provenance comments the
 * result files carry, so an exported file and a generated one can be read by the same thing. The
 * PNG is a snapshot of the chart, which is what the matplotlib script used to be for.
 */
public final class ResultExporter {

    private static final double SNAPSHOT_SCALE = 2;

    private ResultExporter() {
    }

    public static void writeCsv(Path target, List<ElementMargin> rows, String simulator, String executable) {
        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add("# simulator: " + simulator);
        lines.add("# executable: " + executable);
        for (ElementMargin row : rows) {
            lines.add(String.format(Locale.ROOT, "%s,%.4f,%.4f",
                    row.displayName(), row.margin().lowerPercent(), row.margin().upperPercent()));
        }
        try {
            Files.write(target, lines);
        } catch (IOException e) {
            throw new MarginXException("Cannot write " + target, e);
        }
    }

    /** Call on the JavaFX thread: taking a snapshot touches the scene graph. */
    public static void writePng(Path target, Node chart) {
        SnapshotParameters parameters = new SnapshotParameters();
        // Twice the size, because a screen-resolution chart is not much use in a report. White,
        // because the default is transparent and a transparent plot looks empty in most viewers.
        parameters.setTransform(javafx.scene.transform.Transform.scale(SNAPSHOT_SCALE, SNAPSHOT_SCALE));
        parameters.setFill(Color.WHITE);

        WritableImage image = chart.snapshot(parameters, null);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", target.toFile());
        } catch (IOException e) {
            throw new MarginXException("Cannot write " + target, e);
        }
    }
}
