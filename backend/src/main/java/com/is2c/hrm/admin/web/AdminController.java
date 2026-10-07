package com.is2c.hrm.admin.web;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.admin.service.RoleAdminService;
import com.is2c.hrm.admin.service.UserAdminService;
import com.is2c.hrm.common.web.PageResponse;
import com.is2c.hrm.security.api.PermissionAction;
import com.is2c.hrm.security.api.PermissionModule;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserAdminService users;
    private final RoleAdminService roles;

    public AdminController(UserAdminService users, RoleAdminService roles) {
        this.users = users;
        this.roles = roles;
    }

    public record UserDto(UUID id, String username, String email, String displayName, UUID employeeId, boolean active,
            boolean mustChangePassword, boolean locked, Set<String> roles, ScopeType scopeType,
            Set<UUID> scopeCompanyIds, Set<UUID> scopeBranchIds, Set<UUID> scopeDepartmentIds, Instant lastLoginAt,
            Long version) {
        static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getEmployeeId(),
                    u.isActive(), u.isMustChangePassword(), u.getLockedUntil() != null,
                    u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()), u.getScopeType(),
                    ids(u, ScopeItemType.COMPANY), ids(u, ScopeItemType.BRANCH), ids(u, ScopeItemType.DEPARTMENT),
                    u.getLastLoginAt(), u.getVersion());
        }

        private static Set<UUID> ids(AppUser u, ScopeItemType type) {
            return u.getScopeItems().stream().filter(i -> i.type() == type).map(ScopeItem::itemId)
                    .collect(Collectors.toSet());
        }
    }

    public record RoleDto(UUID id, String code, String name, String description, boolean system,
            Set<String> permissions, Long version) {
        static RoleDto of(Role r) {
            return new RoleDto(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystem(),
                    Set.copyOf(r.getPermissions()), r.getVersion());
        }
    }

    public record ResetPasswordRequest(@NotBlank String temporaryPassword) {
    }

    public record PermissionCatalog(List<String> modules, List<String> actions) {
    }

    // Users (FR-004)

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN:VIEW')")
    public PageResponse<UserDto> listUsers(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, Pageable pageable) {
        return PageResponse.of(users.list(q, active, pageable), UserDto::of);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN:ADD')")
    public UserDto createUser(@RequestBody UserAdminService.UserData body) {
        return UserDto.of(users.create(body));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN:VIEW')")
    public UserDto getUser(@PathVariable UUID id) {
        return UserDto.of(users.get(id));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN:EDIT')")
    public UserDto updateUser(@PathVariable UUID id, @RequestBody UserAdminService.UserData body) {
        return UserDto.of(users.update(id, body));
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAuthority('ADMIN:EDIT')")
    public UserDto deactivate(@PathVariable UUID id) {
        return UserDto.of(users.setActive(id, false));
    }

    @PostMapping("/users/{id}/activate")
    @PreAuthorize("hasAuthority('ADMIN:EDIT')")
    public UserDto activate(@PathVariable UUID id) {
        return UserDto.of(users.setActive(id, true));
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ADMIN:EDIT')")
    public void resetPassword(@PathVariable UUID id, @RequestBody ResetPasswordRequest body) {
        users.resetPassword(id, body.temporaryPassword());
    }

    // Roles (FR-005)

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ADMIN:VIEW')")
    public List<RoleDto> listRoles() {
        return roles.list().stream().map(RoleDto::of).toList();
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ADMIN:VIEW')")
    public RoleDto getRole(@PathVariable UUID id) {
        return RoleDto.of(roles.get(id));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN:CONFIGURE')")
    public RoleDto createRole(@RequestBody RoleAdminService.RoleData body) {
        return RoleDto.of(roles.create(body));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ADMIN:CONFIGURE')")
    public RoleDto updateRole(@PathVariable UUID id, @RequestBody RoleAdminService.RoleData body) {
        return RoleDto.of(roles.update(id, body));
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ADMIN:CONFIGURE')")
    public void deleteRole(@PathVariable UUID id) {
        roles.delete(id);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ADMIN:VIEW')")
    public PermissionCatalog permissions() {
        return new PermissionCatalog(Arrays.stream(PermissionModule.values()).map(Enum::name).toList(),
                Arrays.stream(PermissionAction.values()).map(Enum::name).toList());
    }
}
