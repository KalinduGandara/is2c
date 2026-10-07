package com.is2c.hrm.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends HrmException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
