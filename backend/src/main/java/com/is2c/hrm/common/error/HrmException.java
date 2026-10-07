package com.is2c.hrm.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/** Base for business exceptions mapped to problem+json (NFR-009). */
public class HrmException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<FieldError> errors;

    public HrmException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public HrmException(HttpStatus status, String code, String message, List<FieldError> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
