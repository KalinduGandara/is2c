package com.is2c.hrm.org.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.is2c.hrm.org.domain.Department;

/** Test access to department descendant expansion. */
public final class DataScopeServiceAccess {

    private DataScopeServiceAccess() {
    }

    public static Set<UUID> descendants(UUID root, JdbcTemplate jdbc) {
        List<Department> all = jdbc.query("SELECT id, company_id, parent_id, code FROM department WHERE deleted_at IS NULL",
                (rs, i) -> {
                    Department d = new Department(rs.getObject("company_id", UUID.class), rs.getString("code"));
                    setId(d, rs.getObject("id", UUID.class));
                    d.setParentId(rs.getObject("parent_id", UUID.class));
                    return d;
                });
        return DataScopeService.withDescendants(Set.of(root), all);
    }

    private static void setId(Department d, UUID id) {
        try {
            var f = com.is2c.hrm.common.domain.BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(d, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
