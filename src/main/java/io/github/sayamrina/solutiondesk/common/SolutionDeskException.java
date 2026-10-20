package io.github.sayamrina.solutiondesk.common;

/** A known, explainable failure. Carries the error code and the facts support needs (the detail). */
public class SolutionDeskException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public SolutionDeskException(ErrorCode errorCode, String detail, Object... args) {
        super(detail);
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode errorCode() { return errorCode; }
    public Object[] args() { return args; }
}
