package com.ynu.marginx.presentation.gui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.testsupport.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The colouring rules, checked without a display. The netlist used is the same reference deck the
 * parser tests read, so the two cannot disagree about what a line is.
 */
class NetlistHighlighterTest {

    private final NetlistHighlighter highlighter = new NetlistHighlighter();

    @Test
    void coloursEveryElementSymbolTheParserKnowsAbout() {
        // Derived from ElementType rather than a list of its own, so a new element type cannot be
        // added to the parser and left uncoloured here.
        for (ElementType type : ElementType.values()) {
            String line = type.directiveKey().toLowerCase() + "01   1   2   1.0";
            assertThat(highlighter.styleOf(line))
                    .as("element line for %s", type)
                    .isEqualTo(NetlistHighlighter.ELEMENT);
        }
    }

    @Test
    void recognisesTheJunctionInductanceBeforeThePlainJunction() {
        // BI has to be matched before B, or every bi... line reads as a junction.
        assertThat(highlighter.designatorOf("bi01   1   2   jmod area=2")).isEqualToIgnoringCase("BI");
        assertThat(highlighter.designatorOf("b01    1   2   jmod area=2")).isEqualToIgnoringCase("B");
    }

    @Test
    void coloursDotCommands() {
        assertThat(highlighter.styleOf(".tran 1ps 1000ps 0ps")).isEqualTo(NetlistHighlighter.DOT_COMMAND);
        assertThat(highlighter.styleOf(".print phase b01")).isEqualTo(NetlistHighlighter.DOT_COMMAND);
        assertThat(highlighter.styleOf(".model jmod jj(rtype=1)")).isEqualTo(NetlistHighlighter.DOT_COMMAND);
        assertThat(highlighter.styleOf(".subckt half 1 2")).isEqualTo(NetlistHighlighter.DOT_COMMAND);
        assertThat(highlighter.styleOf(".FILE test.csv")).isEqualTo(NetlistHighlighter.DOT_COMMAND);
    }

    @Test
    void separatesMarginXDirectivesFromOrdinaryComments() {
        // Both start with a star; only the first sort changes what gets measured.
        for (String directive : List.of("*MIN=1", "*MAX=3", "*FIX", "*SYN 1", "*LMIN=0.5", "*BMAX=3")) {
            assertThat(highlighter.styleOf(directive))
                    .as("directive %s", directive)
                    .isEqualTo(NetlistHighlighter.DIRECTIVE);
        }
        for (String comment : List.of("* Example JTL Basic", "*** section ***", "* MIN is not a directive here")) {
            assertThat(highlighter.styleOf(comment))
                    .as("comment %s", comment)
                    .isEqualTo(NetlistHighlighter.COMMENT);
        }
    }

    @Test
    void coloursAShuntDirectiveThatRidesOnAResistorLine() {
        assertThat(highlighter.styleOf("RS1     3     7     5.23ohm  *Bc=1"))
                .isEqualTo(NetlistHighlighter.DIRECTIVE);
        assertThat(highlighter.styleOf("rs2     3     7     5.23ohm  *calc=2"))
                .isEqualTo(NetlistHighlighter.DIRECTIVE);
    }

    @Test
    void spansCoverTheWholeTextExactly() {
        // RichTextFX rejects spans that do not add up to the length of the document.
        String text = String.join("\n", Fixtures.circuitLines());

        assertThat(highlighter.computeHighlighting(text).length()).isEqualTo(text.length());
    }

    @Test
    void classifiesTheReferenceNetlist() {
        List<String> lines = Fixtures.circuitLines();

        assertThat(styleOfFirstLineStartingWith(lines, "* Example")).isEqualTo(NetlistHighlighter.COMMENT);
        assertThat(styleOfFirstLineStartingWith(lines, ".model")).isEqualTo(NetlistHighlighter.DOT_COMMAND);
        assertThat(styleOfFirstLineStartingWith(lines, "b01")).isEqualTo(NetlistHighlighter.ELEMENT);
        assertThat(styleOfFirstLineStartingWith(lines, "L01")).isEqualTo(NetlistHighlighter.ELEMENT);
        assertThat(styleOfFirstLineStartingWith(lines, "*FIX")).isEqualTo(NetlistHighlighter.DIRECTIVE);
        assertThat(styleOfFirstLineStartingWith(lines, "RS1")).isEqualTo(NetlistHighlighter.DIRECTIVE);
    }

    @Test
    void leavesBlankLinesAlone() {
        assertThat(highlighter.styleOf("")).isEqualTo(NetlistHighlighter.PLAIN);
        assertThat(highlighter.styleOf("   ")).isEqualTo(NetlistHighlighter.PLAIN);
    }

    private String styleOfFirstLineStartingWith(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .map(highlighter::styleOf)
                .orElseThrow(() -> new AssertionError("no line starting with " + prefix));
    }
}
