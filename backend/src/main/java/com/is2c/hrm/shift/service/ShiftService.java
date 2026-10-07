package com.is2c.hrm.shift.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.shift.api.OtPolicyDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.Session;
import com.is2c.hrm.shift.api.ShiftDefinition.ShiftType;
import com.is2c.hrm.shift.domain.OtPolicy;
import com.is2c.hrm.shift.domain.OtPolicyRepository;
import com.is2c.hrm.shift.domain.Shift;
import com.is2c.hrm.shift.domain.ShiftRepository;

/** FR-030 shift templates and FR-032 overtime policies. */
@Service
public class ShiftService {

    private final ShiftRepository shifts;
    private final OtPolicyRepository policies;
    private final DataScopeService scope;
    private final AuditService audit;

    public ShiftService(ShiftRepository shifts, OtPolicyRepository policies, DataScopeService scope,
            AuditService audit) {
        this.shifts = shifts;
        this.policies = policies;
        this.scope = scope;
        this.audit = audit;
    }

    public record ShiftData(String code, String name, ShiftType type, String color, LocalTime s1Start,
            LocalTime s1End, LocalTime s2Start, LocalTime s2End, LocalTime flexEarliestStart, LocalTime flexLatestEnd,
            Integer requiredMinutes, Integer lateGraceMinutes, Integer earlyGraceMinutes, BreakMode breakMode,
            Integer breakMinutes, Integer breakThresholdMinutes, Integer halfDayThresholdMinutes, UUID otPolicyId,
            Integer windowBeforeMinutes, Integer windowAfterMinutes, Long version) {
    }

    public record OtPolicyData(String name, Integer minOtMinutes, Integer roundingMinutes, Boolean allowPreShiftOt,
            Integer maxOtMinutes, Boolean requiresApproval, Long version) {
    }

    // ---------------------------------------------------------------- read API for other modules

    /** Definitions (with OT policy resolved) of the given shifts. */
    @Transactional(readOnly = true)
    public Map<UUID, ShiftDefinition> definitions(Collection<UUID> shiftIds) {
        Map<UUID, ShiftDefinition> result = new HashMap<>();
        List<Shift> found = shifts.findAllById(shiftIds);
        Map<UUID, OtPolicyDefinition> pols = new HashMap<>();
        policies.findAllById(found.stream().map(Shift::getOtPolicyId).filter(java.util.Objects::nonNull).toList())
                .forEach(p -> pols.put(p.getId(), p.toDefinition()));
        found.forEach(s -> result.put(s.getId(), s.toDefinition(pols.get(s.getOtPolicyId()))));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Shift> forCompany(UUID companyId) {
        return shifts.findByCompanyIdOrderByCode(companyId);
    }

    // ---------------------------------------------------------------- shifts

    @Transactional(readOnly = true)
    public List<Shift> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return shifts.findByCompanyIdOrderByCode(companyId);
    }

    @Transactional(readOnly = true)
    public Shift get(UUID id) {
        Shift s = shifts.findById(id).orElseThrow(() -> new NotFoundException("Shift", id));
        scope.assertCompanyReadable(s.getCompanyId());
        return s;
    }

    @Transactional
    public Shift create(UUID companyId, ShiftData d) {
        scope.assertCompanyWritable(companyId);
        validate(companyId, null, d);
        Shift s = new Shift(companyId);
        apply(s, d);
        shifts.save(s);
        audit.record(AuditEntry.of("CREATE", "SHIFT", s.getId()).company(companyId).after(d));
        return s;
    }

    @Transactional
    public Shift update(UUID id, ShiftData d) {
        Shift s = shifts.findById(id).orElseThrow(() -> new NotFoundException("Shift", id));
        scope.assertCompanyWritable(s.getCompanyId());
        Versions.check(s, d.version());
        validate(s.getCompanyId(), id, d);
        apply(s, d);
        audit.record(AuditEntry.of("UPDATE", "SHIFT", id).company(s.getCompanyId()).after(d));
        return s;
    }

    @Transactional
    public Shift setActive(UUID id, boolean active) {
        Shift s = shifts.findById(id).orElseThrow(() -> new NotFoundException("Shift", id));
        scope.assertCompanyWritable(s.getCompanyId());
        s.setActive(active);
        audit.record(AuditEntry.of(active ? "ACTIVATE" : "DEACTIVATE", "SHIFT", id).company(s.getCompanyId()));
        return s;
    }

    private void apply(Shift s, ShiftData d) {
        s.setCode(d.code().toUpperCase());
        s.setName(d.name().trim());
        s.setType(d.type());
        s.setColor(d.color());
        boolean flexible = d.type() == ShiftType.FLEXIBLE;
        boolean split = d.type() == ShiftType.SPLIT;
        s.setS1Start(flexible ? null : d.s1Start());
        s.setS1End(flexible ? null : d.s1End());
        s.setS2Start(split ? d.s2Start() : null);
        s.setS2End(split ? d.s2End() : null);
        s.setFlexEarliestStart(flexible ? d.flexEarliestStart() : null);
        s.setFlexLatestEnd(flexible ? d.flexLatestEnd() : null);
        s.setRequiredMinutes(flexible ? d.requiredMinutes() : null);
        s.setLateGraceMinutes(orZero(d.lateGraceMinutes()));
        s.setEarlyGraceMinutes(orZero(d.earlyGraceMinutes()));
        s.setBreakMode(d.breakMode() == null ? BreakMode.FIXED : d.breakMode());
        s.setBreakMinutes(orZero(d.breakMinutes()));
        s.setBreakThresholdMinutes(orZero(d.breakThresholdMinutes()));
        s.setHalfDayThresholdMinutes(d.halfDayThresholdMinutes());
        s.setOtPolicyId(d.otPolicyId());
        s.setWindowBeforeMinutes(d.windowBeforeMinutes());
        s.setWindowAfterMinutes(d.windowAfterMinutes());
    }

    private void validate(UUID companyId, UUID selfId, ShiftData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.code() == null || !d.code().matches("[A-Za-z0-9_-]{1,20}")) {
            errors.add(new FieldError("code", "must be 1-20 letters, digits, '-' or '_'"));
        } else {
            shifts.findByCompanyIdAndCodeIgnoreCase(companyId, d.code()).filter(o -> !o.getId().equals(selfId))
                    .ifPresent(o -> errors.add(new FieldError("code", "code already exists in this company")));
        }
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "is required"));
        }
        if (d.color() != null && !d.color().isBlank() && !d.color().matches("#[0-9A-Fa-f]{6}")) {
            errors.add(new FieldError("color", "must be a hex colour like #3366FF"));
        }
        if (d.type() == null) {
            errors.add(new FieldError("type", "is required"));
        } else {
            validateTimes(d, errors);
        }
        nonNegative(errors, "lateGraceMinutes", d.lateGraceMinutes());
        nonNegative(errors, "earlyGraceMinutes", d.earlyGraceMinutes());
        nonNegative(errors, "breakMinutes", d.breakMinutes());
        nonNegative(errors, "breakThresholdMinutes", d.breakThresholdMinutes());
        nonNegative(errors, "halfDayThresholdMinutes", d.halfDayThresholdMinutes());
        window(errors, "windowBeforeMinutes", d.windowBeforeMinutes());
        window(errors, "windowAfterMinutes", d.windowAfterMinutes());
        if (d.otPolicyId() != null && policies.findById(d.otPolicyId())
                .filter(p -> p.getCompanyId().equals(companyId)).isEmpty()) {
            errors.add(new FieldError("otPolicyId", "OT policy must belong to the same company"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private static void validateTimes(ShiftData d, List<FieldError> errors) {
        switch (d.type()) {
            case FLEXIBLE -> {
                if (d.flexEarliestStart() == null) {
                    errors.add(new FieldError("flexEarliestStart", "is required for flexible shifts"));
                }
                if (d.flexLatestEnd() == null) {
                    errors.add(new FieldError("flexLatestEnd", "is required for flexible shifts"));
                }
                if (d.requiredMinutes() == null || d.requiredMinutes() <= 0) {
                    errors.add(new FieldError("requiredMinutes", "must be greater than 0"));
                } else if (d.flexEarliestStart() != null && d.flexLatestEnd() != null) {
                    int window = new Session(d.flexEarliestStart(), d.flexLatestEnd()).minutes();
                    if (d.requiredMinutes() > window) {
                        errors.add(new FieldError("requiredMinutes", "cannot exceed the flexible window"));
                    }
                }
            }
            case SPLIT -> {
                if (d.s1Start() == null || d.s1End() == null || d.s2Start() == null || d.s2End() == null) {
                    errors.add(new FieldError("s2Start", "split shifts need two sessions"));
                } else if (!d.s1End().isAfter(d.s1Start()) || !d.s2End().isAfter(d.s2Start())) {
                    errors.add(new FieldError("s2End", "split sessions must not cross midnight"));
                } else if (d.s2Start().isBefore(d.s1End())) {
                    errors.add(new FieldError("s2Start", "the second session must start after the first ends"));
                }
            }
            default -> {
                if (d.s1Start() == null || d.s1End() == null) {
                    errors.add(new FieldError("s1Start", "start and end times are required"));
                } else {
                    boolean crosses = !d.s1End().isAfter(d.s1Start());
                    if (d.type() == ShiftType.NIGHT && !crosses) {
                        errors.add(new FieldError("s1End", "a night shift must end on the next day"));
                    }
                    if (d.type() == ShiftType.FIXED && crosses) {
                        errors.add(new FieldError("s1End", "a fixed shift cannot cross midnight; use NIGHT or CUSTOM"));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- OT policies

    @Transactional(readOnly = true)
    public List<OtPolicy> policies(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return policies.findByCompanyIdOrderByName(companyId);
    }

    @Transactional
    public OtPolicy createPolicy(UUID companyId, OtPolicyData d) {
        scope.assertCompanyWritable(companyId);
        validatePolicy(d);
        OtPolicy p = new OtPolicy(companyId);
        applyPolicy(p, d);
        policies.save(p);
        audit.record(AuditEntry.of("CREATE", "OT_POLICY", p.getId()).company(companyId).after(d));
        return p;
    }

    @Transactional
    public OtPolicy updatePolicy(UUID id, OtPolicyData d) {
        OtPolicy p = policies.findById(id).orElseThrow(() -> new NotFoundException("OT policy", id));
        scope.assertCompanyWritable(p.getCompanyId());
        Versions.check(p, d.version());
        validatePolicy(d);
        applyPolicy(p, d);
        audit.record(AuditEntry.of("UPDATE", "OT_POLICY", id).company(p.getCompanyId()).after(d));
        return p;
    }

    @Transactional
    public void deletePolicy(UUID id) {
        OtPolicy p = policies.findById(id).orElseThrow(() -> new NotFoundException("OT policy", id));
        scope.assertCompanyWritable(p.getCompanyId());
        if (shifts.countByOtPolicyId(id) > 0) {
            throw new ValidationException("The OT policy is used by shifts and cannot be deleted");
        }
        policies.delete(p);
        audit.record(AuditEntry.of("DELETE", "OT_POLICY", id).company(p.getCompanyId()));
    }

    private static void applyPolicy(OtPolicy p, OtPolicyData d) {
        p.setName(d.name().trim());
        p.setMinOtMinutes(d.minOtMinutes() == null ? 30 : d.minOtMinutes());
        p.setRoundingMinutes(d.roundingMinutes() == null ? 15 : d.roundingMinutes());
        p.setAllowPreShiftOt(Boolean.TRUE.equals(d.allowPreShiftOt()));
        p.setMaxOtMinutes(d.maxOtMinutes());
        p.setRequiresApproval(Boolean.TRUE.equals(d.requiresApproval()));
    }

    private static void validatePolicy(OtPolicyData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "is required"));
        }
        nonNegative(errors, "minOtMinutes", d.minOtMinutes());
        if (d.roundingMinutes() != null && (d.roundingMinutes() < 1 || d.roundingMinutes() > 120)) {
            errors.add(new FieldError("roundingMinutes", "must be between 1 and 120"));
        }
        if (d.maxOtMinutes() != null && d.maxOtMinutes() <= 0) {
            errors.add(new FieldError("maxOtMinutes", "must be greater than 0"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static void nonNegative(List<FieldError> errors, String field, Integer v) {
        if (v != null && v < 0) {
            errors.add(new FieldError(field, "must not be negative"));
        }
    }

    private static void window(List<FieldError> errors, String field, Integer v) {
        if (v != null && (v < 0 || v > 720)) {
            errors.add(new FieldError(field, "must be between 0 and 720"));
        }
    }
}
