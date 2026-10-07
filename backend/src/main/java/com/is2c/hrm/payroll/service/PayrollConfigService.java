package com.is2c.hrm.payroll.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.api.CompanyCreatedEvent;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.api.PackInstaller;
import com.is2c.hrm.payroll.api.UnitMetric;
import com.is2c.hrm.payroll.domain.PayrollComponent;
import com.is2c.hrm.payroll.domain.PayrollComponent.Assignment;
import com.is2c.hrm.payroll.domain.PayrollComponent.Frequency;
import com.is2c.hrm.payroll.domain.PayrollComponent.UnitRateMode;
import com.is2c.hrm.payroll.domain.PayrollComponentRepository;
import com.is2c.hrm.payroll.domain.SlabRow;
import com.is2c.hrm.payroll.domain.SlabTable;
import com.is2c.hrm.payroll.domain.SlabTableRepository;

/** FR-080/081 payroll components, slab tables, statutory settings and per-company seeding. */
@Service
public class PayrollConfigService {

    private final PayrollComponentRepository components;
    private final SlabTableRepository slabs;
    private final StatutoryPackRegistry packs;
    private final DataScopeService scope;
    private final AuditService audit;

    public PayrollConfigService(PayrollComponentRepository components, SlabTableRepository slabs,
            StatutoryPackRegistry packs, DataScopeService scope, AuditService audit) {
        this.components = components;
        this.slabs = slabs;
        this.packs = packs;
        this.scope = scope;
        this.audit = audit;
    }

    public record ComponentData(String code, String name, ComponentKind kind, Frequency frequency, Assignment assignment,
            CalcType calcType, Integer sequence, Set<String> bases, String basisRef, BigDecimal ratePercent,
            UnitMetric unitMetric, UnitRateMode unitRateMode, BigDecimal unitFixedAmount, BigDecimal divisor,
            BigDecimal multiplier, UUID slabTableId, Boolean prorate, Boolean showOnPayslip, Boolean statutory,
            Boolean active, Long version) {
    }

    public record SlabData(String code, String name, Boolean statutory, List<SlabRow> rows, Long version) {
    }

    public record StatutoryView(String pack, List<PayrollComponent> components, List<SlabTable> slabTables) {
    }

    // ---------------------------------------------------------------- components

    @Transactional(readOnly = true)
    public List<PayrollComponent> components(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return components.findByCompanyIdOrderBySequenceAscCodeAsc(companyId);
    }

    /** Unscoped read for payroll processing. */
    @Transactional(readOnly = true)
    public List<PayrollComponent> componentsForProcessing(UUID companyId) {
        return components.findByCompanyIdOrderBySequenceAscCodeAsc(companyId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, SlabTable> slabTablesForProcessing(UUID companyId) {
        return slabs.findByCompanyIdOrderByCode(companyId).stream().collect(Collectors.toMap(SlabTable::getId, t -> t));
    }

    @Transactional
    public PayrollComponent createComponent(UUID companyId, ComponentData d) {
        scope.assertCompanyWritable(companyId);
        if (d.code() == null || !d.code().toUpperCase().matches("[A-Z][A-Z0-9_]{0,29}")) {
            throw new ValidationException("code", "must be 1-30 upper-case letters, digits or '_'");
        }
        PayrollComponent c = new PayrollComponent(companyId, d.code().toUpperCase());
        apply(c, d);
        validateCompany(companyId, c);
        components.save(c);
        audit.record(AuditEntry.of("CREATE", "PAYROLL_COMPONENT", c.getId()).company(companyId).after(d));
        return c;
    }

    @Transactional
    public PayrollComponent updateComponent(UUID id, ComponentData d) {
        PayrollComponent c = components.findById(id).orElseThrow(() -> new NotFoundException("Component", id));
        scope.assertCompanyWritable(c.getCompanyId());
        Versions.check(c, d.version());
        ComponentData before = snapshot(c);
        if (c.isSystemComponent()) {
            if (d.active() != null && !d.active()) {
                throw new ValidationException("active", "the BASIC component cannot be deactivated");
            }
            // System component: only presentation, bases and proration may change.
            c.setName(blank(d.name()) ? c.getName() : d.name().trim());
            c.setBaseSet(d.bases());
            c.setProrate(d.prorate() == null || d.prorate());
            c.setShowOnPayslip(d.showOnPayslip() == null || d.showOnPayslip());
        } else {
            apply(c, d);
        }
        validateCompany(c.getCompanyId(), c);
        audit.record(AuditEntry.of("UPDATE", c.isStatutory() ? "STATUTORY_SETTING" : "PAYROLL_COMPONENT", id)
                .company(c.getCompanyId()).before(before).after(d));
        return c;
    }

    private void validateCompany(UUID companyId, PayrollComponent candidate) {
        List<PayrollComponent> all = new ArrayList<>(components.findByCompanyIdOrderBySequenceAscCodeAsc(companyId)
                .stream().filter(x -> !x.getId().equals(candidate.getId())).toList());
        all.add(candidate);
        Set<UUID> slabIds = slabs.findByCompanyIdOrderByCode(companyId).stream().map(SlabTable::getId)
                .collect(Collectors.toSet());
        List<FieldError> errors = new ArrayList<>();
        if (blank(candidate.getName())) {
            errors.add(new FieldError("name", "is required"));
        }
        errors.addAll(ComponentGraphValidator.validate(all, slabIds));
        if (!errors.isEmpty()) {
            throw new ValidationException("Invalid component configuration", errors);
        }
    }

    private static void apply(PayrollComponent c, ComponentData d) {
        c.setName(blank(d.name()) ? null : d.name().trim());
        c.setKind(d.kind());
        c.setFrequency(d.frequency() == null ? Frequency.RECURRING : d.frequency());
        c.setAssignment(d.assignment() == null ? Assignment.PER_EMPLOYEE : d.assignment());
        c.setCalcType(d.calcType());
        c.setSequence(d.sequence() == null ? 100 : d.sequence());
        c.setBaseSet(d.bases());
        c.setBasisRef(blank(d.basisRef()) ? null : d.basisRef().trim().toUpperCase());
        c.setRatePercent(d.ratePercent());
        c.setUnitMetric(d.unitMetric());
        c.setUnitRateMode(d.unitRateMode());
        c.setUnitFixedAmount(d.unitFixedAmount());
        c.setDivisor(d.divisor());
        c.setMultiplier(d.multiplier());
        c.setSlabTableId(d.slabTableId());
        c.setProrate(Boolean.TRUE.equals(d.prorate()));
        c.setShowOnPayslip(d.showOnPayslip() == null || d.showOnPayslip());
        c.setStatutory(Boolean.TRUE.equals(d.statutory()));
        c.setActive(d.active() == null || d.active());
    }

    private static ComponentData snapshot(PayrollComponent c) {
        return new ComponentData(c.getCode(), c.getName(), c.getKind(), c.getFrequency(), c.getAssignment(),
                c.getCalcType(), c.getSequence(), c.baseSet(), c.getBasisRef(), c.getRatePercent(), c.getUnitMetric(),
                c.getUnitRateMode(), c.getUnitFixedAmount(), c.getDivisor(), c.getMultiplier(), c.getSlabTableId(),
                c.isProrate(), c.isShowOnPayslip(), c.isStatutory(), c.isActive(), c.getVersion());
    }

    // ---------------------------------------------------------------- slab tables

    @Transactional(readOnly = true)
    public List<SlabTable> slabTables(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return slabs.findByCompanyIdOrderByCode(companyId);
    }

    @Transactional
    public SlabTable createSlabTable(UUID companyId, SlabData d) {
        scope.assertCompanyWritable(companyId);
        validateSlab(companyId, null, d);
        SlabTable t = new SlabTable(companyId, d.code().toUpperCase());
        t.setName(d.name().trim());
        t.setStatutory(Boolean.TRUE.equals(d.statutory()));
        t.getRows().addAll(d.rows());
        slabs.save(t);
        audit.record(AuditEntry.of("CREATE", "SLAB_TABLE", t.getId()).company(companyId).after(d));
        return t;
    }

    @Transactional
    public SlabTable updateSlabTable(UUID id, SlabData d) {
        SlabTable t = slabs.findById(id).orElseThrow(() -> new NotFoundException("Slab table", id));
        scope.assertCompanyWritable(t.getCompanyId());
        Versions.check(t, d.version());
        SlabData merged = new SlabData(t.getCode(), d.name(), t.isStatutory(), d.rows(), d.version());
        validateSlab(t.getCompanyId(), id, merged);
        SlabData before = new SlabData(t.getCode(), t.getName(), t.isStatutory(), List.copyOf(t.getRows()), t.getVersion());
        t.setName(d.name().trim());
        t.getRows().clear();
        t.getRows().addAll(d.rows());
        audit.record(AuditEntry.of("UPDATE", t.isStatutory() ? "STATUTORY_SETTING" : "SLAB_TABLE", id)
                .company(t.getCompanyId()).before(before).after(merged));
        return t;
    }

    private void validateSlab(UUID companyId, UUID selfId, SlabData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.code() == null || !d.code().toUpperCase().matches("[A-Z][A-Z0-9_]{0,29}")) {
            errors.add(new FieldError("code", "must be 1-30 upper-case letters, digits or '_'"));
        } else {
            slabs.findByCompanyIdAndCode(companyId, d.code().toUpperCase()).filter(o -> !o.getId().equals(selfId))
                    .ifPresent(o -> errors.add(new FieldError("code", "code already exists")));
        }
        if (blank(d.name())) {
            errors.add(new FieldError("name", "is required"));
        }
        errors.addAll(slabRowProblems(d.rows()));
        if (!errors.isEmpty()) {
            throw new ValidationException("Invalid slab table", errors);
        }
    }

    /** Rows must start at 0, be contiguous, have positive width, and only the last may be open-ended. */
    static List<FieldError> slabRowProblems(List<SlabRow> rows) {
        List<FieldError> errors = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            errors.add(new FieldError("rows", "at least one row is required"));
            return errors;
        }
        BigDecimal expectedLower = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            SlabRow r = rows.get(i);
            String f = "rows[" + i + "]";
            if (r.lowerBound() == null || r.lowerBound().compareTo(expectedLower) != 0) {
                errors.add(new FieldError(f, "lower bound must be " + expectedLower.stripTrailingZeros().toPlainString()));
                return errors;
            }
            if (r.ratePercent() == null || r.ratePercent().signum() < 0 || r.ratePercent().compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.add(new FieldError(f, "rate must be between 0 and 100"));
            }
            if (r.upperBound() == null) {
                if (i != rows.size() - 1) {
                    errors.add(new FieldError(f, "only the last row can be open-ended"));
                }
                return errors;
            }
            if (r.upperBound().compareTo(r.lowerBound()) <= 0) {
                errors.add(new FieldError(f, "upper bound must be greater than the lower bound"));
                return errors;
            }
            expectedLower = r.upperBound();
        }
        return errors;
    }

    // ---------------------------------------------------------------- statutory (FR-081)

    @Transactional(readOnly = true)
    public StatutoryView statutory(UUID companyId, String packCode) {
        scope.assertCompanyReadable(companyId);
        return new StatutoryView(packCode,
                components.findByCompanyIdOrderBySequenceAscCodeAsc(companyId).stream().filter(PayrollComponent::isStatutory).toList(),
                slabs.findByCompanyIdOrderByCode(companyId).stream().filter(SlabTable::isStatutory).toList());
    }

    /** BASIC plus the company's statutory pack (FR-080, FR-081). */
    @EventListener
    @Transactional
    public void seed(CompanyCreatedEvent event) {
        UUID companyId = event.companyId();
        PayrollComponent basic = new PayrollComponent(companyId, PayrollComponent.BASIC);
        basic.setName("Basic salary");
        basic.setKind(ComponentKind.EARNING);
        basic.setCalcType(CalcType.FIXED);
        basic.setSequence(10);
        basic.setBaseSet(Set.of("STATUTORY", "TAXABLE"));
        basic.setProrate(true);
        basic.setSystemComponent(true);
        components.save(basic);
        packs.get(event.statutoryPack()).ifPresent(pack -> pack.install(new Installer(companyId)));
    }

    private final class Installer implements PackInstaller {
        private final UUID companyId;

        Installer(UUID companyId) {
            this.companyId = companyId;
        }

        @Override
        public void slabTable(String code, String name, boolean statutory, List<Slab> rows) {
            SlabTable t = new SlabTable(companyId, code);
            t.setName(name);
            t.setStatutory(statutory);
            rows.forEach(r -> t.getRows().add(new SlabRow(r.lowerBound(), r.upperBound(), r.ratePercent())));
            slabs.save(t);
        }

        @Override
        public void component(ComponentSpec s) {
            PayrollComponent c = new PayrollComponent(companyId, s.code());
            c.setName(s.name());
            c.setKind(s.kind());
            c.setFrequency(Frequency.valueOf(s.frequency()));
            c.setAssignment(Assignment.valueOf(s.assignment()));
            c.setCalcType(s.calcType());
            c.setSequence(s.sequence());
            c.setBaseSet(s.bases());
            c.setBasisRef(s.basisRef());
            c.setRatePercent(s.ratePercent());
            c.setUnitMetric(s.unitMetric());
            c.setUnitRateMode(s.unitRateMode() == null ? null : UnitRateMode.valueOf(s.unitRateMode()));
            c.setUnitFixedAmount(s.unitFixedAmount());
            c.setDivisor(s.divisor());
            c.setMultiplier(s.multiplier());
            if (s.slabTableCode() != null) {
                c.setSlabTableId(slabs.findByCompanyIdAndCode(companyId, s.slabTableCode()).orElseThrow().getId());
            }
            c.setProrate(s.prorate());
            c.setShowOnPayslip(s.showOnPayslip());
            c.setStatutory(s.statutory());
            components.save(c);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
