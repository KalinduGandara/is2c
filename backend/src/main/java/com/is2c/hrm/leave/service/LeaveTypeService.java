package com.is2c.hrm.leave.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.leave.domain.LeaveType;
import com.is2c.hrm.leave.domain.LeaveType.AccrualMethod;
import com.is2c.hrm.leave.domain.LeaveTypeRepository;
import com.is2c.hrm.org.api.CompanyCreatedEvent;
import com.is2c.hrm.org.service.DataScopeService;

/** FR-060 leave types (policies) with seeded defaults. */
@Service
public class LeaveTypeService {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final LeaveTypeRepository types;
    private final DataScopeService scope;
    private final AuditService audit;

    public LeaveTypeService(LeaveTypeRepository types, DataScopeService scope, AuditService audit) {
        this.types = types;
        this.scope = scope;
        this.audit = audit;
    }

    public record LeaveTypeData(String code, String name, Boolean paid, BigDecimal annualDays,
            AccrualMethod accrualMethod, Boolean proRata, BigDecimal cfMaxDays, Integer cfExpiryMonths,
            Boolean allowHalfDay, Boolean allowNegative, Boolean unlimited, Boolean countWeeklyOff,
            Boolean countHoliday, BigDecimal attachmentAboveDays, Set<UUID> eligibleCategoryIds, String color,
            Boolean active, Long version) {
    }

    @Transactional(readOnly = true)
    public List<LeaveType> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return types.findByCompanyIdOrderByCode(companyId);
    }

    @Transactional(readOnly = true)
    public LeaveType get(UUID id) {
        return types.findById(id).orElseThrow(() -> new NotFoundException("Leave type", id));
    }

    @Transactional
    public LeaveType create(UUID companyId, LeaveTypeData d) {
        scope.assertCompanyWritable(companyId);
        validate(companyId, null, d);
        LeaveType t = new LeaveType(companyId);
        apply(t, d);
        types.save(t);
        audit.record(AuditEntry.of("CREATE", "LEAVE_TYPE", t.getId()).company(companyId).after(d));
        return t;
    }

    @Transactional
    public LeaveType update(UUID id, LeaveTypeData d) {
        LeaveType t = get(id);
        scope.assertCompanyWritable(t.getCompanyId());
        Versions.check(t, d.version());
        validate(t.getCompanyId(), id, d);
        apply(t, d);
        audit.record(AuditEntry.of("UPDATE", "LEAVE_TYPE", id).company(t.getCompanyId()).after(d));
        return t;
    }

    @EventListener
    @Transactional
    public void seed(CompanyCreatedEvent event) {
        UUID c = event.companyId();
        seed(c, "ANNUAL", "Annual leave", true, 14, true, false, false);
        seed(c, "CASUAL", "Casual leave", true, 7, true, false, false);
        seed(c, "MEDICAL", "Medical leave", true, 7, false, false, false);
        seed(c, "NOPAY", "No pay leave", false, 0, false, true, true);
    }

    private void seed(UUID companyId, String code, String name, boolean paid, int days, boolean proRata,
            boolean unlimited, boolean allowNegative) {
        LeaveType t = new LeaveType(companyId);
        t.setCode(code);
        t.setName(name);
        t.setPaid(paid);
        t.setAnnualDays(BigDecimal.valueOf(days));
        t.setAccrualMethod(AccrualMethod.ANNUAL_UPFRONT);
        t.setProRata(proRata);
        t.setAllowHalfDay(true);
        t.setUnlimited(unlimited);
        t.setAllowNegative(allowNegative);
        types.save(t);
    }

    private void apply(LeaveType t, LeaveTypeData d) {
        t.setCode(d.code().toUpperCase());
        t.setName(d.name().trim());
        t.setPaid(d.paid() == null || d.paid());
        t.setAnnualDays(d.annualDays() == null ? BigDecimal.ZERO : d.annualDays());
        t.setAccrualMethod(d.accrualMethod() == null ? AccrualMethod.ANNUAL_UPFRONT : d.accrualMethod());
        t.setProRata(Boolean.TRUE.equals(d.proRata()));
        t.setCfMaxDays(d.cfMaxDays() == null ? BigDecimal.ZERO : d.cfMaxDays());
        t.setCfExpiryMonths(d.cfExpiryMonths() == null ? 0 : d.cfExpiryMonths());
        t.setAllowHalfDay(d.allowHalfDay() == null || d.allowHalfDay());
        t.setAllowNegative(Boolean.TRUE.equals(d.allowNegative()));
        t.setUnlimited(Boolean.TRUE.equals(d.unlimited()));
        t.setCountWeeklyOff(Boolean.TRUE.equals(d.countWeeklyOff()));
        t.setCountHoliday(Boolean.TRUE.equals(d.countHoliday()));
        t.setAttachmentAboveDays(d.attachmentAboveDays());
        t.getEligibleCategoryIds().clear();
        if (d.eligibleCategoryIds() != null) {
            t.getEligibleCategoryIds().addAll(d.eligibleCategoryIds());
        }
        t.setColor(d.color());
        t.setActive(d.active() == null || d.active());
    }

    private void validate(UUID companyId, UUID selfId, LeaveTypeData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.code() == null || !d.code().matches("[A-Za-z0-9_-]{1,20}")) {
            errors.add(new FieldError("code", "must be 1-20 letters, digits, '-' or '_'"));
        } else {
            types.findByCompanyIdAndCodeIgnoreCase(companyId, d.code()).filter(o -> !o.getId().equals(selfId))
                    .ifPresent(o -> errors.add(new FieldError("code", "code already exists in this company")));
        }
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "is required"));
        }
        halfSteps(errors, "annualDays", d.annualDays());
        halfSteps(errors, "cfMaxDays", d.cfMaxDays());
        if (d.cfExpiryMonths() != null && (d.cfExpiryMonths() < 0 || d.cfExpiryMonths() > 12)) {
            errors.add(new FieldError("cfExpiryMonths", "must be between 0 and 12"));
        }
        if (d.attachmentAboveDays() != null && d.attachmentAboveDays().signum() < 0) {
            errors.add(new FieldError("attachmentAboveDays", "must not be negative"));
        }
        if (d.color() != null && !d.color().isBlank() && !d.color().matches("#[0-9A-Fa-f]{6}")) {
            errors.add(new FieldError("color", "must be a hex colour"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private static void halfSteps(List<FieldError> errors, String field, BigDecimal v) {
        if (v != null && (v.signum() < 0 || v.remainder(HALF).signum() != 0 || v.compareTo(BigDecimal.valueOf(366)) > 0)) {
            errors.add(new FieldError(field, "must be 0-366 in steps of 0.5"));
        }
    }
}
