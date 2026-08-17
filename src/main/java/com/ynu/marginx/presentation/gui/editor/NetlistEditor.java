package com.ynu.marginx.presentation.gui.editor;

import java.time.Duration;
import java.util.Collection;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import javafx.scene.layout.BorderPane;

/**
 * A netlist under the editing cursor: line numbers down the side and the syntax coloured in.
 *
 * <p>The colouring is recomputed a short moment after typing stops rather than on every keystroke,
 * so a long deck does not restyle itself letter by letter. Validation - actually parsing what was
 * typed - is a separate step and deliberately not done here.
 */
public final class NetlistEditor extends BorderPane {

    private static final Duration RESTYLE_DELAY = Duration.ofMillis(150);

    private final CodeArea codeArea = new CodeArea();
    private final NetlistHighlighter highlighter = new NetlistHighlighter();

    public NetlistEditor() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("netlist-editor");
        codeArea.multiPlainChanges()
                .successionEnds(RESTYLE_DELAY)
                .subscribe(change -> restyle());
        setCenter(new VirtualizedScrollPane<>(codeArea));
    }

    public void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
        restyle();
        codeArea.moveTo(0);
        codeArea.requestFollowCaret();
    }

    public String getText() {
        return codeArea.getText();
    }

    /** Puts the caret on a line and scrolls to it, for the element list to jump to a definition. */
    public void goToLine(int lineNumber) {
        int line = Math.max(0, Math.min(lineNumber, codeArea.getParagraphs().size() - 1));
        codeArea.moveTo(line, 0);
        codeArea.requestFollowCaret();
    }

    public CodeArea area() {
        return codeArea;
    }

    private void restyle() {
        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(codeArea.getText());
        if (spans.length() == codeArea.getLength()) {
            codeArea.setStyleSpans(0, spans);
        }
    }
}
