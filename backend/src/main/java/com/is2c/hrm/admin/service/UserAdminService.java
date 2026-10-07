package com.is2c.hrm.admin.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.api.EmployeePlacementLookup;
import com.is2c.hrm.org.api.EmployeeScope;
import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.Department;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.RefreshTokenRepository;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.RoleRepository;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.hrm.security.service.PasswordPolicy;
import com.is2c.hrm.security.service.PrincipalLoader;
import com.is2c.hrm.security.service.SecurityEvent;
import com.is2c.hrm.security.service.SecurityEvents;

/** FR-004 user management with FR-006 scope assignment and privilege-escalation guards. */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final PrincipalLoader principals;
    private final DataScopeService scope;
    private final OrgDirectory org;
    private final ObjectProvider<EmployeePlacementLookup> placements;
    private final AuditService audit;
    private final SecurityEvents securityEvents;
    private final Clock clock;

    public UserAdminService(AppUserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder, PrincipalLoader principals, DataScopeService scope, OrgDirectory org,
            ObjectProvider<EmployeePlacementLookup> placements, AuditService audit, SecurityEvents securityEvents,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.principals = principals;
        this.scope = scope;
        this.org = org;
        this.placements = placements;
        this.audit = audit;
        this.securityEvents = securityEvents;
        this.clock = clock;
    }

    public record UserData(String username, String email, String displayName, String password, UUID employeeId,
            Set<String> roles, ScopeType scopeType, Set<UUID> scopeCompanyIds, Set<UUID> scopeBranchIds,
            Set<UUID> scopeDepartmentIds, Boolean active, Long version) {
    }

    /** Audit snapshot without secrets. */
    record UserSnapshot(String username, String email, String displayName, UUID employeeId, Set<String> roles,
            ScopeType scopeType, Set<ScopeItem> scopeItems, boolean active) {
        static UserSnapshot of(AppUser u) {
            return new UserSnapshot(u.getUsername(), u.getEmail(), u.getDisplayName(), u.getEmployeeId(),
                    roleCodes(u), u.getScopeType(), Set.copyOf(u.getScopeItems()), u.isActive());
        }
    }

    @Transactional(readOnly = true)
    public Page<AppUser> list(String q, Boolean active, Pageable pageable) {
        AuthenticatedUser actor = CurrentUser.get();
        EmployeeScope actorScope = scope.forUser(actor);
        String needle = q == null ? null : q.toLowerCase(Locale.ROOT).trim();
        List<AppUser> filtered = users.findAll().stream()
                .filter(u -> active == null || u.isActive() == active)
                .filter(u -> needle == null || u.getUsername().toLowerCase(Locale.ROOT).contains(needle)
                        || u.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                        || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(needle)))
                .filter(u -> canManage(actorScope, u))
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase(Locale.ROOT)))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public AppUser get(UUID id) {
        return manageable(id);
    }

    @Transactional
    public AppUser create(UserData d) {
        validateBasics(d, null);
        PasswordPolicy.check("password", d.password());
        AppUser u = new AppUser(d.username().trim(), d.displayName().trim(), encoder.encode(d.password()));
        u.setMustChangePassword(true);
        apply(u, d);
        users.save(u);
        audit.record(AuditEntry.of("CREATE", "USER", u.getId()).after(UserSnapshot.of(u)));
        return u;
    }

    @Transactional
    public AppUser update(UUID id, UserData d) {
        AppUser u = manageable(id);
        Versions.check(u, d.version());
        validateBasics(d, u);
        UserSnapshot before = UserSnapshot.of(u);
        boolean wasSuperAdmin = roleCodes(u).contains(Role.SUPER_ADMIN) && u.isActive();
        u.setUsername(d.username().trim());
        apply(u, d);
        boolean isSuperAdmin = roleCodes(u).contains(Role.SUPER_ADMIN) && u.isActive();
        if (wasSuperAdmin && !isSuperAdmin) {
            guardLastSuperAdmin(u.getId());
        }
        if (!u.isActive()) {
            refreshTokens.revokeAllForUser(u.getId(), clock.instant());
        }
        principals.evict(u.getId());
        audit.record(AuditEntry.of("UPDATE", "USER", id).before(before).after(UserSnapshot.of(u)));
        return u;
    }

    @Transactional
    public AppUser setActive(UUID id, boolean active) {
        AppUser u = manageable(id);
        if (!active && u.isActive() && roleCodes(u).contains(Role.SUPER_ADMIN)) {
            guardLastSuperAdmin(u.getId());
        }
        u.setActive(active);
        if (!active) {
            refreshTokens.revokeAllForUser(u.getId(), clock.instant());
        }
        principals.evict(u.getId());
        audit.record(AuditEntry.of(active ? "ACTIVATE" : "DEACTIVATE", "USER", id));
        return u;
    }

    @Transactional
    public void resetPassword(UUID id, String temporaryPassword) {
        AppUser u = manageable(id);
        PasswordPolicy.check("temporaryPassword", temporaryPassword);
        u.setPasswordHash(encoder.encode(temporaryPassword));
        u.setMustChangePassword(true);
        refreshTokens.revokeAllForUser(u.getId(), clock.instant());
        principals.evict(u.getId());
        securityEvents.publish(SecurityEvent.Type.PASSWORD_RESET, u.getId(), u.getUsername(),
                "by " + CurrentUser.get().username());
    }

    // ---------------------------------------------------------------------

    private AppUser manageable(UUID id) {
        AppUser u = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (!canManage(scope.forUser(CurrentUser.get()), u)) {
            throw new NotFoundException("User", id);
        }
        return u;
    }

    /** Global admins manage everyone; others only users confined to companies they fully cover. */
    private boolean canManage(EmployeeScope actor, AppUser target) {
        if (actor.all()) {
            return true;
        }
        if (target.getScopeType() == ScopeType.ALL) {
            return false;
        }
        Set<UUID> companies = companiesOf(target.getScopeType(), target.getScopeItems(), target.getEmployeeId());
        return !companies.isEmpty() && companies.stream().allMatch(actor::coversWholeCompany);
    }

    private Set<UUID> companiesOf(ScopeType type, Set<ScopeItem> items, UUID employeeId) {
        Set<UUID> companies = new HashSet<>();
        for (ScopeItem item : items) {
            switch (item.type()) {
                case COMPANY -> companies.add(item.itemId());
                case BRANCH -> org.branch(item.itemId()).map(Branch::getCompanyId).ifPresent(companies::add);
                case DEPARTMENT -> org.department(item.itemId()).map(Department::getCompanyId).ifPresent(companies::add);
            }
        }
        if (employeeId != null) {
            placement(employeeId).map(EmployeePlacement::companyId).ifPresent(companies::add);
        }
        return companies;
    }

    private void apply(AppUser u, UserData d) {
        u.setEmail(d.email());
        u.setDisplayName(d.displayName().trim());
        u.setEmployeeId(d.employeeId());
        u.setActive(d.active() == null || d.active());
        u.setScopeType(d.scopeType());
        u.getScopeItems().clear();
        u.getScopeItems().addAll(scopeItems(d));
        u.getRoles().clear();
        u.getRoles().addAll(roles.findByCodeIn(d.roles()));
    }

    private void validateBasics(UserData d, AppUser existing) {
        AuthenticatedUser actor = CurrentUser.get();
        EmployeeScope actorScope = scope.forUser(actor);
        List<FieldError> errors = new ArrayList<>();
        if (d.username() == null || !d.username().trim().matches("[A-Za-z0-9._@-]{3,100}")) {
            errors.add(new FieldError("username", "must be 3-100 letters, digits or . _ @ -"));
        } else {
            users.findByUsernameIgnoreCase(d.username().trim())
                    .filter(o -> existing == null || !o.getId().equals(existing.getId()))
                    .ifPresent(o -> errors.add(new FieldError("username", "username already exists")));
        }
        if (d.displayName() == null || d.displayName().isBlank()) {
            errors.add(new FieldError("displayName", "must not be blank"));
        }
        if (d.email() != null && !d.email().isBlank() && !d.email().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.add(new FieldError("email", "must be a valid email address"));
        }
        if (d.roles() == null || d.roles().isEmpty()) {
            errors.add(new FieldError("roles", "at least one role is required"));
        } else {
            List<Role> found = roles.findByCodeIn(d.roles());
            if (found.size() != d.roles().size()) {
                errors.add(new FieldError("roles", "unknown role"));
            }
            for (Role r : found) {
                if (Role.SUPER_ADMIN.equals(r.getCode()) && !actor.hasRole(Role.SUPER_ADMIN)) {
                    throw new ForbiddenException("Only a Super Administrator can grant the Super Administrator role");
                }
                if (!actor.permissions().containsAll(r.getPermissions())) {
                    throw new ForbiddenException("You cannot grant role " + r.getCode()
                            + " because it has permissions you do not hold");
                }
            }
        }
        if (d.scopeType() == null) {
            errors.add(new FieldError("scopeType", "must not be null"));
        } else {
            validateScope(d, actorScope, errors);
        }
        if (d.employeeId() != null) {
            if (placements.stream().findAny().isPresent() && placement(d.employeeId()).isEmpty()) {
                errors.add(new FieldError("employeeId", "employee not found"));
            } else if (!actorScope.all() && placement(d.employeeId())
                    .map(p -> !actorScope.coversWholeCompany(p.companyId())).orElse(true)) {
                errors.add(new FieldError("employeeId", "employee is outside your scope"));
            }
            users.findByEmployeeId(d.employeeId()).filter(o -> existing == null || !o.getId().equals(existing.getId()))
                    .ifPresent(o -> errors.add(new FieldError("employeeId", "employee is already linked to another user")));
        }
        if (d.scopeType() == ScopeType.OWN && d.employeeId() == null) {
            errors.add(new FieldError("employeeId", "OWN scope requires a linked employee"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private void validateScope(UserData d, EmployeeScope actorScope, List<FieldError> errors) {
        if (d.scopeType() == ScopeType.ALL && !actorScope.all()) {
            throw new ForbiddenException("Only users with global scope can grant global scope");
        }
        Set<UUID> ids = switch (d.scopeType()) {
            case COMPANY -> nullSafe(d.scopeCompanyIds());
            case BRANCH -> nullSafe(d.scopeBranchIds());
            case DEPARTMENT -> nullSafe(d.scopeDepartmentIds());
            case ALL, OWN -> Set.of();
        };
        if (Set.of(ScopeType.COMPANY, ScopeType.BRANCH, ScopeType.DEPARTMENT).contains(d.scopeType()) && ids.isEmpty()) {
            errors.add(new FieldError("scopeType", d.scopeType() + " scope requires at least one item"));
            return;
        }
        for (UUID id : ids) {
            UUID company = switch (d.scopeType()) {
                case COMPANY -> {
                    try {
                        yield org.company(id).getId();
                    } catch (NotFoundException e) {
                        yield null;
                    }
                }
                case BRANCH -> org.branch(id).map(Branch::getCompanyId).orElse(null);
                case DEPARTMENT -> org.department(id).map(Department::getCompanyId).orElse(null);
                default -> null;
            };
            if (company == null) {
                errors.add(new FieldError("scope", "unknown " + d.scopeType().name().toLowerCase(Locale.ROOT) + " " + id));
            } else if (!actorScope.coversWholeCompany(company)) {
                errors.add(new FieldError("scope", "scope item " + id + " is outside your scope"));
            }
        }
    }

    private static Set<ScopeItem> scopeItems(UserData d) {
        return switch (d.scopeType()) {
            case COMPANY -> toItems(ScopeItemType.COMPANY, d.scopeCompanyIds());
            case BRANCH -> toItems(ScopeItemType.BRANCH, d.scopeBranchIds());
            case DEPARTMENT -> toItems(ScopeItemType.DEPARTMENT, d.scopeDepartmentIds());
            case ALL, OWN -> Set.of();
        };
    }

    private static Set<ScopeItem> toItems(ScopeItemType type, Set<UUID> ids) {
        Set<ScopeItem> items = new HashSet<>();
        nullSafe(ids).forEach(id -> items.add(new ScopeItem(type, id)));
        return items;
    }

    private void guardLastSuperAdmin(UUID excludingUserId) {
        boolean another = users.findActiveByRoleCode(Role.SUPER_ADMIN).stream()
                .anyMatch(u -> !u.getId().equals(excludingUserId));
        if (!another) {
            throw new ValidationException("The last active Super Administrator cannot be removed or deactivated");
        }
    }

    private java.util.Optional<EmployeePlacement> placement(UUID employeeId) {
        return placements.stream().flatMap(p -> p.find(employeeId).stream()).findFirst();
    }

    static Set<String> roleCodes(AppUser u) {
        Set<String> codes = new HashSet<>();
        u.getRoles().forEach(r -> codes.add(r.getCode()));
        return codes;
    }

    private static <T> Set<T> nullSafe(Set<T> s) {
        return s == null ? Set.of() : s;
    }
}
