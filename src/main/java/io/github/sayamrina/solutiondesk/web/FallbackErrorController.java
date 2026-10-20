package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.logging.IncidentLogger;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Catches what the controller advice cannot see, e.g. errors while a template renders. These are still
 * logged as SD-SYS-500 with the original request ID, so no failure goes unrecorded.
 */
@Controller
public class FallbackErrorController implements ErrorController {

    @RequestMapping("/error")
    public String error(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object requestId = request.getAttribute("requestId");
        if (status instanceof Integer code && code == 404) {
            response.setStatus(404);
            return "not-found";
        }
        Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (requestId != null) MDC.put("requestId", requestId.toString());
        MDC.put("path", String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
        try {
            IncidentLogger.unexpected(ex == null ? new IllegalStateException("error status " + status) : ex, Map.of());
        } finally {
            MDC.remove("requestId");
            MDC.remove("path");
        }
        response.setStatus(500);
        model.addAttribute("code", ErrorCode.UNEXPECTED);
        model.addAttribute("message", "Something went wrong on our side. Our support team can find the details with the request ID below.");
        model.addAttribute("requestId", requestId);
        return "error";
    }
}
