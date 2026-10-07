package com.is2c.hrm.common.domain;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a set of weekdays as CSV, e.g. "SATURDAY,SUNDAY" (portable, NFR-005). */
@Converter
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return "";
        }
        return days.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String value) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (value != null && !value.isBlank()) {
            Arrays.stream(value.split(",")).map(String::trim).map(DayOfWeek::valueOf).forEach(days::add);
        }
        return days;
    }
}
