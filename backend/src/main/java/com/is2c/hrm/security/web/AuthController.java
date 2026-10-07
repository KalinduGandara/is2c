package com.is2c.hrm.security.web;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.service.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {
        static TokenResponse of(AuthService.Tokens t) {
            return new TokenResponse(t.accessToken(), t.refreshToken(), t.expiresIn(), "Bearer");
        }
    }

    public record MeResponse(UUID id, String username, String displayName, UUID employeeId,
            boolean mustChangePassword, Set<String> roles, Set<String> permissions, ScopeType scopeType,
            Set<UUID> scopeCompanyIds, Set<UUID> scopeBranchIds, Set<UUID> scopeDepartmentIds) {
        static MeResponse of(AuthenticatedUser u) {
            return new MeResponse(u.id(), u.username(), u.displayName(), u.employeeId(), u.mustChangePassword(),
                    u.roles(), u.permissions(), u.scopeType(), u.scopeCompanyIds(), u.scopeBranchIds(),
                    u.scopeDepartmentIds());
        }
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.of(auth.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.of(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request) {
        auth.logout(request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me() {
        return MeResponse.of(CurrentUser.get());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(CurrentUser.get().id(), request.currentPassword(), request.newPassword());
    }
}
