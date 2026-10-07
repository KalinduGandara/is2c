package com.is2c.hrm.leave.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.approval.api.ApprovalHandler;
import com.is2c.hrm.approval.api.ApprovalOutcome;
import com.is2c.hrm.approval.api.ApprovalSubmission;
import com.is2c.hrm.approval.api.ApprovalTypes;
import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.attendance.api.ApprovedLeaveLookup;
import com.is2c.hrm.attendance.service.AttendanceCalculator.LeaveInfo;
import com.is2c.hrm.attendance.service.AttendanceLocks;
import com.is2c.hrm.attendance.service.AttendanceProcessor;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.InvalidStateException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeStatus;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.leave.domain.LeaveAttachment;
import com.is2c.hrm.leave.domain.LeaveAttachmentRepository;
import com.is2c.hrm.leave.domain.LeaveBalance;
import com.is2c.hrm.leave.domain.LeaveBalanceTxn;
import com.is2c.hrm.leave.domain.LeaveRequest;
import com.is2c.hrm.leave.domain.LeaveRequest.HalfDayPart;
import com.is2c.hrm.leave.domain.LeaveRequest.Status;
import com.is2c.hrm.leave.domain.LeaveRequestRepository;
import com.is2c.hrm.leave.domain.LeaveType;
import com.is2c.hrm.leave.domain.LeaveTypeRepository;
import com.is2c.hrm.platform.storage.FileStorage;
import com.is2c.hrm.platform.storage.Uploads;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.api.DaySchedule.DayType;
import com.is2c.hrm.roster.service.ScheduleResolver;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-063..066 leave applications, approval outcomes, cancellation and attendance integration. */
@Service
public class LeaveRequestService implements ApprovalHandler, ApprovedLeaveLookup {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final LeaveRequestRepository requests;
    private final LeaveAttachmentRepository attachments;
    private final LeaveTypeRepository types;
    private final LeaveBalanceService balances;
    private final EmployeeService employees;
    private final ScheduleResolver schedules;
    private final AttendanceLocks locks;
    private final AttendanceProcessor processor;
    private final ApprovalService approvals;
    private final FileStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public LeaveRequestService(LeaveRequestRepository requests, LeaveAttachmentRepository attachments,
            LeaveTypeRepository types, LeaveBalanceService balances, EmployeeService employees,
            ScheduleResolver schedules, AttendanceLocks locks, @Lazy AttendanceProcessor processor,
            @Lazy ApprovalService approvals, FileStorage storage, AuditService audit, Clock clock) {
        this.requests = requests;
        this.attachments = attachments;
        this.types = types;
        this.balances = balances;
        this.employees = employees;
        this.schedules = schedules;
        this.locks = locks;
        this.processor = processor;
        this.approvals = approvals;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    public record ApplyData(UUID employeeId, UUID leaveTypeId, LocalDate fromDate, LocalDate toDate,
            HalfDayPart halfDayPart, String reason) {
    }

    public record Content(LeaveAttachment attachment, InputStream stream) {
    }

    // ---------------------------------------------------------------- apply (FR-063)

    /** On behalf of an employee in scope (LEAVE:ADD). */
    @Transactional
    public LeaveRequest applyFor(ApplyData d, List<MultipartFile> files) {
        if (d.employeeId() == null) {
            throw new ValidationException("employeeId", "is required");
        }
        return apply(employees.get(d.employeeId()), d, files);
    }

    /** Self-service: the caller's linked employee. */
    @Transactional
    public LeaveRequest applyOwn(ApplyData d, List<MultipartFile> files) {
        UUID own = ownEmployeeId();
        return apply(employees.byIds(List.of(own)).get(own), d, files);
    }

    private LeaveRequest apply(Employee e, ApplyData d, List<MultipartFile> files) {
        List<FieldError> errors = new ArrayList<>();
        LeaveType type = d.leaveTypeId() == null ? null : types.findById(d.leaveTypeId())
                .filter(t -> t.getCompanyId().equals(e.getCompanyId())).orElse(null);
        if (type == null || !type.isActive()) {
            errors.add(new FieldError("leaveTypeId", "an active leave type of the employee's company is required"));
        }
        if (d.fromDate() == null || d.toDate() == null) {
            errors.add(new FieldError("toDate", "from and to dates are required"));
        } else if (d.toDate().isBefore(d.fromDate())) {
            errors.add(new FieldError("toDate", "cannot be before the from date"));
        } else if (d.fromDate().getYear() != d.toDate().getYear()) {
            errors.add(new FieldError("toDate", "a request cannot span two leave years; split it"));
        }
        if (d.halfDayPart() != null && d.fromDate() != null && !d.fromDate().equals(d.toDate())) {
            errors.add(new FieldError("halfDayPart", "half days are only possible for a single date"));
        }
        if (type != null && d.halfDayPart() != null && !type.isAllowHalfDay()) {
            errors.add(new FieldError("halfDayPart", "this leave type does not allow half days"));
        }
        if (e.getStatus() != EmployeeStatus.ACTIVE) {
            errors.add(new FieldError("employeeId", "the employee is not active"));
        }
        if (type != null && !type.eligible(e.getCategoryId())) {
            errors.add(new FieldError("leaveTypeId", "the employee is not eligible for this leave type"));
        }
        if (d.reason() != null && d.reason().length() > 1000) {
            errors.add(new FieldError("reason", "must be at most 1000 characters"));
        }
        throwIfAny(errors);

        List<LeaveRequest.Day> days = countDays(e, type, d);
        BigDecimal total = days.stream().map(LeaveRequest.Day::fraction).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            errors.add(new FieldError("toDate", "the selected dates contain no working days"));
        }
        for (LeaveRequest other : requests.findActiveOverlapping(e.getId(), d.fromDate(), d.toDate())) {
            boolean compatibleHalves = d.halfDayPart() != null && other.getHalfDayPart() != null
                    && d.halfDayPart() != other.getHalfDayPart() && other.getFromDate().equals(d.fromDate());
            if (!compatibleHalves) {
                errors.add(new FieldError("fromDate", "overlaps another " + other.getStatus().name().toLowerCase()
                        + " leave request (" + other.getFromDate() + " – " + other.getToDate() + ")"));
                break;
            }
        }
        LeaveBalance balance = type.isUnlimited() ? null : balances.ensure(e, type, d.fromDate().getYear());
        if (balance != null && !type.isAllowNegative() && balance.available().compareTo(total) < 0) {
            errors.add(new FieldError("leaveTypeId", "insufficient balance: " + balance.available().stripTrailingZeros()
                    .toPlainString() + " day(s) available"));
        }
        boolean hasFiles = files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty());
        if (type.getAttachmentAboveDays() != null && total.compareTo(type.getAttachmentAboveDays()) > 0 && !hasFiles) {
            errors.add(new FieldError("files", "a supporting document is required for more than "
                    + type.getAttachmentAboveDays().stripTrailingZeros().toPlainString() + " day(s)"));
        }
        throwIfAny(errors);
        locks.assertUnlocked(e.getCompanyId(), days.stream().map(LeaveRequest.Day::date).toList());

        LeaveRequest r = new LeaveRequest(e.getId(), e.getCompanyId(), type.getId(), d.fromDate(), d.toDate(),
                d.halfDayPart(), total, d.reason() == null ? null : d.reason().trim(), CurrentUser.get().id(), clock.instant());
        r.getLeaveDays().addAll(days);
        requests.save(r);
        if (hasFiles) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    storeAttachment(r, f);
                }
            }
        }
        if (balance != null) {
            balance.addPending(total);
            balances.post(balance, LeaveBalanceTxn.Type.PENDING, total, r.getId().toString(), null);
        }
        audit.record(AuditEntry.of("APPLY", "LEAVE_REQUEST", r.getId()).company(e.getCompanyId())
                .after(Map.of("employeeId", e.getId(), "type", type.getCode(), "from", d.fromDate(), "to", d.toDate(),
                        "days", total)));
        ApprovalOutcome outcome = approvals.submit(new ApprovalSubmission(ApprovalTypes.LEAVE_REQUEST, r.getId(),
                e.getCompanyId(), e.getId(), e.getEmployeeNumber() + " " + e.fullName() + ": " + type.getName() + " "
                        + d.fromDate() + (d.toDate().equals(d.fromDate()) ? "" : " – " + d.toDate()) + " ("
                        + total.stripTrailingZeros().toPlainString() + " day(s))"));
        r.setApprovalId(outcome.transactionId());
        return r;
    }

    /** FR-063 day counting from the resolved schedule. */
    List<LeaveRequest.Day> countDays(Employee e, LeaveType type, ApplyData d) {
        List<DaySchedule> resolved = schedules.resolve(List.of(e), d.fromDate(), d.toDate()).get(e.getId());
        List<LeaveRequest.Day> days = new ArrayList<>();
        for (DaySchedule s : resolved) {
            boolean counts = s.dayType() == DayType.WORKDAY
                    || (s.dayType() == DayType.WEEKLY_OFF && type.isCountWeeklyOff())
                    || (s.dayType() == DayType.HOLIDAY && type.isCountHoliday());
            if (counts) {
                days.add(new LeaveRequest.Day(s.date(), d.halfDayPart() != null ? HALF : BigDecimal.ONE, d.halfDayPart()));
            }
        }
        return days;
    }

    // ---------------------------------------------------------------- cancel (FR-064)

    /** Employee cancels their own PENDING request. */
    @Transactional
    public LeaveRequest cancelOwn(UUID id, String reason) {
        LeaveRequest r = find(id);
        if (!r.getEmployeeId().equals(ownEmployeeId())) {
            throw new NotFoundException("Leave request", id);
        }
        if (r.getStatus() != Status.PENDING) {
            throw new InvalidStateException("Only pending requests can be cancelled by the employee");
        }
        approvals.cancelByModule(ApprovalTypes.LEAVE_REQUEST, id, reason);
        release(r, Status.CANCELLED, reason);
        return r;
    }

    /** HR cancels a PENDING or APPROVED request (LEAVE:EDIT); approved leave returns taken days. */
    @Transactional
    public LeaveRequest cancelByHr(UUID id, String reason) {
        LeaveRequest r = find(id);
        employees.get(r.getEmployeeId());
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason", "a reason is required");
        }
        if (r.getStatus() == Status.PENDING) {
            approvals.cancelByModule(ApprovalTypes.LEAVE_REQUEST, id, reason);
            release(r, Status.CANCELLED, reason);
        } else if (r.getStatus() == Status.APPROVED) {
            locks.assertUnlocked(r.getCompanyId(), r.getLeaveDays().stream().map(LeaveRequest.Day::date).toList());
            LeaveType type = types.findById(r.getLeaveTypeId()).orElseThrow();
            if (!type.isUnlimited()) {
                LeaveBalance b = balance(r, type);
                b.addTaken(r.getDays().negate());
                balances.post(b, LeaveBalanceTxn.Type.RETURN, r.getDays(), id.toString(), reason);
            }
            r.decide(Status.CANCELLED, clock.instant(), reason.trim());
            audit.record(AuditEntry.of("CANCEL_APPROVED", "LEAVE_REQUEST", id).company(r.getCompanyId()).reason(reason));
            reprocess(r);
        } else {
            throw new InvalidStateException("The request is already " + r.getStatus());
        }
        return r;
    }

    // ---------------------------------------------------------------- ApprovalHandler (FR-064)

    @Override
    public String type() {
        return ApprovalTypes.LEAVE_REQUEST;
    }

    @Override
    public void onApproved(UUID referenceId) {
        LeaveRequest r = find(referenceId);
        // the period may have been locked after submission (FR-053): refuse rather than silently not reprocessing
        locks.assertUnlocked(r.getCompanyId(), r.getLeaveDays().stream().map(LeaveRequest.Day::date).toList());
        LeaveType type = types.findById(r.getLeaveTypeId()).orElseThrow();
        if (!type.isUnlimited()) {
            LeaveBalance b = balance(r, type);
            b.addPending(r.getDays().negate());
            b.addTaken(r.getDays());
            balances.post(b, LeaveBalanceTxn.Type.TAKEN, r.getDays(), referenceId.toString(), null);
        }
        r.decide(Status.APPROVED, clock.instant(), null);
        requests.flush();
        reprocess(r);
    }

    @Override
    public void onRejected(UUID referenceId, String comment) {
        release(find(referenceId), Status.REJECTED, comment);
    }

    @Override
    public void onCancelled(UUID referenceId) {
        release(find(referenceId), Status.CANCELLED, null);
    }

    private void release(LeaveRequest r, Status outcome, String note) {
        LeaveType type = types.findById(r.getLeaveTypeId()).orElseThrow();
        if (!type.isUnlimited()) {
            LeaveBalance b = balance(r, type);
            b.addPending(r.getDays().negate());
            balances.post(b, LeaveBalanceTxn.Type.RELEASE, r.getDays(), r.getId().toString(), note);
        }
        r.decide(outcome, clock.instant(), note == null ? null : note.trim());
        audit.record(AuditEntry.of(outcome.name(), "LEAVE_REQUEST", r.getId()).company(r.getCompanyId()).reason(note));
    }

    // ---------------------------------------------------------------- ApprovedLeaveLookup (attendance)

    @Override
    @Transactional(readOnly = true)
    public Map<Key, LeaveInfo> find(Collection<UUID> employeeIds, LocalDate from, LocalDate to) {
        Map<Key, LeaveInfo> result = new HashMap<>();
        if (employeeIds.isEmpty()) {
            return result;
        }
        Map<UUID, Boolean> paid = new HashMap<>();
        for (LeaveRequest r : requests.findApproved(employeeIds, from, to)) {
            boolean isPaid = paid.computeIfAbsent(r.getLeaveTypeId(), t -> types.findById(t).map(LeaveType::isPaid).orElse(true));
            for (LeaveRequest.Day day : r.getLeaveDays()) {
                if (day.date().isBefore(from) || day.date().isAfter(to)) {
                    continue;
                }
                Key key = new Key(r.getEmployeeId(), day.date());
                LeaveInfo existing = result.get(key);
                if (existing == null) {
                    result.put(key, new LeaveInfo(day.fraction(), day.part() == null ? null : day.part().name(), isPaid,
                            r.getId()));
                } else {
                    // Two half days on the same date (e.g. morning casual + afternoon annual) make a full day.
                    BigDecimal sum = existing.fraction().add(day.fraction()).min(BigDecimal.ONE);
                    result.put(key, new LeaveInfo(sum, null, existing.paid() && isPaid, existing.requestId()));
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- queries & attachments

    @Transactional(readOnly = true)
    public LeaveRequest get(UUID id) {
        LeaveRequest r = find(id);
        employees.get(r.getEmployeeId());
        return r;
    }

    @Transactional(readOnly = true)
    public LeaveRequest getOwn(UUID id) {
        LeaveRequest r = find(id);
        if (!r.getEmployeeId().equals(ownEmployeeId())) {
            throw new NotFoundException("Leave request", id);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> own() {
        return requests.findByEmployeeIdOrderByFromDateDesc(ownEmployeeId());
    }

    @Transactional(readOnly = true)
    public List<LeaveAttachment> attachmentsOf(UUID requestId) {
        return attachments.findByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public Content openAttachment(LeaveRequest r, UUID attachmentId) {
        LeaveAttachment a = attachments.findById(attachmentId).filter(x -> x.getRequestId().equals(r.getId()))
                .orElseThrow(() -> new NotFoundException("Attachment", attachmentId));
        try {
            return new Content(a, storage.open(a.getStorageKey()));
        } catch (IOException e) {
            throw new NotFoundException("Attachment content", attachmentId);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void storeAttachment(LeaveRequest r, MultipartFile f) {
        Uploads.Checked c = Uploads.check(f, "files");
        try {
            String key = storage.store(new ByteArrayInputStream(c.bytes()));
            attachments.save(new LeaveAttachment(r.getId(), c.fileName(), c.contentType(), c.bytes().length, key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void reprocess(LeaveRequest r) {
        processor.process(r.getCompanyId(), List.of(r.getEmployeeId()), r.getFromDate(), r.getToDate());
    }

    private LeaveBalance balance(LeaveRequest r, LeaveType type) {
        Employee e = employees.byIds(List.of(r.getEmployeeId())).get(r.getEmployeeId());
        return balances.ensure(e, type, r.getFromDate().getYear());
    }

    private LeaveRequest find(UUID id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Leave request", id));
    }

    private static UUID ownEmployeeId() {
        UUID own = CurrentUser.get().employeeId();
        if (own == null) {
            throw new ForbiddenException("No employee is linked to this user");
        }
        return own;
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}
