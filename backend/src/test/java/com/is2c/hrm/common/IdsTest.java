package com.is2c.hrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.common.domain.Ids;

class IdsTest {

    @Test
    @DisplayName("NFR-015 ids are version-7 UUIDs and time ordered")
    void version7() throws InterruptedException {
        UUID a = Ids.newId();
        Thread.sleep(2);
        UUID b = Ids.newId();
        assertThat(a.version()).isEqualTo(7);
        assertThat(a.variant()).isEqualTo(2);
        assertThat(a.compareTo(b)).isNegative();
    }
}
