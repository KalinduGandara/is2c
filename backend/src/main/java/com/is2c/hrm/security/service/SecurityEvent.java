package com.is2c.hrm.security.service;

import java.util.UUID;

/** Security-relevant event (NFR-008, FR-008); persisted to the audit log by the audit module. */
public record SecurityEvent(Type type, UUID userId, String username, String detail) {

    public enum Type {
        LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, LOGOUT, TOKEN_REUSE_DETECTED,
        PASSWORD_CHANGED, PASSWORD_RESET, PERMISSION_DENIED
    }
}
