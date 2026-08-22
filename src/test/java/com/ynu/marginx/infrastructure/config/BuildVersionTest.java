package com.ynu.marginx.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.presentation.cli.BuildVersionProvider;
import org.junit.jupiter.api.Test;

/**
 * The version has to come from the build, not from a constant somebody has to remember to bump.
 *
 * <p>What these guard is the wiring between Gradle and the resource: a substitution that silently
 * stops happening would leave every copy reporting the same thing again, which is the state this
 * replaced.
 */
class BuildVersionTest {

    @Test
    void reportsTheVersionTheBuildWasGiven() {
        // The tests run against the processed resources, so a filtered value is what should arrive.
        assertThat(BuildVersion.version())
                .isNotBlank()
                .isNotEqualTo("unknown")
                .matches("\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    void neverShowsAnUnsubstitutedPlaceholder() {
        assertThat(BuildVersion.version()).doesNotContain("$").doesNotContain("{");
    }

    @Test
    void isWhatTheCommandLineWouldPrint() {
        assertThat(new BuildVersionProvider().getVersion())
                .containsExactly("MarginXJ " + BuildVersion.version());
    }
}
