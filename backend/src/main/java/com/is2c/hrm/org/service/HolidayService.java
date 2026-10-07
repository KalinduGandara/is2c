package com.is2c.hrm.org.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.BranchRepository;
import com.is2c.hrm.org.domain.Holiday;
import com.is2c.hrm.org.domain.HolidayRepository;

/** FR-025 holiday calendar. */
@Service
public class HolidayService {

    private final HolidayRepository holidays;
    private final BranchRepository branches;
    private final DataScopeService scope;
    private final AuditService audit;

    public HolidayService(HolidayRepository holidays, BranchRepository branches, DataScopeService scope,
            AuditService audit) {
        this.holidays = holidays;
        this.branches = branches;
        this.scope = scope;
        this.audit = audit;
    }

    public record HolidayData(LocalDate date, String name, Holiday.Type type, Boolean allBranches,
            Set<UUID> branchIds, Long version) {
    }

    public record CopyResult(int copied, int skipped) {
    }

    @Transactional(readOnly = true)
    public List<Holiday> list(UUID companyId, int year) {
        scope.assertCompanyReadable(companyId);
        return holidays.findByCompanyIdAndDateBetweenOrderByDate(companyId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
    }

    /** Holidays of several companies in a date range (for schedule resolution; no scope check). */
    @Transactional(readOnly = true)
    public List<Holiday> between(Collection<UUID> companyIds, LocalDate from, LocalDate to) {
        return companyIds.isEmpty() ? List.of() : holidays.findByCompanyIdInAndDateBetween(companyIds, from, to);
    }

    @Transactional
    public Holiday create(UUID companyId, HolidayData d) {
        scope.assertCompanyWritable(companyId);
        validate(companyId, null, d);
        Holiday h = new Holiday(companyId);
        apply(h, d);
        holidays.save(h);
        audit.record(AuditEntry.of("CREATE", "HOLIDAY", h.getId()).company(companyId).after(d));
        return h;
    }

    @Transactional
    public Holiday update(UUID id, HolidayData d) {
        Holiday h = holidays.findById(id).orElseThrow(() -> new NotFoundException("Holiday", id));
        scope.assertCompanyWritable(h.getCompanyId());
        Versions.check(h, d.version());
        validate(h.getCompanyId(), id, d);
        HolidayData before = new HolidayData(h.getDate(), h.getName(), h.getType(), h.isAllBranches(),
                Set.copyOf(h.getBranchIds()), h.getVersion());
        apply(h, d);
        audit.record(AuditEntry.of("UPDATE", "HOLIDAY", id).company(h.getCompanyId()).before(before).after(d));
        return h;
    }

    @Transactional
    public void delete(UUID id) {
        Holiday h = holidays.findById(id).orElseThrow(() -> new NotFoundException("Holiday", id));
        scope.assertCompanyWritable(h.getCompanyId());
        holidays.delete(h);
        audit.record(AuditEntry.of("DELETE", "HOLIDAY", id).company(h.getCompanyId())
                .before(new HolidayData(h.getDate(), h.getName(), h.getType(), h.isAllBranches(),
                        Set.copyOf(h.getBranchIds()), h.getVersion())));
    }

    /** Copies holidays of one year to another by month/day; skips Feb 29 in non-leap years and duplicates. */
    @Transactional
    public CopyResult copyYear(UUID companyId, int fromYear, int toYear) {
        scope.assertCompanyWritable(companyId);
        if (fromYear == toYear) {
            throw new ValidationException("toYear", "target year must differ from the source year");
        }
        int copied = 0;
        int skipped = 0;
        for (Holiday source : list(companyId, fromYear)) {
            MonthDay md = MonthDay.from(source.getDate());
            if (!md.isValidYear(toYear)) {
                skipped++;
                continue;
            }
            LocalDate target = md.atYear(toYear);
            boolean duplicate = holidays.findByCompanyIdAndDate(companyId, target).stream()
                    .anyMatch(o -> o.overlaps(source.isAllBranches(), source.getBranchIds()));
            if (duplicate) {
                skipped++;
                continue;
            }
            Holiday copy = new Holiday(companyId);
            copy.setDate(target);
            copy.setName(source.getName());
            copy.setType(source.getType());
            copy.setAllBranches(source.isAllBranches());
            copy.getBranchIds().addAll(source.getBranchIds());
            holidays.save(copy);
            copied++;
        }
        audit.record(AuditEntry.of("COPY_YEAR", "HOLIDAY", companyId).company(companyId)
                .after(java.util.Map.of("fromYear", fromYear, "toYear", toYear, "copied", copied, "skipped", skipped)));
        return new CopyResult(copied, skipped);
    }

    private void apply(Holiday h, HolidayData d) {
        h.setDate(d.date());
        h.setName(d.name().trim());
        h.setType(d.type());
        boolean all = d.allBranches() == null || d.allBranches();
        h.setAllBranches(all);
        h.getBranchIds().clear();
        if (!all) {
            h.getBranchIds().addAll(d.branchIds());
        }
    }

    private void validate(UUID companyId, UUID selfId, HolidayData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.date() == null) {
            errors.add(new FieldError("date", "is required"));
        }
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "is required"));
        }
        if (d.type() == null) {
            errors.add(new FieldError("type", "is required"));
        }
        boolean all = d.allBranches() == null || d.allBranches();
        Set<UUID> branchIds = d.branchIds() == null ? Set.of() : new HashSet<>(d.branchIds());
        if (!all) {
            if (branchIds.isEmpty()) {
                errors.add(new FieldError("branchIds", "select at least one branch"));
            } else if (branches.findByIdIn(branchIds).stream().map(Branch::getCompanyId)
                    .filter(companyId::equals).count() != branchIds.size()) {
                errors.add(new FieldError("branchIds", "branches must belong to the company"));
            }
        }
        if (errors.isEmpty()) {
            boolean duplicate = holidays.findByCompanyIdAndDate(companyId, d.date()).stream()
                    .filter(o -> !o.getId().equals(selfId))
                    .anyMatch(o -> o.overlaps(all, branchIds));
            if (duplicate) {
                errors.add(new FieldError("date", "a holiday already exists on this date for these branches"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}
