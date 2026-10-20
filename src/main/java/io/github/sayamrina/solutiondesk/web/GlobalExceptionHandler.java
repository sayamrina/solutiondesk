package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import io.github.sayamrina.solutiondesk.logging.IncidentLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Every failure ends here: it is logged once with its error code, and the user sees a friendly page
 * with the error code and request ID to quote when contacting support.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(SolutionDeskException.class)
    public String known(SolutionDeskException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        IncidentLogger.known(ex, Map.of());
        return render(ex.errorCode(), ex.args(), request, response, model);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request, HttpServletResponse response,
                           Model model) {
        SolutionDeskException known = new SolutionDeskException(ErrorCode.UPLOAD_TOO_LARGE,
                "upload rejected by the server limit of " + ex.getMaxUploadSize() / 1024 + " KB", "?", 256);
        IncidentLogger.known(known, Map.of());
        return render(ErrorCode.UPLOAD_TOO_LARGE, known.args(), request, response, model);
    }

    @ExceptionHandler(IllegalStateException.class)
    public String rule(IllegalStateException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(422);
        model.addAttribute("ruleMessage", ex.getMessage());
        model.addAttribute("requestId", request.getAttribute("requestId"));
        model.addAttribute("back", request.getHeader("Referer"));
        return "rule-violation";
    }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public String notFound(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(404);
        model.addAttribute("requestId", request.getAttribute("requestId"));
        return "not-found";
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        IncidentLogger.unexpected(ex, Map.of());
        return render(ErrorCode.UNEXPECTED, new Object[0], request, response, model);
    }

    private String render(ErrorCode code, Object[] args, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(code.status().value());
        model.addAttribute("code", code);
        model.addAttribute("message", messages.getMessage(code.messageKey(), args, request.getLocale()));
        model.addAttribute("requestId", request.getAttribute("requestId"));
        return "error";
    }
}
