package com.is2c.hrm.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends HrmException {

    public NotFoundException(String entity, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
