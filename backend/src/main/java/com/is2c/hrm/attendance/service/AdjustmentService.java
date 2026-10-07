package com.is2c.hrm.attendance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.api.ApprovalHandler;
import com.is2c.hrm.approval.api.ApprovalSubmission;
import com.is2c.hrm.approval.api.ApprovalTypes;
import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.attendance.domain.AttendanceAdjustment;
import com.is2c.hrm.attendance.domain.AttendanceAdjustment.Status;
import com.is2c.hrm.attendance.domain.AttendanceAdjustment.Type;
import com.is2c.hrm.attendance.domain.AttendanceAdjustmentRepository;
import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.domain.AttendanceDailyRepository;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.LockedException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.api.PermissionAction;
import com.is2c.hrm.security.api.PermissionModule;

/** FR-050..053 manual attendance creation/correction with mandatory reason, approval and lock handling. */
@Service
public class AdjustmentService implements ApprovalHandler {

    private final AttendanceAdjustmentRepository adjustments;
    private final AttendanceDailyRepository dailies;
    private final EmployeeService employees;
    private final AttendanceProcessor processor;
    private final AttendanceLocks locks;
    private final ApprovalService approvals;
    private final OrgDirectory org;
    private final AuditService audit;
    private final Clock clock;

    public AdjustmentService(AttendanceAdjustmentRepository adjustments, AttendanceDailyRepository dailies,
            EmployeeService employees, AttendanceProcessor processor, AttendanceLocks locks,
            @org.springframework.context.annotation.Lazy ApprovalService approvals, OrgDirectory org,
            AuditService audit, Clock clock) {
        this.adjustments = adjustments;
        this.dailies = dailies;
        this.employees = employees;
        this.processor = processor;
        this.locks = locks;
        this.approvals = approvals;
        this.org = org;
        this.audit = audit;
        this.clock = clock;
    }

    public record AdjustmentRequest(UUID employeeId, LocalDate date, LocalDateTime in, LocalDateTime out,
            String reason, Boolean overrideLock) {
    }

    @Transactional
    public AttendanceAdjustment request(AdjustmentRequest r) {
        if (r.employeeId() == null || r.date() == null) {
            throw new ValidationException("Validation failed", List.of(new FieldError("employeeId", "is required"),
                    new FieldError("date", "is required")));
        }
        Employee e = employees.get(r.employeeId());
        AuthenticatedUser user = CurrentUser.get();
        AttendanceDaily daily = dailies.findByEmployeeIdAndWorkDate(e.getId(), r.date()).orElse(null);
        boolean hasTimes = daily != null && (daily.getFirstIn() != null || daily.getLastOut() != null);
        Type type = hasTimes ? Type.EDIT : Type.CREATE;
        String needed = Permissions.of(PermissionModule.ATTENDANCE, type == Type.CREATE ? PermissionAction.ADD : PermissionAction.EDIT);
        if (!user.hasPermission(needed)) {
            throw new ForbiddenException("This change requires " + needed);
        }
        LocalDateTime newIn = r.in() != null ? r.in() : type == Type.EDIT ? daily.getFirstIn() : null;
        LocalDateTime newOut = r.out() != null ? r.out() : type == Type.EDIT ? daily.getLastOut() : null;

        List<FieldError> errors = new ArrayList<>();
        if (r.reason() == null || r.reason().isBlank()) {
            errors.add(new FieldError("reason", "a reason is required"));
        } else if (r.reason().length() > 500) {
            errors.add(new FieldError("reason", "must be at most 500 characters"));
        }
        if (r.in() == null && r.out() == null) {
            errors.add(new FieldError("in", "provide an IN and/or OUT time"));
        }
        if (newIn == null || newOut == null) {
            errors.add(new FieldError("out", "both IN and OUT times are required"));
        } else if (!newOut.isAfter(newIn)) {
            errors.add(new FieldError("out", "OUT must be after IN"));
        } else if (newOut.isAfter(newIn.plusHours(24))) {
            errors.add(new FieldError("out", "a single day cannot exceed 24 hours"));
        }
        if (newIn != null && (newIn.toLocalDate().isBefore(r.date().minusDays(1)) || newIn.toLocalDate().isAfter(r.date()))) {
            errors.add(new FieldError("in", "IN must be on the work date (or the evening before for night shifts)"));
        }
        LocalDate today = LocalDate.now(clock.withZone(org.settings(e.getCompanyId()).timeZone()));
        if (r.date().isAfter(today)) {
            errors.add(new FieldError("date", "future dates cannot be adjusted"));
        }
        if (r.date().isBefore(e.getJoinDate()) || (e.getExitDate() != null && r.date().isAfter(e.getExitDate()))) {
            errors.add(new FieldError("date", "the employee was not employed on this date"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        boolean override = Boolean.TRUE.equals(r.overrideLock());
        if (locks.isLocked(e.getCompanyId(), r.date())) {
            if (!override) {
                throw new LockedException(AttendanceLocks.CODE, "Attendance on " + r.date() + " is locked by payroll");
            }
            if (!user.hasPermission(Permissions.of(PermissionModule.ATTENDANCE, PermissionAction.CONFIGURE))) {
                throw new ForbiddenException("Overriding a payroll lock requires ATTENDANCE:CONFIGURE");
            }
        } else {
            override = false;
        }
        if (adjustments.existsByEmployeeIdAndWorkDateAndStatus(e.getId(), r.date(), Status.PENDING)) {
            throw new ValidationException("date", "a correction for this day is already awaiting approval");
        }

        AttendanceAdjustment adj = adjustments.save(new AttendanceAdjustment(e.getId(), e.getCompanyId(), r.date(),
                type, newIn, newOut, daily == null ? null : daily.getFirstIn(), daily == null ? null : daily.getLastOut(),
                daily == null ? null : daily.getStatus(), r.reason().trim(), override, user.id(), clock.instant()));
        audit.record(AuditEntry.of(type == Type.CREATE ? "MANUAL_CREATE" : "MANUAL_EDIT", "ATTENDANCE_ADJUSTMENT",
                adj.getId()).company(e.getCompanyId())
                .before(Map.of("employeeId", e.getId(), "date", r.date(), "in", String.valueOf(adj.getBeforeIn()),
                        "out", String.valueOf(adj.getBeforeOut()), "status", String.valueOf(adj.getBeforeStatus())))
                .after(Map.of("employeeId", e.getId(), "date", r.date(), "in", newIn, "out", newOut))
                .reason(r.reason()));
        if (override) {
            audit.record(AuditEntry.of("LOCK_OVERRIDE", "ATTENDANCE_ADJUSTMENT", adj.getId()).company(e.getCompanyId())
                    .reason(r.reason()));
        }
        var outcome = approvals.submit(new ApprovalSubmission(ApprovalTypes.ATTENDANCE_CORRECTION, adj.getId(),
                e.getCompanyId(), e.getId(), "Attendance correction " + e.getEmployeeNumber() + " " + r.date()));
        adj.setApprovalId(outcome.transactionId());
        return adj;
    }

    @Transactional(readOnly = true)
    public List<AttendanceAdjustment> list(UUID companyId, LocalDate from, LocalDate to) {
        java.util.Set<UUID> visible = employees.employedInScope(companyId, null, null, from, to).stream()
                .map(Employee::getId).collect(java.util.stream.Collectors.toSet());
        return adjustments.findByCompanyIdAndWorkDateBetweenOrderByRequestedAtDesc(companyId, from, to).stream()
                .filter(a -> visible.contains(a.getEmployeeId())).toList();
    }

    // ---------------------------------------------------------------- ApprovalHandler

    @Override
    public String type() {
        return ApprovalTypes.ATTENDANCE_CORRECTION;
    }

    @Override
    public void onApproved(UUID referenceId) {
        AttendanceAdjustment adj = find(referenceId);
        adj.decide(Status.APPLIED, clock.instant());
        adjustments.flush();
        processor.process(adj.getCompanyId(), List.of(adj.getEmployeeId()), adj.getWorkDate(), adj.getWorkDate(),
                adj.isOverrideLock());
    }

    @Override
    public void onRejected(UUID referenceId, String comment) {
        find(referenceId).decide(Status.REJECTED, clock.instant());
    }

    @Override
    public void onCancelled(UUID referenceId) {
        find(referenceId).decide(Status.CANCELLED, clock.instant());
    }

    private AttendanceAdjustment find(UUID id) {
        return adjustments.findById(id).orElseThrow(() -> new NotFoundException("Adjustment", id));
    }
}
