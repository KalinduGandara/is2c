package com.is2c.hrm.employee.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.CustomFieldDef;
import com.is2c.hrm.employee.domain.CustomFieldDef.FieldType;
import com.is2c.hrm.employee.domain.CustomFieldDefRepository;
import com.is2c.hrm.employee.domain.CustomFieldValue;
import com.is2c.hrm.employee.domain.CustomFieldValueRepository;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.org.service.DataScopeService;

/** FR-019 custom employee fields: definitions, value validation and storage. */
@Service
public class CustomFieldService implements EmployeeExtension, EmployeeDetailContributor {

    private static final int MAX_TEXT = 1000;

    private final CustomFieldDefRepository defs;
    private final CustomFieldValueRepository values;
    private final DataScopeService scope;
    private final AuditService audit;

    public CustomFieldService(CustomFieldDefRepository defs, CustomFieldValueRepository values, DataScopeService scope,
            AuditService audit) {
        this.defs = defs;
        this.values = values;
        this.scope = scope;
        this.audit = audit;
    }

    public record FieldData(String key, String label, FieldType type, List<String> options, Boolean required,
            Boolean active, Integer displayOrder, Long version) {
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDef> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return defs.findByCompanyIdOrderByDisplayOrderAscKeyAsc(companyId);
    }

    @Transactional
    public CustomFieldDef create(UUID companyId, FieldData d) {
        scope.assertCompanyWritable(companyId);
        List<FieldError> errors = new ArrayList<>();
        if (d.key() == null || !d.key().matches("[a-z][a-z0-9_]{0,49}")) {
            errors.add(new FieldError("key", "must start with a lower-case letter and contain only a-z, 0-9, _"));
        } else if (defs.findByCompanyIdAndKey(companyId, d.key()).isPresent()) {
            errors.add(new FieldError("key", "key already exists"));
        }
        if (d.type() == null) {
            errors.add(new FieldError("type", "must not be null"));
        }
        validateCommon(d, d.type(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        CustomFieldDef f = new CustomFieldDef(companyId, d.key(), d.type());
        apply(f, d);
        defs.save(f);
        audit.record(AuditEntry.of("CREATE", "CUSTOM_FIELD", f.getId()).company(companyId).after(d));
        return f;
    }

    @Transactional
    public CustomFieldDef update(UUID id, FieldData d) {
        CustomFieldDef f = defs.findById(id).orElseThrow(() -> new NotFoundException("Custom field", id));
        scope.assertCompanyWritable(f.getCompanyId());
        Versions.check(f, d.version());
        List<FieldError> errors = new ArrayList<>();
        if (d.type() != null && d.type() != f.getType()) {
            errors.add(new FieldError("type", "the type of a field cannot be changed"));
        }
        validateCommon(d, f.getType(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        apply(f, d);
        audit.record(AuditEntry.of("UPDATE", "CUSTOM_FIELD", id).company(f.getCompanyId()).after(d));
        return f;
    }

    private static void validateCommon(FieldData d, FieldType type, List<FieldError> errors) {
        if (d.label() == null || d.label().isBlank()) {
            errors.add(new FieldError("label", "must not be blank"));
        }
        if (type == FieldType.SELECT && (d.options() == null || d.options().stream().noneMatch(o -> !o.isBlank()))) {
            errors.add(new FieldError("options", "SELECT fields need at least one option"));
        }
        if (d.options() != null && d.options().stream().anyMatch(o -> o.contains("|"))) {
            errors.add(new FieldError("options", "options cannot contain '|'"));
        }
    }

    private static void apply(CustomFieldDef f, FieldData d) {
        f.setLabel(d.label().trim());
        f.setOptions(f.getType() == FieldType.SELECT ? d.options() : null);
        f.setRequired(Boolean.TRUE.equals(d.required()));
        f.setActive(d.active() == null || d.active());
        f.setDisplayOrder(d.displayOrder() == null ? 0 : d.displayOrder());
    }

    // ---------------------------------------------------------------- EmployeeExtension

    @Override
    public void validate(EmployeeData data, List<FieldError> errors) {
        if (data.companyId() == null) {
            return;
        }
        Map<String, String> provided = data.customFields() == null ? Map.of() : data.customFields();
        Map<String, CustomFieldDef> active = new HashMap<>();
        defs.findByCompanyIdOrderByDisplayOrderAscKeyAsc(data.companyId()).stream().filter(CustomFieldDef::isActive)
                .forEach(f -> active.put(f.getKey(), f));
        for (String key : provided.keySet()) {
            if (!active.containsKey(key)) {
                errors.add(new FieldError("customFields." + key, "unknown custom field"));
            }
        }
        for (CustomFieldDef f : active.values()) {
            String raw = provided.get(f.getKey());
            String field = "customFields." + f.getKey();
            if (raw == null || raw.isBlank()) {
                if (f.isRequired()) {
                    errors.add(new FieldError(field, f.getLabel() + " is required"));
                }
                continue;
            }
            String problem = typeProblem(f, raw.trim());
            if (problem != null) {
                errors.add(new FieldError(field, problem));
            }
        }
    }

    @Override
    public void saved(Employee employee, EmployeeData data) {
        if (data.customFields() == null) {
            return;
        }
        Map<UUID, CustomFieldValue> existing = new HashMap<>();
        values.findByIdEmployeeId(employee.getId()).forEach(v -> existing.put(v.getId().fieldDefId(), v));
        for (CustomFieldDef f : defs.findByCompanyIdOrderByDisplayOrderAscKeyAsc(employee.getCompanyId())) {
            if (!f.isActive()) {
                continue; // values of inactive fields are retained untouched
            }
            String raw = data.customFields().get(f.getKey());
            CustomFieldValue current = existing.get(f.getId());
            if (raw == null || raw.isBlank()) {
                if (current != null) {
                    values.delete(current);
                }
            } else if (current != null) {
                current.setValue(normalize(f, raw.trim()));
            } else {
                values.save(new CustomFieldValue(employee.getId(), f.getId(), normalize(f, raw.trim())));
            }
        }
    }

    // ---------------------------------------------------------------- EmployeeDetailContributor

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> customFields(Employee employee) {
        Map<UUID, CustomFieldDef> active = new HashMap<>();
        defs.findByCompanyIdOrderByDisplayOrderAscKeyAsc(employee.getCompanyId()).stream()
                .filter(CustomFieldDef::isActive).forEach(f -> active.put(f.getId(), f));
        Map<String, String> result = new LinkedHashMap<>();
        values.findByIdEmployeeId(employee.getId()).stream()
                .filter(v -> active.containsKey(v.getId().fieldDefId()))
                .forEach(v -> result.put(active.get(v.getId().fieldDefId()).getKey(), v.getValue()));
        return result;
    }

    static String typeProblem(CustomFieldDef f, String value) {
        return switch (f.getType()) {
            case TEXT -> value.length() > MAX_TEXT ? "must be at most " + MAX_TEXT + " characters" : null;
            case NUMBER -> {
                try {
                    new BigDecimal(value);
                    yield null;
                } catch (NumberFormatException e) {
                    yield "must be a number";
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(value);
                    yield null;
                } catch (DateTimeParseException e) {
                    yield "must be a date (YYYY-MM-DD)";
                }
            }
            case BOOLEAN -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false") ? null
                    : "must be true or false";
            case SELECT -> f.options().contains(value) ? null : "must be one of " + f.options();
        };
    }

    private static String normalize(CustomFieldDef f, String value) {
        return switch (f.getType()) {
            case BOOLEAN -> value.toLowerCase(java.util.Locale.ROOT);
            case NUMBER -> new BigDecimal(value).stripTrailingZeros().toPlainString();
            default -> value;
        };
    }
}
