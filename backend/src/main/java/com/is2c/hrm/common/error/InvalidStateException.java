package com.is2c.hrm.common.error;

import org.springframework.http.HttpStatus;

/** Requested transition is not allowed from the current state: 409 INVALID_STATE. */
public class InvalidStateException extends HrmException {

    public InvalidStateException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }
}
