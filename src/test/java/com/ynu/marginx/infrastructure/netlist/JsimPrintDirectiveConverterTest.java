package com.ynu.marginx.infrastructure.netlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsimPrintDirectiveConverterTest {

    private final JsimPrintDirectiveConverter converter = new JsimPrintDirectiveConverter();

    @Test
    void turnsASubcircuitReferenceAround() {
        assertThat(converter.convert(List.of(".print phase X1.b01")))
                .containsExactly(".print phase b01_X1");
    }

    @Test
    void leavesAPlainReferenceAlone() {
        assertThat(converter.convert(List.of(".print phase b01")))
                .containsExactly(".print phase b01");
    }

    @Test
    void acceptsTheUpperCaseSpelling() {
        assertThat(converter.convert(List.of(".PRINT PHASE X1.B01")))
                .containsExactly(".print PHASE B01_X1");
    }

    @Test
    void keepsEveryOtherLineVerbatim() {
        List<String> netlist = List.of("* Example", "L01 4 3 2p", ".tran 1ps 1000ps 0ps", ".FILE CIRCUIT.CSV");

        assertThat(converter.convert(netlist)).isEqualTo(netlist);
    }
}
