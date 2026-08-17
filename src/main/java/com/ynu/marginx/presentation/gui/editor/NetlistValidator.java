package com.ynu.marginx.presentation.gui.editor;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.infrastructure.judgement.JudgementSpecParser;
import com.ynu.marginx.infrastructure.netlist.NetlistParser;
import com.ynu.marginx.shared.exception.CircuitFileException;
import java.util.List;
import java.util.OptionalInt;

/**
 * Checks what is in the editor by handing it to the very parsers the calculation uses.
 *
 * <p>There is deliberately no syntax knowledge here. An editor judging a netlist by rules of its
 * own could accept something the calculation then rejects, which is the one failure this whole
 * arrangement exists to rule out: if {@link NetlistParser} reads it, the run reads it the same way,
 * because it is the same code.
 */
public final class NetlistValidator {

    /** What the parser made of a netlist: either the netlist, or a message and where it failed. */
    public record NetlistResult(Netlist netlist, String message, OptionalInt line) {

        public boolean valid() {
            return netlist != null;
        }
    }

    /** The judgement file has no line numbers to report: its parser works in blocks, not lines. */
    public record JudgementResult(boolean valid, String message) {
    }

    private final NetlistParser netlists = new NetlistParser();
    private final JudgementSpecParser judgements = new JudgementSpecParser();

    public NetlistResult validateNetlist(String baseName, String text) {
        try {
            Netlist netlist = netlists.parse(baseName, lines(text));
            return new NetlistResult(netlist,
                    netlist.elementCount() + " margin targets", OptionalInt.empty());
        } catch (CircuitFileException e) {
            return new NetlistResult(null, e.getMessage(), e.line());
        } catch (RuntimeException e) {
            // A malformed number arrives as a NumberFormatException out of the unit parsing, with
            // no line attached. Reporting it plainly beats guessing which line it came from.
            return new NetlistResult(null, message(e), OptionalInt.empty());
        }
    }

    public JudgementResult validateJudgement(String text) {
        try {
            JudgementSpec spec = judgements.parse(lines(text));
            return new JudgementResult(true, spec.totalRules() + " judgement rules");
        } catch (RuntimeException e) {
            return new JudgementResult(false, message(e));
        }
    }

    private List<String> lines(String text) {
        return List.of(text.split("\n", -1));
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
