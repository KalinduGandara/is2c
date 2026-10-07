package com.is2c.hrm.leave.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.leave.domain.LeaveBalance;
import com.is2c.hrm.leave.domain.LeaveBalanceTxn;
import com.is2c.hrm.leave.domain.LeaveType;
import com.is2c.hrm.leave.service.LeaveBalanceService;
import com.is2c.hrm.leave.service.LeaveTypeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.security.api.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class LeaveTypeController {

    private final LeaveTypeService types;
    private final LeaveBalanceService balances;
    private final DataScopeService scope;

    public LeaveTypeController(LeaveTypeService types, LeaveBalanceService balances, DataScopeService scope) {
        this.types = types;
        this.balances = balances;
        this.scope = scope;
    }

    public record LeaveTypeDto(UUID id, UUID companyId, String code, String name, boolean paid, BigDecimal annualDays,
            LeaveType.AccrualMethod accrualMethod, boolean proRata, BigDecimal cfMaxDays, int cfExpiryMonths,
            boolean allowHalfDay, boolean allowNegative, boolean unlimited, boolean countWeeklyOff,
            boolean countHoliday, BigDecimal attachmentAboveDays, Set<UUID> eligibleCategoryIds, String color,
            boolean active, Long version) {
        static LeaveTypeDto of(LeaveType t) {
            return new LeaveTypeDto(t.getId(), t.getCompanyId(), t.getCode(), t.getName(), t.isPaid(), t.getAnnualDays(),
                    t.getAccrualMethod(), t.isProRata(), t.getCfMaxDays(), t.getCfExpiryMonths(), t.isAllowHalfDay(),
                    t.isAllowNegative(), t.isUnlimited(), t.isCountWeeklyOff(), t.isCountHoliday(),
                    t.getAttachmentAboveDays(), Set.copyOf(t.getEligibleCategoryIds()), t.getColor(), t.isActive(),
                    t.getVersion());
        }
    }

    public record BalanceDto(UUID id, UUID leaveTypeId, String leaveTypeCode, String leaveTypeName, boolean unlimited,
            int year, BigDecimal opening, BigDecimal entitled, BigDecimal carriedForward, BigDecimal adjustment,
            BigDecimal taken, BigDecimal pending, BigDecimal expired, BigDecimal available) {
        static BalanceDto of(LeaveBalanceService.BalanceView v, int year) {
            LeaveBalance b = v.balance();
            LeaveType t = v.type();
            if (b == null) {
                return new BalanceDto(null, t.getId(), t.getCode(), t.getName(), true, year, null, null, null, null,
                        null, null, null, null);
            }
            return new BalanceDto(b.getId(), t.getId(), t.getCode(), t.getName(), false, year, b.getOpening(),
                    b.getEntitled(), b.getCarriedForward(), b.getAdjustment(), b.getTaken(), b.getPending(),
                    b.getExpired(), b.available());
        }
    }

    public record BalancePosting(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days, String reason) {
    }

    public record LedgerDto(LeaveBalanceTxn.Type type, BigDecimal days, String reference, String reason,
            Instant createdAt) {
    }

    public record JobResult(int affected) {
    }

    // Leave types (FR-060) — readable by every user of the company (needed to apply for leave).

    @GetMapping("/companies/{companyId}/leave-types")
    public List<LeaveTypeDto> list(@PathVariable UUID companyId) {
        return types.list(companyId).stream().map(LeaveTypeDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/leave-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAVE:CONFIGURE')")
    public LeaveTypeDto create(@PathVariable UUID companyId, @RequestBody LeaveTypeService.LeaveTypeData body) {
        return LeaveTypeDto.of(types.create(companyId, body));
    }

    @PutMapping("/leave-types/{id}")
    @PreAuthorize("hasAuthority('LEAVE:CONFIGURE')")
    public LeaveTypeDto update(@PathVariable UUID id, @RequestBody LeaveTypeService.LeaveTypeData body) {
        return LeaveTypeDto.of(types.update(id, body));
    }

    // Balances (FR-061)

    @GetMapping("/leave/balances")
    @PreAuthorize("hasAuthority('LEAVE:VIEW')")
    public List<BalanceDto> balances(@RequestParam UUID employeeId, @RequestParam int year) {
        return balances.forEmployee(employeeId, year).stream().map(v -> BalanceDto.of(v, year)).toList();
    }

    @GetMapping("/me/leave/balances")
    public List<BalanceDto> myBalances(@RequestParam int year) {
        UUID employeeId = CurrentUser.get().employeeId();
        if (employeeId == null) {
            throw new NotFoundException("No employee is linked to this user");
        }
        return balances.own(employeeId, year).stream().map(v -> BalanceDto.of(v, year)).toList();
    }

    @PostMapping("/leave/balances/opening")
    @PreAuthorize("hasAuthority('LEAVE:EDIT')")
    public JobResult opening(@RequestBody BalancePosting body) {
        balances.setOpening(body.employeeId(), body.leaveTypeId(), body.year(), body.days(), body.reason());
        return new JobResult(1);
    }

    @PostMapping("/leave/balances/adjust")
    @PreAuthorize("hasAuthority('LEAVE:EDIT')")
    public JobResult adjust(@RequestBody BalancePosting body) {
        balances.adjust(body.employeeId(), body.leaveTypeId(), body.year(), body.days(), body.reason());
        return new JobResult(1);
    }

    @GetMapping("/leave/balances/{id}/ledger")
    @PreAuthorize("hasAuthority('LEAVE:VIEW')")
    public List<LedgerDto> ledger(@PathVariable UUID id) {
        return balances.ledger(id).stream().map(t -> new LedgerDto(t.getType(), t.getDays(), t.getReference(),
                t.getReason(), t.getCreatedAt())).toList();
    }

    // Jobs (FR-062), on demand

    @PostMapping("/leave/jobs/accrual")
    @PreAuthorize("hasAuthority('LEAVE:CONFIGURE')")
    public JobResult accrual(@RequestParam UUID companyId, @RequestParam LocalDate asOf) {
        scope.assertCompanyWritable(companyId);
        return new JobResult(balances.runAccrual(companyId, asOf));
    }

    @PostMapping("/leave/jobs/year-end")
    @PreAuthorize("hasAuthority('LEAVE:CONFIGURE')")
    public JobResult yearEnd(@RequestParam UUID companyId, @RequestParam int year) {
        scope.assertCompanyWritable(companyId);
        return new JobResult(balances.runYearEnd(companyId, year));
    }

    @PostMapping("/leave/jobs/expiry")
    @PreAuthorize("hasAuthority('LEAVE:CONFIGURE')")
    public JobResult expiry(@RequestParam UUID companyId, @RequestParam LocalDate asOf) {
        scope.assertCompanyWritable(companyId);
        return new JobResult(balances.runExpiry(companyId, asOf));
    }
}
