package com.ynu.marginx.infrastructure.judgement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.domain.model.judge.JudgementRule;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.shared.exception.JudgementFileException;
import com.ynu.marginx.testsupport.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgementSpecParserTest {

    private final JudgementSpecParser parser = new JudgementSpecParser();

    @Test
    void groupsWindowsUnderTheirElementDesignator() {
        JudgementSpec spec = parser.parse(Fixtures.judgementLines());

        assertThat(spec.elementCount()).isEqualTo(2);
        assertThat(spec.totalRules()).isEqualTo(4);
        assertThat(spec.rulesFor(0)).containsExactly(
                new JudgementRule(300, 400, 1, false),
                new JudgementRule(600, 700, 3, false));
    }

    @Test
    void readsTheOptionalAntiFlag() {
        JudgementSpec spec = parser.parse(List.of("B01", "300 400 1 1"));

        assertThat(spec.rulesFor(0).get(0).inverted()).isTrue();
    }

    @Test
    void rejectsAWindowBeforeAnyDesignator() {
        assertThatThrownBy(() -> parser.parse(List.of("300 400 1")))
                .isInstanceOf(JudgementFileException.class);
    }

    @Test
    void rejectsAFileWithoutAnyDesignator() {
        assertThatThrownBy(() -> parser.parse(List.of("", "   ")))
                .isInstanceOf(JudgementFileException.class);
    }
}
