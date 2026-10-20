package io.github.sayamrina.solutiondesk.learning;

/** Turns the number of correct answers into a percentage and a pass/fail result. */
public final class ScoreCalculator {

    public static final int PASS_MARK = 80;

    private ScoreCalculator() { }

    public static int percent(int correct, int total) {
        if (total <= 0) return 0;
        // multiply first: (correct / total) * 100 is 0 for every result below 100 % in integer arithmetic (SD-105)
        return correct * 100 / total;
    }

    public static boolean passed(int percent) {
        return percent >= PASS_MARK;
    }
}
