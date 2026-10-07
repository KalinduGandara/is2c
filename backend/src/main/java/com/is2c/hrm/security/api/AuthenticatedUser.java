package com.is2c.hrm.security.api;

import java.util.Set;
import java.util.UUID;

/** The principal of an authenticated API request. */
public record AuthenticatedUser(
        UUID id,
        String username,
        String displayName,
        UUID employeeId,
        boolean mustChangePassword,
        Set<String> roles,
        Set<String> permissions,
        ScopeType scopeType,
        Set<UUID> scopeCompanyIds,
        Set<UUID> scopeBranchIds,
        Set<UUID> scopeDepartmentIds) {

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }
}
