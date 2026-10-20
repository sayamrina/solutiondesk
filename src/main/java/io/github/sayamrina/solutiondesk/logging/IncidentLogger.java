package io.github.sayamrina.solutiondesk.logging;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The single place where failures are written to the log, with error code, owner team and the
 * context fields support searches for. Known errors are WARN with a one-line reason; unexpected
 * errors are ERROR with the stack trace.
 */
public final class IncidentLogger {

    private static final Logger log = LoggerFactory.getLogger("solutiondesk.incident");

    private IncidentLogger() { }

    public static void known(SolutionDeskException ex, Map<String, String> context) {
        withContext(ex.errorCode(), context, () -> log.warn("{}", ex.getMessage()));
    }

    public static void unexpected(Throwable ex, Map<String, String> context) {
        withContext(ErrorCode.UNEXPECTED, context, () -> log.error("unexpected error", ex));
    }

    private static void withContext(ErrorCode code, Map<String, String> context, Runnable write) {
        MDC.put("errorCode", code.code());
        MDC.put("team", code.owner().name());
        context.forEach(MDC::put);
        try {
            write.run();
        } finally {
            MDC.remove("errorCode");
            MDC.remove("team");
            context.keySet().forEach(MDC::remove);
        }
    }
}
