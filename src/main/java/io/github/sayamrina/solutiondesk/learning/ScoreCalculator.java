package io.github.sayamrina.solutiondesk.learning;

/** Turns the number of correct answers into a percentage and a pass/fail result. */
public final class ScoreCalculator {

    public static final int PASS_MARK = 80;

    private ScoreCalculator() { }

    public static int percent(int correct, int total) {
        if (total <= 0) return 0;
        return (correct / total) * 100;
    }

    public static boolean passed(int percent) {
        return percent >= PASS_MARK;
    }
}
