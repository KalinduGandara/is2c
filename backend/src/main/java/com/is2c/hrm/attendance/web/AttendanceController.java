package com.is2c.hrm.attendance.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.attendance.api.EventDirection;
import com.is2c.hrm.attendance.domain.AttendanceAdjustment;
import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.service.AdjustmentService;
import com.is2c.hrm.attendance.service.AttendanceCalculator.DayStatus;
import com.is2c.hrm.attendance.service.OtApprovalService;
import com.is2c.hrm.attendance.service.AttendanceProcessor;
import com.is2c.hrm.attendance.service.AttendanceQueryService;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.web.PageResponse;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.roster.api.DaySchedule.DayType;
import com.is2c.hrm.security.api.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {

    private final AttendanceProcessor processor;
    private final AttendanceQueryService query;
    private final EmployeeService employees;
    private final DataScopeService scope;
    private final OrgDirectory org;
    private final AdjustmentService adjustments;
    private final OtApprovalService otApprovals;

    public AttendanceController(AttendanceProcessor processor, AttendanceQueryService query, EmployeeService employees,
            DataScopeService scope, OrgDirectory org, AdjustmentService adjustments, OtApprovalService otApprovals) {
        this.adjustments = adjustments;
        this.otApprovals = otApprovals;
        this.processor = processor;
        this.query = query;
        this.employees = employees;
        this.scope = scope;
        this.org = org;
    }

    public record ProcessRequest(UUID companyId, List<UUID> employeeIds, LocalDate from, LocalDate to) {
    }

    public record DailyDto(UUID id, UUID employeeId, String employeeNumber, String employeeName, LocalDate date,
            UUID shiftId, DayType dayType, DayStatus status, LocalDateTime firstIn, LocalDateTime lastOut,
            int scheduledMinutes, int workedMinutes, int lateMinutes, int earlyMinutes, int shortMinutes,
            int otWorkdayMinutes, int otOffdayMinutes, int otHolidayMinutes, int extraMinutes,
            boolean otRequiresApproval, boolean otApproved, boolean otRejected, BigDecimal absentFraction,
            BigDecimal leaveFraction, boolean leavePaid, boolean manual, List<String> exceptions) {
        static DailyDto of(AttendanceDaily d, Employee e) {
            return new DailyDto(d.getId(), d.getEmployeeId(), e == null ? null : e.getEmployeeNumber(),
                    e == null ? null : e.fullName(), d.getWorkDate(), d.getShiftId(), d.getDayType(), d.getStatus(),
                    d.getFirstIn(), d.getLastOut(), d.getScheduledMinutes(), d.getWorkedMinutes(), d.getLateMinutes(),
                    d.getEarlyMinutes(), d.getShortMinutes(), d.getOtWorkdayMinutes(), d.getOtOffdayMinutes(),
                    d.getOtHolidayMinutes(), d.getExtraMinutes(), d.isOtRequiresApproval(), d.isOtApproved(),
                    d.isOtRejected(), d.getAbsentFraction(), d.getLeaveFraction(), d.isLeavePaid(), d.isManual(),
                    d.exceptionList());
        }
    }

    public record EventDto(UUID id, LocalDateTime time, EventDirection direction, String deviceId, String source) {
    }

    public record AdjustmentDto(UUID id, AttendanceAdjustment.Type type, LocalDateTime newIn, LocalDateTime newOut,
            LocalDateTime beforeIn, LocalDateTime beforeOut, DayStatus beforeStatus, String reason,
            AttendanceAdjustment.Status status, boolean overrideLock, UUID requestedBy, Instant requestedAt) {
        public static AdjustmentDto of(AttendanceAdjustment a) {
            return new AdjustmentDto(a.getId(), a.getType(), a.getNewIn(), a.getNewOut(), a.getBeforeIn(),
                    a.getBeforeOut(), a.getBeforeStatus(), a.getReason(), a.getStatus(), a.isOverrideLock(),
                    a.getRequestedBy(), a.getRequestedAt());
        }
    }

    public record DayDetailDto(DailyDto daily, List<EventDto> events, List<AdjustmentDto> adjustments) {
    }

    @PostMapping("/attendance/process")
    @PreAuthorize("hasAuthority('ATTENDANCE:EDIT')")
    public AttendanceProcessor.ProcessResult process(@RequestBody ProcessRequest r) {
        scope.assertCompanyReadable(r.companyId());
        Set<UUID> allowed = employees.employedInScope(r.companyId(), null, null, r.from(), r.to()).stream()
                .map(Employee::getId).collect(Collectors.toSet());
        Set<UUID> targets = r.employeeIds() == null || r.employeeIds().isEmpty() ? allowed
                : r.employeeIds().stream().filter(allowed::contains).collect(Collectors.toSet());
        if (targets.isEmpty()) {
            return new AttendanceProcessor.ProcessResult(0, 0, 0);
        }
        return processor.process(r.companyId(), targets, r.from(), r.to());
    }

    @GetMapping("/attendance/daily")
    @PreAuthorize("hasAuthority('ATTENDANCE:VIEW')")
    public PageResponse<DailyDto> daily(@RequestParam UUID companyId, @RequestParam LocalDate from,
            @RequestParam LocalDate to, @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID employeeId, @RequestParam(required = false) DayStatus status,
            @RequestParam(required = false) String exception, Pageable pageable) {
        Page<AttendanceDaily> page = query.daily(new AttendanceQueryService.DailyFilter(companyId, from, to,
                departmentId, employeeId, status, exception), pageable);
        Set<UUID> ids = new HashSet<>();
        page.forEach(d -> ids.add(d.getEmployeeId()));
        Map<UUID, Employee> byId = employees.byIds(ids);
        return PageResponse.of(page, d -> DailyDto.of(d, byId.get(d.getEmployeeId())));
    }

    @GetMapping("/attendance/daily/{employeeId}/{date}")
    @PreAuthorize("hasAuthority('ATTENDANCE:VIEW')")
    public DayDetailDto day(@PathVariable UUID employeeId, @PathVariable LocalDate date) {
        return toDetail(query.day(employeeId, date));
    }

    /** FR-054 days with any (or the given) exception. */
    @GetMapping("/attendance/exceptions")
    @PreAuthorize("hasAuthority('ATTENDANCE:VIEW')")
    public PageResponse<DailyDto> exceptions(@RequestParam UUID companyId, @RequestParam LocalDate from,
            @RequestParam LocalDate to, @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID departmentId, Pageable pageable) {
        return daily(companyId, from, to, departmentId, null, null, type == null || type.isBlank() ? "ANY" : type,
                pageable);
    }

    @PostMapping("/attendance/adjustments")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:ADD', 'ATTENDANCE:EDIT')")
    public AdjustmentDto adjust(@RequestBody AdjustmentService.AdjustmentRequest body) {
        return AdjustmentDto.of(adjustments.request(body));
    }

    @GetMapping("/attendance/adjustments")
    @PreAuthorize("hasAuthority('ATTENDANCE:VIEW')")
    public List<AdjustmentDto> adjustments(@RequestParam UUID companyId, @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        scope.assertCompanyReadable(companyId);
        return adjustments.list(companyId, from, to).stream().map(AdjustmentDto::of).toList();
    }

    public record OtDecision(List<UUID> dailyIds, String comment) {
    }

    public record DecisionResult(int updated) {
    }

    @PostMapping("/attendance/ot/approve")
    @PreAuthorize("hasAuthority('ATTENDANCE:APPROVE')")
    public DecisionResult approveOt(@RequestBody OtDecision body) {
        return new DecisionResult(otApprovals.decide(body.dailyIds(), true, body.comment()));
    }

    @PostMapping("/attendance/ot/reject")
    @PreAuthorize("hasAuthority('ATTENDANCE:APPROVE')")
    public DecisionResult rejectOt(@RequestBody OtDecision body) {
        return new DecisionResult(otApprovals.decide(body.dailyIds(), false, body.comment()));
    }

    @GetMapping("/me/attendance")
    public List<DailyDto> mine(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        UUID employeeId = CurrentUser.get().employeeId();
        if (employeeId == null) {
            throw new NotFoundException("No employee is linked to this user");
        }
        return query.own(employeeId, from, to).stream().map(d -> DailyDto.of(d, null)).toList();
    }

    DayDetailDto toDetail(AttendanceQueryService.DayDetail d) {
        ZoneId zone = org.settings(d.employee().getCompanyId()).timeZone();
        return new DayDetailDto(d.daily() == null ? null : DailyDto.of(d.daily(), d.employee()),
                d.events().stream().map(e -> new EventDto(e.getId(), e.getEventTime().atZone(zone).toLocalDateTime(),
                        e.getDirection(), e.getDeviceId(), e.getSourceId().toString())).toList(),
                d.adjustments().stream().map(AdjustmentDto::of).toList());
    }
}
