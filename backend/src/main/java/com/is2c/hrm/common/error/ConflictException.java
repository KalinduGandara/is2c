package com.is2c.hrm.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends HrmException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
