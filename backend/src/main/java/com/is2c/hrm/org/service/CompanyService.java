package com.is2c.hrm.org.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.api.CompanyCreatedEvent;
import com.is2c.hrm.org.api.EmployeeNumberFormat;
import com.is2c.hrm.org.domain.Company;
import com.is2c.hrm.org.domain.CompanyRepository;
import com.is2c.hrm.org.domain.CompanySettings;
import com.is2c.hrm.org.domain.CompanySettingsRepository;

/** FR-010 companies and FR-016 company settings. */
@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final CompanySettingsRepository settingsRepo;
    private final DataScopeService scope;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public CompanyService(CompanyRepository companies, CompanySettingsRepository settingsRepo, DataScopeService scope,
            AuditService audit, ApplicationEventPublisher events) {
        this.companies = companies;
        this.settingsRepo = settingsRepo;
        this.scope = scope;
        this.audit = audit;
        this.events = events;
    }

    public record CompanyData(String code, String legalName, String registrationNo, String address, String phone,
            String email, String currency, String epfEmployerNo, String etfNo, String statutoryPack, String timeZone,
            Long version) {
    }

    public record SettingsData(Set<DayOfWeek> weeklyOffDays, String employeeNumberFormat, Integer payrollStartDay,
            Boolean absentAsNoPay, BigDecimal noPayDivisor, Integer punchWindowBeforeMinutes,
            Integer punchWindowAfterMinutes, DayOfWeek weekStartDay, Long version) {
    }

    @Transactional(readOnly = true)
    public List<Company> list() {
        var s = scope.current();
        return companies.findAll().stream().filter(c -> s.coversCompany(c.getId()))
                .sorted(java.util.Comparator.comparing(Company::getCode)).toList();
    }

    @Transactional(readOnly = true)
    public Company get(UUID id) {
        scope.assertCompanyReadable(id);
        return companies.findById(id).orElseThrow(() -> new NotFoundException("Company", id));
    }

    @Transactional
    public Company create(CompanyData data) {
        if (!scope.current().all()) {
            throw new ForbiddenException("Only users with global scope can create companies");
        }
        validate(data);
        companies.findByCodeIgnoreCase(data.code()).ifPresent(c -> {
            throw new ValidationException("code", "Company code already exists");
        });
        Company c = new Company(data.code().toUpperCase());
        apply(c, data);
        companies.save(c);
        settingsRepo.save(new CompanySettings(c.getId()));
        audit.record(AuditEntry.of("CREATE", "COMPANY", c.getId()).company(c.getId()).after(data));
        events.publishEvent(new CompanyCreatedEvent(c.getId(), c.getStatutoryPack()));
        return c;
    }

    @Transactional
    public Company update(UUID id, CompanyData data) {
        scope.assertCompanyWritable(id);
        Company c = companies.findById(id).orElseThrow(() -> new NotFoundException("Company", id));
        Versions.check(c, data.version());
        validate(data);
        companies.findByCodeIgnoreCase(data.code()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new ValidationException("code", "Company code already exists");
        });
        CompanyData before = snapshot(c);
        c.setCode(data.code().toUpperCase());
        apply(c, data);
        audit.record(AuditEntry.of("UPDATE", "COMPANY", id).company(id).before(before).after(data));
        return c;
    }

    @Transactional(readOnly = true)
    public CompanySettings settings(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return settingsRepo.findByCompanyId(companyId).orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    @Transactional
    public CompanySettings updateSettings(UUID companyId, SettingsData data) {
        scope.assertCompanyWritable(companyId);
        CompanySettings s = settingsRepo.findByCompanyId(companyId)
                .orElseThrow(() -> new NotFoundException("Company", companyId));
        Versions.check(s, data.version());
        validateSettings(data);
        SettingsData before = snapshot(s);
        s.setWeeklyOffDays(data.weeklyOffDays());
        s.setEmployeeNumberFormat(data.employeeNumberFormat());
        s.setPayrollStartDay(data.payrollStartDay());
        s.setAbsentAsNoPay(data.absentAsNoPay());
        s.setNoPayDivisor(data.noPayDivisor());
        s.setPunchWindowBeforeMinutes(data.punchWindowBeforeMinutes());
        s.setPunchWindowAfterMinutes(data.punchWindowAfterMinutes());
        s.setWeekStartDay(data.weekStartDay());
        audit.record(AuditEntry.of("UPDATE", "COMPANY_SETTINGS", companyId).company(companyId).before(before).after(data));
        return s;
    }

    private static void apply(Company c, CompanyData d) {
        c.setLegalName(d.legalName().trim());
        c.setRegistrationNo(d.registrationNo());
        c.setAddress(d.address());
        c.setPhone(d.phone());
        c.setEmail(d.email());
        c.setCurrency(d.currency() == null ? "LKR" : d.currency().toUpperCase());
        c.setEpfEmployerNo(d.epfEmployerNo());
        c.setEtfNo(d.etfNo());
        c.setStatutoryPack(d.statutoryPack() == null || d.statutoryPack().isBlank() ? "LK" : d.statutoryPack().toUpperCase());
        c.setTimeZone(d.timeZone() == null || d.timeZone().isBlank() ? "Asia/Colombo" : d.timeZone());
    }

    private static void validate(CompanyData d) {
        List<FieldError> errors = new java.util.ArrayList<>();
        if (d.code() == null || !d.code().matches("[A-Za-z0-9_-]{1,20}")) {
            errors.add(new FieldError("code", "must be 1-20 letters, digits, '-' or '_'"));
        }
        if (d.legalName() == null || d.legalName().isBlank()) {
            errors.add(new FieldError("legalName", "must not be blank"));
        }
        if (d.currency() != null) {
            try {
                Currency.getInstance(d.currency().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("currency", "must be an ISO 4217 currency code"));
            }
        }
        if (d.timeZone() != null && !d.timeZone().isBlank()) {
            try {
                ZoneId.of(d.timeZone());
            } catch (DateTimeException e) {
                errors.add(new FieldError("timeZone", "must be a valid time zone id"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private static void validateSettings(SettingsData d) {
        List<FieldError> errors = new java.util.ArrayList<>();
        if (d.weeklyOffDays() == null) {
            errors.add(new FieldError("weeklyOffDays", "must not be null"));
        }
        EmployeeNumberFormat.problems(d.employeeNumberFormat())
                .forEach(p -> errors.add(new FieldError("employeeNumberFormat", p)));
        if (d.payrollStartDay() == null || d.payrollStartDay() < 1 || d.payrollStartDay() > 28) {
            errors.add(new FieldError("payrollStartDay", "must be between 1 and 28"));
        }
        if (d.absentAsNoPay() == null) {
            errors.add(new FieldError("absentAsNoPay", "must not be null"));
        }
        if (d.noPayDivisor() == null || d.noPayDivisor().signum() <= 0) {
            errors.add(new FieldError("noPayDivisor", "must be greater than 0"));
        }
        if (d.punchWindowBeforeMinutes() == null || d.punchWindowBeforeMinutes() < 0 || d.punchWindowBeforeMinutes() > 720) {
            errors.add(new FieldError("punchWindowBeforeMinutes", "must be between 0 and 720"));
        }
        if (d.punchWindowAfterMinutes() == null || d.punchWindowAfterMinutes() < 0 || d.punchWindowAfterMinutes() > 720) {
            errors.add(new FieldError("punchWindowAfterMinutes", "must be between 0 and 720"));
        }
        if (d.weekStartDay() == null) {
            errors.add(new FieldError("weekStartDay", "must not be null"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private static CompanyData snapshot(Company c) {
        return new CompanyData(c.getCode(), c.getLegalName(), c.getRegistrationNo(), c.getAddress(), c.getPhone(),
                c.getEmail(), c.getCurrency(), c.getEpfEmployerNo(), c.getEtfNo(), c.getStatutoryPack(),
                c.getTimeZone(), c.getVersion());
    }

    private static SettingsData snapshot(CompanySettings s) {
        return new SettingsData(Set.copyOf(s.getWeeklyOffDays()), s.getEmployeeNumberFormat(), s.getPayrollStartDay(),
                s.isAbsentAsNoPay(), s.getNoPayDivisor(), s.getPunchWindowBeforeMinutes(),
                s.getPunchWindowAfterMinutes(), s.getWeekStartDay(), s.getVersion());
    }
}
