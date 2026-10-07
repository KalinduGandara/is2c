package com.is2c.hrm.common.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a small set of UUIDs as CSV (portable, NFR-005). */
@Converter
public class UuidSetConverter implements AttributeConverter<Set<UUID>, String> {

    @Override
    public String convertToDatabaseColumn(Set<UUID> ids) {
        return ids == null ? "" : ids.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<UUID> convertToEntityAttribute(String value) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            Arrays.stream(value.split(",")).map(String::trim).map(UUID::fromString).forEach(ids::add);
        }
        return ids;
    }
}
