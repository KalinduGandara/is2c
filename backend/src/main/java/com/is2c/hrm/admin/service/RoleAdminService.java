package com.is2c.hrm.admin.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.RoleRepository;
import com.is2c.hrm.security.service.PrincipalLoader;

/** FR-005 role and permission-matrix administration. */
@Service
public class RoleAdminService {

    private final RoleRepository roles;
    private final PrincipalLoader principals;
    private final AuditService audit;

    public RoleAdminService(RoleRepository roles, PrincipalLoader principals, AuditService audit) {
        this.roles = roles;
        this.principals = principals;
        this.audit = audit;
    }

    public record RoleData(String code, String name, String description, Set<String> permissions, Long version) {
    }

    record RoleSnapshot(String code, String name, String description, Set<String> permissions) {
        static RoleSnapshot of(Role r) {
            return new RoleSnapshot(r.getCode(), r.getName(), r.getDescription(), Set.copyOf(r.getPermissions()));
        }
    }

    @Transactional(readOnly = true)
    public List<Role> list() {
        return roles.findAll().stream().sorted(Comparator.comparing(Role::isSystem).reversed()
                .thenComparing(Role::getCode)).toList();
    }

    @Transactional(readOnly = true)
    public Role get(UUID id) {
        return roles.findById(id).orElseThrow(() -> new NotFoundException("Role", id));
    }

    @Transactional
    public Role create(RoleData d) {
        validate(d);
        if (d.code() == null || !d.code().matches("[A-Z][A-Z0-9_]{1,49}")) {
            throw new ValidationException("code", "must be 2-50 upper-case letters, digits or '_'");
        }
        if (roles.findByCode(d.code()).isPresent()) {
            throw new ValidationException("code", "role code already exists");
        }
        Role r = new Role(d.code(), d.name().trim(), d.description());
        r.replacePermissions(d.permissions());
        roles.save(r);
        audit.record(AuditEntry.of("CREATE", "ROLE", r.getId()).after(RoleSnapshot.of(r)));
        return r;
    }

    @Transactional
    public Role update(UUID id, RoleData d) {
        Role r = get(id);
        Versions.check(r, d.version());
        if (Role.SUPER_ADMIN.equals(r.getCode())) {
            throw new ValidationException("The Super Administrator role always has every permission and cannot be edited");
        }
        validate(d);
        RoleSnapshot before = RoleSnapshot.of(r);
        if (!r.isSystem()) {
            r.setName(d.name().trim());
        }
        r.setDescription(d.description());
        r.replacePermissions(d.permissions());
        principals.evictAll();
        audit.record(AuditEntry.of("UPDATE", "ROLE", id).before(before).after(RoleSnapshot.of(r)));
        return r;
    }

    @Transactional
    public void delete(UUID id) {
        Role r = get(id);
        if (r.isSystem()) {
            throw new ValidationException("System roles cannot be deleted");
        }
        if (roles.countUsersWithRole(id) > 0) {
            throw new ValidationException("Role is assigned to users and cannot be deleted");
        }
        roles.delete(r);
        audit.record(AuditEntry.of("DELETE", "ROLE", id).before(RoleSnapshot.of(r)));
    }

    private static void validate(RoleData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "must not be blank"));
        }
        if (d.permissions() == null) {
            errors.add(new FieldError("permissions", "must not be null"));
        } else {
            d.permissions().stream().filter(p -> !Permissions.isValid(p))
                    .forEach(p -> errors.add(new FieldError("permissions", "unknown permission " + p)));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        if (!CurrentUser.get().permissions().containsAll(d.permissions())) {
            throw new ForbiddenException("You cannot grant permissions you do not hold");
        }
    }
}
