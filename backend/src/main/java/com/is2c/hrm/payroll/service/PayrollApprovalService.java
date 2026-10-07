package com.is2c.hrm.payroll.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.api.ApprovalHandler;
import com.is2c.hrm.approval.api.ApprovalSubmission;
import com.is2c.hrm.approval.api.ApprovalTypes;
import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.InvalidStateException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.payroll.domain.PayrollException;
import com.is2c.hrm.payroll.domain.PayrollExceptionRepository;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.domain.PayrollPeriod.Status;
import com.is2c.hrm.payroll.domain.PayrollPeriodRepository;
import com.is2c.hrm.payroll.domain.PayrollResult;
import com.is2c.hrm.payroll.domain.PayrollResultLine;
import com.is2c.hrm.payroll.domain.PayrollResultLineRepository;
import com.is2c.hrm.payroll.domain.PayrollResultRepository;
import com.is2c.hrm.platform.db.DatabasePlatform;
import com.is2c.hrm.security.api.CurrentUser;

/**
 * FR-087 review workflow: submit (→ PAYROLL_APPROVAL transaction), send back, approval callbacks and finalization.
 * Attendance locking follows the period status (FR-053, see {@link PayrollPeriod#locksAttendance()}).
 */
@Service
public class PayrollApprovalService implements ApprovalHandler {

    static final String PAYSLIP_SEQUENCE = "PAYSLIP";

    private final PayrollPeriodService periodService;
    private final PayrollPeriodRepository periods;
    private final PayrollExceptionRepository exceptions;
    private final PayrollResultRepository results;
    private final PayrollResultLineRepository lines;
    private final ApprovalService approvals;
    private final DatabasePlatform platform;
    private final AuditService audit;
    private final Clock clock;

    public PayrollApprovalService(PayrollPeriodService periodService, PayrollPeriodRepository periods,
            PayrollExceptionRepository exceptions, PayrollResultRepository results, PayrollResultLineRepository lines,
            @Lazy ApprovalService approvals, DatabasePlatform platform, AuditService audit, Clock clock) {
        this.periodService = periodService;
        this.periods = periods;
        this.exceptions = exceptions;
        this.results = results;
        this.lines = lines;
        this.approvals = approvals;
        this.platform = platform;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PayrollPeriod submit(UUID id) {
        PayrollPeriod p = periodService.get(id);
        if (p.getStatus() != Status.DRAFT) {
            throw new InvalidStateException("Only DRAFT periods can be submitted (current: " + p.getStatus() + ")");
        }
        if (p.isStale()) {
            throw new ValidationException("period", "the period is stale; reprocess it before submitting");
        }
        long blocking = exceptions.findByPeriodId(id).stream().filter(PayrollException::isBlocking).count();
        if (blocking > 0) {
            throw new ValidationException("period", blocking + " blocking exception(s) must be resolved first");
        }
        transition(p, Status.IN_REVIEW, "SUBMIT", null);
        var outcome = approvals.submit(new ApprovalSubmission(ApprovalTypes.PAYROLL_APPROVAL, id, p.getCompanyId(), null,
                String.format("Payroll %d-%02d", p.getYear(), p.getMonth())));
        p.setApprovalId(outcome.transactionId());
        return p;
    }

    @Transactional
    public PayrollPeriod sendBack(UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new ValidationException("comment", "a comment is required when sending back");
        }
        PayrollPeriod p = periodService.get(id);
        if (p.getStatus() != Status.IN_REVIEW) {
            throw new InvalidStateException("Only periods in review can be sent back (current: " + p.getStatus() + ")");
        }
        approvals.cancelByModule(ApprovalTypes.PAYROLL_APPROVAL, id, comment.trim());
        transition(p, Status.DRAFT, "SEND_BACK", comment.trim());
        return p;
    }

    @Transactional
    public PayrollPeriod finalizePeriod(UUID id) {
        PayrollPeriod p = periodService.get(id);
        if (p.getStatus() != Status.APPROVED) {
            throw new InvalidStateException("Only APPROVED periods can be finalized (current: " + p.getStatus() + ")");
        }
        List<PayrollResult> all = results.findByPeriodIdOrderByEmployeeNumber(id);
        for (PayrollResult r : all) {
            long seq = platform.nextSequenceValue(p.getCompanyId(), PAYSLIP_SEQUENCE);
            r.lock(String.format("PS-%d%02d-%06d", p.getYear(), p.getMonth(), seq));
        }
        if (!all.isEmpty()) {
            lines.findByResultIdIn(all.stream().map(PayrollResult::getId).toList()).forEach(PayrollResultLine::lock);
        }
        Instant now = clock.instant();
        Status before = p.getStatus();
        p.finalize(now, CurrentUser.get().id());
        audit.record(AuditEntry.of("FINALIZE", "PAYROLL_PERIOD", id).company(p.getCompanyId())
                .before(Map.of("status", before)).after(Map.of("status", Status.FINALIZED, "payslips", all.size())));
        return p;
    }

    // ---------------------------------------------------------------- ApprovalHandler

    @Override
    public String type() {
        return ApprovalTypes.PAYROLL_APPROVAL;
    }

    @Override
    public void onApproved(UUID referenceId) {
        PayrollPeriod p = find(referenceId);
        if (p.getStatus() == Status.IN_REVIEW) {
            transition(p, Status.APPROVED, "APPROVE", null);
        }
    }

    @Override
    public void onRejected(UUID referenceId, String comment) {
        PayrollPeriod p = find(referenceId);
        if (p.getStatus() == Status.IN_REVIEW) {
            transition(p, Status.DRAFT, "REJECT", comment);
        }
    }

    @Override
    public void onCancelled(UUID referenceId) {
        PayrollPeriod p = find(referenceId);
        if (p.getStatus() == Status.IN_REVIEW) {
            transition(p, Status.DRAFT, "CANCEL", null);
        }
    }

    private void transition(PayrollPeriod p, Status to, String action, String reason) {
        Status from = p.getStatus();
        p.setStatus(to);
        audit.record(AuditEntry.of(action, "PAYROLL_PERIOD", p.getId()).company(p.getCompanyId())
                .before(Map.of("status", from)).after(Map.of("status", to)).reason(reason));
    }

    private PayrollPeriod find(UUID id) {
        return periods.findById(id).orElseThrow(() -> new NotFoundException("Payroll period", id));
    }
}
