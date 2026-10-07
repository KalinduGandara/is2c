package com.is2c.hrm.shift;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.shift.api.OtPolicyDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.Session;
import com.is2c.hrm.shift.api.ShiftDefinition.ShiftType;
import com.is2c.hrm.shift.domain.RotationPattern;

class ShiftRulesTest {

    static ShiftDefinition shift(ShiftType type, List<Session> sessions, BreakMode mode, int breakMinutes) {
        return new ShiftDefinition(UUID.randomUUID(), "S", type, sessions, null, null, null, 0, 0, mode, breakMinutes,
                0, null, null, null, null);
    }

    @Test
    @DisplayName("FR-030 night shift sessions end on the next calendar day")
    void nightSessions() {
        ShiftDefinition night = shift(ShiftType.NIGHT, List.of(new Session(LocalTime.of(22, 0), LocalTime.of(6, 0))),
                BreakMode.FIXED, 30);
        var sessions = night.sessionsOn(LocalDate.of(2026, 3, 31));
        assertThat(sessions.get(0).start()).isEqualTo(LocalDateTime.of(2026, 3, 31, 22, 0));
        assertThat(sessions.get(0).end()).isEqualTo(LocalDateTime.of(2026, 4, 1, 6, 0));
        assertThat(night.scheduledMinutes()).isEqualTo(450);
    }

    @Test
    @DisplayName("FR-030 split shift scheduled minutes sum both sessions; punched breaks are not deducted")
    void splitMinutes() {
        ShiftDefinition split = shift(ShiftType.SPLIT, List.of(new Session(LocalTime.of(7, 0), LocalTime.of(11, 0)),
                new Session(LocalTime.of(16, 0), LocalTime.of(20, 0))), BreakMode.PUNCHED, 60);
        assertThat(split.scheduledMinutes()).isEqualTo(480);
        assertThat(split.sessionsOn(LocalDate.of(2026, 1, 1))).hasSize(2);
    }

    @Test
    @DisplayName("FR-032 OT policy applies minimum, round-down and daily cap")
    void otPolicy() {
        OtPolicyDefinition p = new OtPolicyDefinition(null, 30, 15, false, 240, false);
        assertThat(p.apply(29)).isZero();
        assertThat(p.apply(30)).isEqualTo(30);
        assertThat(p.apply(44)).isEqualTo(30);
        assertThat(p.apply(46)).isEqualTo(45);
        assertThat(p.apply(400)).isEqualTo(240);
        assertThat(new OtPolicyDefinition(null, 0, 1, false, null, false).apply(7)).isEqualTo(7);
    }

    @Test
    @DisplayName("FR-031 rotation day = (date - start) mod N, also before the start date")
    void rotationModulo() {
        RotationPattern p = new RotationPattern(UUID.randomUUID());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        p.getDays().addAll(java.util.Arrays.asList(a, a, b, null));
        assertThat(p.shiftOnOffset(0)).isEqualTo(a);
        assertThat(p.shiftOnOffset(2)).isEqualTo(b);
        assertThat(p.shiftOnOffset(3)).isNull();
        assertThat(p.shiftOnOffset(4)).isEqualTo(a);
        assertThat(p.shiftOnOffset(-1)).isNull();
    }
}
