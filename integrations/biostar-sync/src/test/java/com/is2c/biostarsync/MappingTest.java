package com.is2c.biostarsync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MappingTest {

    private static Settings settings() {
        Settings s = new Settings();
        s.companyId = "c1";
        s.branchId = "b1";
        s.designationId = "d1";
        s.categoryId = "k1";
        s.joinDate = LocalDate.of(2024, 1, 1);
        s.groupDepartments = Map.of("2985", "dep-finance");
        return s;
    }

    @Test
    @DisplayName("Employee number and badge are the BioStar user id; placement comes from defaults and group mapping")
    void employee() {
        Map<String, Object> e = Mapping.employee(
                new BiostarClient.User("1643", "Ravindu Nilanka Edmun", "2985", "Finance", false), settings());
        assertThat(e).containsEntry("employeeNumber", "1643").containsEntry("badgeId", "1643")
                .containsEntry("firstName", "Ravindu Nilanka").containsEntry("lastName", "Edmun")
                .containsEntry("departmentId", "dep-finance").containsEntry("companyId", "c1")
                .containsEntry("branchId", "b1").containsEntry("designationId", "d1").containsEntry("categoryId", "k1")
                .containsEntry("joinDate", "2024-01-01");
    }

    @Test
    @DisplayName("Single-word and empty names still give HRM both a first and a last name")
    void names() {
        assertThat(Mapping.splitName("Madonna", "7")).containsExactly("Madonna", "Madonna");
        assertThat(Mapping.splitName("  ", "7")).containsExactly("BioStar user", "7");
        assertThat(Mapping.splitName(" Kumi   Miranda ", "7")).containsExactly("Kumi", "Miranda");
    }

    @Test
    @DisplayName("tna_key maps to IN/OUT by configuration; anything else is UNKNOWN")
    void direction() {
        Settings s = settings();
        assertThat(Mapping.direction("1", s)).isEqualTo("IN");
        assertThat(Mapping.direction("2", s)).isEqualTo("OUT");
        assertThat(Mapping.direction("3", s)).isEqualTo("UNKNOWN");
        assertThat(Mapping.direction(null, s)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Event external id is device:index (BioStar ids repeat every month); UTC datetime is unchanged")
    void event() {
        Map<String, Object> m = Mapping.event(new BiostarClient.Event("540086297:237709", "2025-04-28T03:19:57.00Z",
                "1643", "2985", "540086297", "1"), settings());
        assertThat(m).containsEntry("externalId", "540086297:237709").containsEntry("employeeIdentifier", "1643")
                .containsEntry("timestamp", "2025-04-28T03:19:57.00Z").containsEntry("deviceId", "540086297")
                .containsEntry("direction", "IN");
    }
}
