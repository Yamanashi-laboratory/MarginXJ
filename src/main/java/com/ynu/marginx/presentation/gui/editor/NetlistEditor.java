package com.ynu.marginx.presentation.gui.editor;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.Netlist;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.layout.BorderPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * A netlist under the editing cursor: line numbers, syntax colouring, the line that failed to
 * parse underlined, and the margin targets marked.
 *
 * <p>Which lines are margin targets is not worked out here. The rule - only a lower-case
 * designator counts - is the least obvious thing about the file format, so the marking comes
 * straight from the elements {@link com.ynu.marginx.infrastructure.netlist.NetlistParser} returned,
 * and shows exactly what the calculation will measure.
 */
public final class NetlistEditor extends BorderPane {

    private static final Duration RESTYLE_DELAY = Duration.ofMillis(400);
    private static final String ERROR_STYLE = "netlist-error";
    private static final String TARGET_STYLE = "netlist-target";

    private final CodeArea codeArea = new CodeArea();
    private final NetlistHighlighter highlighter = new NetlistHighlighter();
    private final Set<Integer> targetLines = new HashSet<>();

    private int errorLine = -1;
    private Consumer<String> onSettled = text -> { };

    public NetlistEditor() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("netlist-editor");
        codeArea.multiPlainChanges()
                .successionEnds(RESTYLE_DELAY)
                .subscribe(change -> {
                    restyle();
                    onSettled.accept(codeArea.getText());
                });
        setCenter(new VirtualizedScrollPane<>(codeArea));
    }

    /** Called once typing has stopped, which is when it is worth parsing the text again. */
    public void setOnSettled(Consumer<String> listener) {
        this.onSettled = listener == null ? text -> { } : listener;
    }

    public void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
        codeArea.getUndoManager().forgetHistory();
        restyle();
        codeArea.moveTo(0);
        codeArea.requestFollowCaret();
    }

    public String getText() {
        return codeArea.getText();
    }

    /** Marks the outcome of a parse: which line failed, and which lines will be measured. */
    public void showParseResult(NetlistValidator.NetlistResult result) {
        errorLine = result.line().orElse(-1);
        targetLines.clear();
        if (result.valid()) {
            for (CircuitElement element : result.netlist().elements()) {
                targetLines.add(element.lineNumber());
            }
        }
        restyle();
    }

    /** Puts the caret on a line and scrolls to it, for the element list to jump to a definition. */
    public void goToLine(int lineNumber) {
        int line = Math.max(0, Math.min(lineNumber, codeArea.getParagraphs().size() - 1));
        codeArea.moveTo(line, 0);
        codeArea.requestFollowCaret();
        codeArea.selectLine();
    }

    public CodeArea area() {
        return codeArea;
    }

    Set<Integer> markedTargetLines() {
        return Set.copyOf(targetLines);
    }

    private void restyle() {
        StyleSpans<Collection<String>> spans = spans(codeArea.getText());
        if (spans.length() == codeArea.getLength()) {
            codeArea.setStyleSpans(0, spans);
        }
    }

    /**
     * The syntax colour of each line, with the target and error marks layered on top of it. They
     * are extra classes rather than replacements so a target line keeps its element colour and
     * only gains the marking.
     */
    private StyleSpans<Collection<String>> spans(String text) {
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                spans.add(List.of(NetlistHighlighter.PLAIN), 1);
            }
            spans.add(stylesFor(index, lines[index]), lines[index].length());
        }
        return spans.create();
    }

    private Collection<String> stylesFor(int lineIndex, String line) {
        List<String> styles = new ArrayList<>(3);
        styles.add(highlighter.styleOf(line));
        if (targetLines.contains(lineIndex)) {
            styles.add(TARGET_STYLE);
        }
        if (lineIndex == errorLine) {
            styles.add(ERROR_STYLE);
        }
        return styles;
    }
}
