package com.is2c.hrm.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.config.HrmProperties;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.RoleRepository;

/** FR-004: first start creates the Super Administrator from HRM_ADMIN_USERNAME / HRM_ADMIN_PASSWORD. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final HrmProperties props;

    public AdminBootstrap(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder, HrmProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String username = props.admin().username();
        String password = props.admin().password();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("No users exist. Set HRM_ADMIN_USERNAME and HRM_ADMIN_PASSWORD to "
                    + "create the initial Super Administrator.");
        }
        AppUser admin = new AppUser(username.trim(), "Super Administrator", encoder.encode(password));
        admin.setMustChangePassword(true);
        admin.setScopeType(ScopeType.ALL);
        admin.getRoles().add(roles.findByCode(Role.SUPER_ADMIN).orElseThrow());
        users.save(admin);
        log.info("Created initial Super Administrator '{}' (password change required at first login)", username);
    }
}
