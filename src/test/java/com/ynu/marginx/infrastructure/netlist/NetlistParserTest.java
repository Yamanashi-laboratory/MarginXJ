package com.ynu.marginx.infrastructure.netlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.circuit.ShuntMode;
import com.ynu.marginx.shared.exception.CircuitFileException;
import com.ynu.marginx.testsupport.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetlistParserTest {

    private final NetlistParser parser = new NetlistParser();

    @Test
    void picksUpOnlyLowerCaseDesignatorsAsMarginTargets() {
        Netlist netlist = parser.parse("test_JTL.cir", Fixtures.circuitLines());

        assertThat(netlist.elements())
                .extracting(CircuitElement::name)
                .containsExactly("B01", "B02", "RB01", "RB02", "IB01");
    }

    @Test
    void ordersElementsByType() {
        Netlist netlist = parser.parse("test_JTL.cir", Fixtures.circuitLines());

        assertThat(netlist.elements())
                .extracting(CircuitElement::type)
                .containsExactly(ElementType.JUNCTION, ElementType.JUNCTION,
                        ElementType.RESISTOR, ElementType.RESISTOR, ElementType.CURRENT_SOURCE);
    }

    @Test
    void readsJunctionAreaModelAndShunt() {
        CircuitElement junction = parser.parse("test_JTL.cir", Fixtures.circuitLines()).element(0);

        assertThat(junction.value()).isEqualTo(2.16);
        assertThat(junction.junctionModel()).isEqualTo("jmod");
        assertThat(junction.node1()).isEqualTo("3");
        assertThat(junction.node2()).isEqualTo("7");
        assertThat(junction.shunt().mode()).isEqualTo(ShuntMode.BC);
        assertThat(junction.shunt().parameter()).isEqualTo(1.0);
        assertThat(junction.range().min()).isEqualTo(0.1);
        assertThat(junction.range().max()).isEqualTo(3.0);
    }

    @Test
    void readsBiasSourceAmplitudeWithItsUnitPrefix() {
        CircuitElement bias = parser.parse("test_JTL.cir", Fixtures.circuitLines()).element(4);

        assertThat(bias.value()).isEqualTo(280.0);
        assertThat(bias.unit()).isEqualTo("uA");
        assertThat(bias.fixed()).isTrue();
    }

    @Test
    void readsResistorWithoutItsOhmSuffix() {
        CircuitElement resistor = parser.parse("test_JTL.cir", Fixtures.circuitLines()).element(2);

        assertThat(resistor.name()).isEqualTo("RB01");
        assertThat(resistor.value()).isEqualTo(5.23);
        assertThat(resistor.unit()).isEqualTo("ohm");
    }

    @Test
    void appliesRangeDirectivesToLaterElementsOnly() {
        List<String> lines = List.of(
                "*RMIN = 1",
                "r01   1   2   5.0ohm",
                "*RMAX = 20",
                "r02   2   3   6.0ohm",
                ".FILE out.csv");

        Netlist netlist = parser.parse("ranges.cir", lines);

        assertThat(netlist.element(0).range().min()).isEqualTo(1);
        assertThat(netlist.element(0).range().max()).isEqualTo(1000);
        assertThat(netlist.element(1).range().max()).isEqualTo(20);
    }

    @Test
    void perElementDirectivesOverrideTheTypeRange() {
        List<String> lines = List.of(
                "l01   1   2   2.5pH",
                "*MIN = 1.0",
                "*MAX = 4.0",
                "*SYN = 2",
                ".FILE out.csv");

        CircuitElement inductor = parser.parse("directives.cir", lines).element(0);

        assertThat(inductor.value()).isEqualTo(2.5);
        assertThat(inductor.unit()).isEqualTo("pH");
        assertThat(inductor.range().min()).isEqualTo(1.0);
        assertThat(inductor.range().max()).isEqualTo(4.0);
        assertThat(inductor.synchronizationGroup()).isEqualTo(2);
    }

    @Test
    void rejectsACircuitWithoutTheFileDirective() {
        assertThatThrownBy(() -> parser.parse("broken.cir", List.of("l01   1   2   2.5pH")))
                .isInstanceOf(CircuitFileException.class)
                .hasMessageContaining(".FILE");
    }

    @Test
    void rejectsACircuitWithoutAnyTarget() {
        assertThatThrownBy(() -> parser.parse("empty.cir", List.of("* comment", ".FILE out.csv")))
                .isInstanceOf(CircuitFileException.class)
                .hasMessageContaining("No target element");
    }
}
