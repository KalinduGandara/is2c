package com.is2c.hrm.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/** Business-rule validation failure: 422 VALIDATION_FAILED. */
public class ValidationException extends HrmException {

    public ValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", message);
    }

    public ValidationException(String field, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", message, List.of(new FieldError(field, message)));
    }

    public ValidationException(String message, List<FieldError> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", message, errors);
    }

    protected ValidationException(String code, String message, List<FieldError> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message, errors);
    }
}
