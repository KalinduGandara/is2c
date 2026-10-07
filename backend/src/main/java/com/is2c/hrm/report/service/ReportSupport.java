package com.is2c.hrm.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.is2c.hrm.employee.domain.Employee;

/** Formatting helpers shared by report providers. */
final class ReportSupport {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ReportSupport() {
    }

    static String name(Employee e) {
        return e == null ? "" : e.getFirstName() + " " + e.getLastName();
    }

    static String number(Employee e) {
        return e == null ? "" : e.getEmployeeNumber();
    }

    static String dateTime(LocalDateTime t) {
        return t == null ? null : DATE_TIME.format(t);
    }

    /** Clock time of a punch; prefixed with the date when it is not on the work date (e.g. next-day OUT). */
    static String punch(LocalDateTime t, LocalDate workDate) {
        if (t == null) {
            return null;
        }
        return t.toLocalDate().equals(workDate) ? TIME.format(t) : DATE_TIME.format(t);
    }

    static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    static String label(String text, UUID id) {
        return text == null ? (id == null ? null : id.toString()) : text;
    }
}
