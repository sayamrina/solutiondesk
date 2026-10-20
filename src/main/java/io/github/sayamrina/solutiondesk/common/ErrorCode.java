package io.github.sayamrina.solutiondesk.common;

import org.springframework.http.HttpStatus;

/**
 * Every error the platform can report. The code is shown to the learner, written to the log and
 * linked to a knowledge base article, so support can go from a screenshot to the cause in one step.
 */
public enum ErrorCode {
    IMPORT_NOT_WELL_FORMED("SD-IMP-001", HttpStatus.BAD_REQUEST, "kb-imp-001", Team.SUPPORT),
    IMPORT_MISSING_FIELD("SD-IMP-002", HttpStatus.BAD_REQUEST, "kb-imp-002", Team.SUPPORT),
    IMPORT_BROKEN_REFERENCE("SD-IMP-003", HttpStatus.BAD_REQUEST, "kb-imp-003", Team.SUPPORT),
    IMPORT_ENCODING("SD-IMP-004", HttpStatus.BAD_REQUEST, "kb-imp-004", Team.SUPPORT),
    IMPORT_DOCTYPE("SD-IMP-005", HttpStatus.BAD_REQUEST, "kb-imp-005", Team.SUPPORT),
    IMPORT_DUPLICATE("SD-IMP-006", HttpStatus.CONFLICT, "kb-imp-006", Team.SUPPORT),
    UPLOAD_TOO_LARGE("SD-UPL-001", HttpStatus.PAYLOAD_TOO_LARGE, "kb-upl-001", Team.HOSTING),
    QUIZ_NOT_ENROLLED("SD-QZ-001", HttpStatus.FORBIDDEN, "kb-qz-001", Team.SUPPORT),
    QUIZ_ENROLLMENT_EXPIRED("SD-QZ-002", HttpStatus.CONFLICT, "kb-qz-002", Team.SUPPORT),
    QUIZ_INCOMPLETE("SD-QZ-003", HttpStatus.BAD_REQUEST, "kb-qz-003", Team.SUPPORT),
    COURSE_NOT_VISIBLE("SD-CRS-001", HttpStatus.NOT_FOUND, "kb-crs-001", Team.SUPPORT),
    UNEXPECTED("SD-SYS-500", HttpStatus.INTERNAL_SERVER_ERROR, "kb-sys-500", Team.DEVELOPMENT);

    /** Who usually fixes the cause behind an error code. */
    public enum Team { SUPPORT, HOSTING, DEVELOPMENT }

    private final String code;
    private final HttpStatus status;
    private final String kbSlug;
    private final Team owner;

    ErrorCode(String code, HttpStatus status, String kbSlug, Team owner) {
        this.code = code;
        this.status = status;
        this.kbSlug = kbSlug;
        this.owner = owner;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public String kbSlug() { return kbSlug; }
    public Team owner() { return owner; }
    /** Key of the user-facing message in messages*.properties. */
    public String messageKey() { return "error." + code; }

    public static ErrorCode fromCode(String code) {
        for (ErrorCode c : values()) {
            if (c.code.equals(code)) return c;
        }
        throw new IllegalArgumentException("Unknown error code " + code);
    }
}
