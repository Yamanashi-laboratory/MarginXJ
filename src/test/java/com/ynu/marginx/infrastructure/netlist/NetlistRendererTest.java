package com.ynu.marginx.infrastructure.netlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.testsupport.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetlistRendererTest {

    private final NetlistParser parser = new NetlistParser();
    private final NetlistRenderer renderer = new NetlistRenderer();

    @Test
    void rewritesTheJunctionLineAndItsShuntResistor() {
        Netlist netlist = parser.parse("test_JTL.cir", Fixtures.circuitLines());

        List<String> rendered = renderer.render(netlist, "CIRCUIT.CSV");

        assertThat(rendered.get(4)).isEqualTo("b01     3     7  jmod area=2.160     ");
        assertThat(rendered.get(5)).startsWith("RS01     3     7");
        assertThat(rendered.get(5)).endsWith("ohm  *Bc=1.000");
    }

    @Test
    void redirectsTheOutputFileDirective() {
        Netlist netlist = parser.parse("test_JTL.cir", Fixtures.circuitLines());

        List<String> rendered = renderer.render(netlist, "CIRCUIT.CSV");

        assertThat(rendered).contains(".FILE CIRCUIT.CSV");
        assertThat(rendered).noneMatch(line -> line.contains("test.csv"));
    }

    @Test
    void writesTheSweptValueIntoTheTargetLine() {
        Netlist netlist = parser.parse("test_JTL.cir", Fixtures.circuitLines());

        List<String> rendered = renderer.render(netlist.withElementValue(0, 3.0), "CIRCUIT.CSV");

        assertThat(rendered.get(4)).contains("area=3.000");
    }

    @Test
    void keepsUntouchedLinesVerbatim() {
        List<String> source = Fixtures.circuitLines();
        Netlist netlist = parser.parse("test_JTL.cir", source);

        List<String> rendered = renderer.render(netlist, "CIRCUIT.CSV");

        assertThat(rendered.get(2)).isEqualTo(source.get(2));
        assertThat(rendered.get(8)).isEqualTo(source.get(8));
    }
}
