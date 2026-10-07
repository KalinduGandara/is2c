package com.is2c.hrm.attendance.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.is2c.hrm.attendance.api.AttendanceEventsIngested;
import com.is2c.hrm.roster.api.ScheduleChangedEvent;

/** Reprocesses affected days when punches arrive or schedules change (FR-048, FR-039). Runs in the caller's transaction. */
@Component
public class AttendanceTriggers {

    private final AttendanceProcessor processor;

    public AttendanceTriggers(AttendanceProcessor processor) {
        this.processor = processor;
    }

    @EventListener
    public void on(AttendanceEventsIngested event) {
        LocalDate min = event.datesByEmployee().values().stream().flatMap(Collection::stream)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate max = event.datesByEmployee().values().stream().flatMap(Collection::stream)
                .max(Comparator.naturalOrder()).orElse(null);
        if (min != null) {
            // A punch after midnight may belong to the previous day's night shift.
            processor.process(event.companyId(), Set.copyOf(event.datesByEmployee().keySet()), min.minusDays(1), max);
        }
    }

    @EventListener
    public void on(ScheduleChangedEvent event) {
        processor.process(event.companyId(), event.employeeIds(), event.from(), event.to());
    }
}
