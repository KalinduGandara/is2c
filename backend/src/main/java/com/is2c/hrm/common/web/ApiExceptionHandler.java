package com.is2c.hrm.common.web;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.HrmException;

import jakarta.validation.ConstraintViolationException;

/** Maps all errors to RFC 9457 problem+json with code and correlationId (NFR-009). Never exposes stack traces. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    @ExceptionHandler(HrmException.class)
    ResponseEntity<ProblemDetail> handle(HrmException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Validation failed", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handle(HandlerMethodValidationException ex) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Validation failed", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handle(ConstraintViolationException ex) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Validation failed", errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handle(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "File exceeds the maximum allowed size",
                List.of(new FieldError("file", "File exceeds the maximum allowed size")));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handle(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK",
                "The record was changed by another user. Reload and try again.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handle(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "CONFLICT", "The change conflicts with existing data", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handle(AccessDeniedException ex) {
        securityLog.warn("Permission denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action",
                List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handle(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handle(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handle(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handle(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the correlation id when reporting it.", List.of());
    }

    public static ProblemDetail body(HttpStatus status, String code, String detail, List<FieldError> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("about:blank"));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("correlationId", CorrelationId.current());
        if (!errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
        return pd;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail,
            List<FieldError> errors) {
        return ResponseEntity.status(status).body(body(status, code, detail, errors));
    }
}
