package com.is2c.hrm.platform.db;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.domain.Ids;

@Component
@ConditionalOnProperty(name = "hrm.db.vendor", havingValue = "postgresql", matchIfMissing = true)
public class PostgresDatabasePlatform implements DatabasePlatform {

    private final JdbcTemplate jdbc;

    public PostgresDatabasePlatform(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String vendor() {
        return "postgresql";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public long nextSequenceValue(UUID companyId, String key) {
        Long value = jdbc.queryForObject("""
                INSERT INTO number_sequence (id, company_id, sequence_key, next_value)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (company_id, sequence_key)
                DO UPDATE SET next_value = number_sequence.next_value + 1
                RETURNING next_value
                """, Long.class, Ids.newId(), companyId, key);
        if (value == null) {
            throw new IllegalStateException("Sequence update returned no value");
        }
        return value;
    }
}
