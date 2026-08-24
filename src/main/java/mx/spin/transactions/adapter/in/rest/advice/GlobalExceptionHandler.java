package mx.spin.transactions.adapter.in.rest.advice;

import mx.spin.transactions.domain.exception.*;
import mx.spin.transactions.domain.policy.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://spin.mx/errors/";

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleViolationException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation", "Business rule violation", e.getMessage());
        problem.setProperty("violations", e.violations().stream()
                .map(v -> Map.of("code", v.code(), "message", v.message())).toList());
        return problem;
    }

    @ExceptionHandler(ProviderRejectedException.class)
    public ProblemDetail handleProviderRejected(ProviderRejectedException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "provider-rejected", "Transaction rejected by provider", e.getMessage());
        problem.setProperty("transactionId", e.transactionId().toString());
        problem.setProperty("failureCode", e.reason().code());
        return problem;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "transaction-not-found", "Transaction not found", e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ProblemDetail handleInvalidState(InvalidStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "invalid-state", "Invalid state transition", e.getMessage());
    }

    /** VOs del dominio y parseos fallidos: el request es sintácticamente inválido. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "validation-failed", "Validation failed", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Última red de seguridad: nunca se filtran stack traces al cliente. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error", "Internal server error", "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(BASE_TYPE + type));
        problem.setTitle(title);
        return problem;
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ProblemDetail handleDuplicate(DuplicateRequestException e) {
        return problem(HttpStatus.CONFLICT, "duplicate-request", "Duplicate request", e.getMessage());
    }
}