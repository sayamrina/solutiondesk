package io.github.sayamrina.solutiondesk;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sayamrina.solutiondesk.learning.ScoreCalculator;
import org.junit.jupiter.api.Test;

/** Regression tests for SD-105: results below 100 % were stored as 0 %. */
class ScoreCalculatorTest {

    @Test
    void partialResultsAreNotZero() {
        assertThat(ScoreCalculator.percent(2, 3)).isEqualTo(66);
        assertThat(ScoreCalculator.percent(4, 5)).isEqualTo(80);
        assertThat(ScoreCalculator.percent(1, 2)).isEqualTo(50);
    }

    @Test
    void edgeCases() {
        assertThat(ScoreCalculator.percent(0, 3)).isZero();
        assertThat(ScoreCalculator.percent(3, 3)).isEqualTo(100);
        assertThat(ScoreCalculator.percent(0, 0)).isZero();
    }

    @Test
    void passMarkIsEightyPercent() {
        assertThat(ScoreCalculator.passed(ScoreCalculator.percent(4, 5))).isTrue();
        assertThat(ScoreCalculator.passed(ScoreCalculator.percent(2, 3))).isFalse();
    }
}
