package com.is2c.hrm.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.org.api.EmployeeNumberFormat;

class EmployeeNumberFormatTest {

    @Test
    @DisplayName("FR-014 default format renders company code and padded sequence")
    void defaultFormat() {
        EmployeeNumberFormat f = EmployeeNumberFormat.parse("{COMPANY}{SEQ:5}");
        assertThat(f.render("ACME", "CMB", 2026, 42)).isEqualTo("ACME00042");
        assertThat(f.sequenceKey("CMB", 2026)).isEqualTo("EMPNO");
    }

    @Test
    @DisplayName("FR-014 branch and year tokens render and partition the sequence")
    void branchYear() {
        EmployeeNumberFormat f = EmployeeNumberFormat.parse("E/{BRANCH}/{YYYY}/{SEQ:3}");
        assertThat(f.render("ACME", "KDY", 2025, 7)).isEqualTo("E/KDY/2025/007");
        assertThat(f.sequenceKey("KDY", 2025)).isEqualTo("EMPNO:KDY:2025");
    }

    @Test
    @DisplayName("FR-014 formats without exactly one SEQ, with unknown tokens or bad widths are rejected")
    void invalid() {
        assertThat(EmployeeNumberFormat.problems("{COMPANY}")).contains("format must contain exactly one {SEQ:n} token");
        assertThat(EmployeeNumberFormat.problems("{SEQ:3}{SEQ:3}")).isNotEmpty();
        assertThat(EmployeeNumberFormat.problems("{FOO}{SEQ:3}")).contains("unknown token {FOO}");
        assertThat(EmployeeNumberFormat.problems("{SEQ:11}")).contains("{SEQ:n} width must be between 1 and 10");
        assertThat(EmployeeNumberFormat.problems("{SEQ:0}")).isNotEmpty();
        assertThat(EmployeeNumberFormat.problems("X{SEQ:3")).isNotEmpty();
        assertThatThrownBy(() -> EmployeeNumberFormat.parse("")).isInstanceOf(IllegalArgumentException.class);
    }
}
