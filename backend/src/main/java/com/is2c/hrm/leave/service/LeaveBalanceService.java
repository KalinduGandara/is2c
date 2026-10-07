package com.is2c.hrm.leave.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeStatus;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.leave.domain.LeaveBalance;
import com.is2c.hrm.leave.domain.LeaveBalanceRepository;
import com.is2c.hrm.leave.domain.LeaveBalanceTxn;
import com.is2c.hrm.leave.domain.LeaveBalanceTxnRepository;
import com.is2c.hrm.leave.domain.LeaveType;
import com.is2c.hrm.leave.domain.LeaveType.AccrualMethod;
import com.is2c.hrm.leave.domain.LeaveTypeRepository;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-061/FR-062 balances, ledger postings, accrual and carry-forward. */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balances;
    private final LeaveBalanceTxnRepository ledger;
    private final LeaveTypeRepository types;
    private final EmployeeService employees;
    private final OrgDirectory org;
    private final AuditService audit;
    private final Clock clock;

    public LeaveBalanceService(LeaveBalanceRepository balances, LeaveBalanceTxnRepository ledger,
            LeaveTypeRepository types, EmployeeService employees, OrgDirectory org, AuditService audit, Clock clock) {
        this.balances = balances;
        this.ledger = ledger;
        this.types = types;
        this.employees = employees;
        this.org = org;
        this.audit = audit;
        this.clock = clock;
    }

    public record BalanceView(LeaveType type, LeaveBalance balance) {
    }

    // ---------------------------------------------------------------- queries

    /** Balances of an employee for a year (in scope); created lazily with entitlement up to today. */
    @Transactional
    public List<BalanceView> forEmployee(UUID employeeId, int year) {
        return forEmployee(employees.get(employeeId), year);
    }

    /** Self-service variant without scope checks. */
    @Transactional
    public List<BalanceView> own(UUID employeeId, int year) {
        return forEmployee(employees.byIds(List.of(employeeId)).get(employeeId), year);
    }

    private List<BalanceView> forEmployee(Employee e, int year) {
        List<BalanceView> result = new ArrayList<>();
        for (LeaveType t : types.findByCompanyIdOrderByCode(e.getCompanyId())) {
            if (!t.isActive() || !t.eligible(e.getCategoryId())) {
                continue;
            }
            result.add(new BalanceView(t, t.isUnlimited() ? null : ensure(e, t, year)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceTxn> ledger(UUID balanceId) {
        LeaveBalance b = balances.findById(balanceId).orElseThrow();
        employees.get(b.getEmployeeId());
        return ledger.findByBalanceIdOrderByCreatedAt(balanceId);
    }

    // ---------------------------------------------------------------- manual postings (FR-061)

    @Transactional
    public LeaveBalance setOpening(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days, String reason) {
        LeaveBalance b = manualTarget(employeeId, leaveTypeId, year, days, reason);
        BigDecimal before = b.getOpening();
        b.setOpening(days);
        post(b, LeaveBalanceTxn.Type.OPENING, days.subtract(before), null, reason);
        audit.record(AuditEntry.of("OPENING_BALANCE", "LEAVE_BALANCE", b.getId()).reason(reason)
                .before(Map.of("opening", before)).after(Map.of("opening", days)));
        return b;
    }

    @Transactional
    public LeaveBalance adjust(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days, String reason) {
        LeaveBalance b = manualTarget(employeeId, leaveTypeId, year, days, reason);
        if (days.signum() == 0) {
            throw new ValidationException("days", "must not be zero");
        }
        b.addAdjustment(days);
        post(b, LeaveBalanceTxn.Type.ADJUSTMENT, days, null, reason);
        audit.record(AuditEntry.of("ADJUST_BALANCE", "LEAVE_BALANCE", b.getId()).reason(reason)
                .after(Map.of("days", days, "available", b.available())));
        return b;
    }

    private LeaveBalance manualTarget(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days, String reason) {
        Employee e = employees.get(employeeId);
        List<FieldError> errors = new ArrayList<>();
        LeaveType t = types.findById(leaveTypeId).filter(x -> x.getCompanyId().equals(e.getCompanyId())).orElse(null);
        if (t == null) {
            errors.add(new FieldError("leaveTypeId", "unknown leave type for this employee"));
        } else if (t.isUnlimited()) {
            errors.add(new FieldError("leaveTypeId", "unlimited leave types have no balance"));
        }
        if (days == null || days.remainder(new BigDecimal("0.5")).signum() != 0) {
            errors.add(new FieldError("days", "must be in steps of 0.5"));
        }
        if (reason == null || reason.isBlank()) {
            errors.add(new FieldError("reason", "a reason is required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        return ensure(e, t, year);
    }

    // ---------------------------------------------------------------- postings used by leave requests

    @Transactional
    public LeaveBalance ensure(Employee e, LeaveType t, int year) {
        Optional<LeaveBalance> existing = balances.findByEmployeeIdAndLeaveTypeIdAndYear(e.getId(), t.getId(), year);
        if (existing.isPresent()) {
            return existing.get();
        }
        LeaveBalance b = balances.save(new LeaveBalance(e.getId(), t.getId(), year));
        if (e.getStatus() == EmployeeStatus.ACTIVE) {
            accrue(e, t, b, LocalDate.now(clock.withZone(org.settings(e.getCompanyId()).timeZone())));
        }
        return b;
    }

    public void post(LeaveBalance b, LeaveBalanceTxn.Type type, BigDecimal days, String reference, String reason) {
        UUID user = CurrentUser.find().map(AuthenticatedUser::id).orElse(null);
        ledger.save(new LeaveBalanceTxn(b.getId(), type, days, reference, reason, user, clock.instant()));
    }

    // ---------------------------------------------------------------- jobs (FR-062), idempotent

    /** Upfront grant once per year; monthly credits for every employed month up to {@code asOf}. */
    void accrue(Employee e, LeaveType t, LeaveBalance b, LocalDate asOf) {
        int year = b.getYear();
        if (asOf.getYear() < year || e.getJoinDate().getYear() > year) {
            return;
        }
        if (t.getAccrualMethod() == AccrualMethod.ANNUAL_UPFRONT) {
            String ref = "UPFRONT:" + year;
            if (!ledger.existsByBalanceIdAndTypeAndReference(b.getId(), LeaveBalanceTxn.Type.ACCRUAL, ref)) {
                BigDecimal grant = LeaveRules.upfrontGrant(t.getAnnualDays(), t.isProRata(), e.getJoinDate(), year);
                b.addEntitled(grant);
                post(b, LeaveBalanceTxn.Type.ACCRUAL, grant, ref, "annual entitlement");
            }
            return;
        }
        YearMonth first = e.getJoinDate().getYear() == year ? YearMonth.from(e.getJoinDate()) : YearMonth.of(year, 1);
        YearMonth last = asOf.getYear() > year ? YearMonth.of(year, 12) : YearMonth.from(asOf);
        BigDecimal credit = LeaveRules.monthlyCredit(t.getAnnualDays());
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            int key = m.getYear() * 100 + m.getMonthValue();
            if (b.getLastAccrualMonth() != null && b.getLastAccrualMonth() >= key) {
                continue;
            }
            b.addEntitled(credit);
            b.setLastAccrualMonth(key);
            post(b, LeaveBalanceTxn.Type.ACCRUAL, credit, "MONTH:" + key, "monthly accrual");
        }
    }

    @Transactional
    public int runAccrual(UUID companyId, LocalDate asOf) {
        int touched = 0;
        for (Employee e : employees.employedBetween(companyId, null, asOf, asOf)) {
            if (e.getStatus() != EmployeeStatus.ACTIVE) {
                continue;
            }
            for (LeaveType t : types.findByCompanyIdOrderByCode(companyId)) {
                if (t.isActive() && !t.isUnlimited() && t.eligible(e.getCategoryId())) {
                    accrue(e, t, ensure(e, t, asOf.getYear()), asOf);
                    touched++;
                }
            }
        }
        return touched;
    }

    /** Carries min(available, cap) of {@code fromYear} into the next year. */
    @Transactional
    public int runYearEnd(UUID companyId, int fromYear) {
        int carried = 0;
        for (LeaveType t : types.findByCompanyIdOrderByCode(companyId)) {
            if (t.isUnlimited() || t.getCfMaxDays().signum() <= 0) {
                continue;
            }
            for (LeaveBalance old : balances.findByLeaveTypeIdAndYear(t.getId(), fromYear)) {
                Employee e = employees.byIds(List.of(old.getEmployeeId())).get(old.getEmployeeId());
                if (e == null || e.getStatus() != EmployeeStatus.ACTIVE) {
                    continue;
                }
                LeaveBalance next = ensure(e, t, fromYear + 1);
                String ref = "CF:" + fromYear;
                if (ledger.existsByBalanceIdAndTypeAndReference(next.getId(), LeaveBalanceTxn.Type.CARRY_FORWARD, ref)) {
                    continue;
                }
                BigDecimal available = old.available();
                BigDecimal cf = available.signum() <= 0 ? BigDecimal.ZERO : available.min(t.getCfMaxDays());
                next.setCarriedForward(cf);
                post(next, LeaveBalanceTxn.Type.CARRY_FORWARD, cf, ref, "carried forward from " + fromYear);
                carried++;
            }
        }
        return carried;
    }

    /** Expires carried-forward days still unused after the type's expiry month. */
    @Transactional
    public int runExpiry(UUID companyId, LocalDate asOf) {
        int expired = 0;
        int year = asOf.getYear();
        for (LeaveType t : types.findByCompanyIdOrderByCode(companyId)) {
            if (t.isUnlimited() || t.getCfExpiryMonths() <= 0
                    || !asOf.isAfter(LeaveRules.carryForwardExpiry(year, t.getCfExpiryMonths()))) {
                continue;
            }
            for (LeaveBalance b : balances.findByLeaveTypeIdAndYear(t.getId(), year)) {
                String ref = "EXPIRY:" + year;
                if (b.getCarriedForward().signum() <= 0
                        || ledger.existsByBalanceIdAndTypeAndReference(b.getId(), LeaveBalanceTxn.Type.EXPIRY, ref)) {
                    continue;
                }
                BigDecimal unused = LeaveRules.unusedCarryForward(b.getCarriedForward(), b.getTaken());
                b.addExpired(unused);
                post(b, LeaveBalanceTxn.Type.EXPIRY, unused, ref, "carried-forward days expired");
                expired++;
            }
        }
        return expired;
    }
}
