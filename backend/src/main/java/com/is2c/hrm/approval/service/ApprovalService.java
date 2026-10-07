package com.is2c.hrm.approval.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.api.ApprovalEvent;
import com.is2c.hrm.approval.api.ApprovalOutcome;
import com.is2c.hrm.approval.api.ApprovalSubmission;
import com.is2c.hrm.approval.domain.ApprovalAction;
import com.is2c.hrm.approval.domain.ApprovalAction.Action;
import com.is2c.hrm.approval.domain.ApprovalActionRepository;
import com.is2c.hrm.approval.domain.ApprovalTransaction;
import com.is2c.hrm.approval.domain.ApprovalTransaction.Status;
import com.is2c.hrm.approval.domain.ApprovalTransactionRepository;
import com.is2c.hrm.approval.domain.ApprovalWorkflow;
import com.is2c.hrm.approval.domain.WorkflowLevel;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.ConflictException;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.api.PermissionAction;
import com.is2c.hrm.security.api.PermissionModule;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** FR-071/FR-072 approval transactions: submission, actions, inbox and history. */
@Service
public class ApprovalService {

    private final ApprovalTransactionRepository transactions;
    private final ApprovalActionRepository actions;
    private final WorkflowService workflows;
    private final EligibilityResolver eligibility;
    private final HandlerRegistry handlers;
    private final EmployeeService employees;
    private final DataScopeService scope;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    public ApprovalService(ApprovalTransactionRepository transactions, ApprovalActionRepository actions,
            WorkflowService workflows, EligibilityResolver eligibility, HandlerRegistry handlers,
            EmployeeService employees, DataScopeService scope, AuditService audit, ApplicationEventPublisher events,
            JsonMapper json, Clock clock) {
        this.transactions = transactions;
        this.actions = actions;
        this.workflows = workflows;
        this.eligibility = eligibility;
        this.handlers = handlers;
        this.employees = employees;
        this.scope = scope;
        this.audit = audit;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    public record TransactionView(ApprovalTransaction transaction, List<WorkflowLevel> levels,
            List<ApprovalAction> history, Set<UUID> currentApprovers, boolean canAct, boolean canCancel) {
    }

    // ---------------------------------------------------------------- submission

    /** Submits for approval; auto-approves (invoking the handler) when no enabled workflow applies. */
    @Transactional
    public ApprovalOutcome submit(ApprovalSubmission s) {
        handlers.get(s.type());
        UUID requester = CurrentUser.find().map(AuthenticatedUser::id).orElse(null);
        EmployeePlacement placement = placement(s.subjectEmployeeId());
        Optional<ApprovalWorkflow> workflow = workflows.resolve(s.type(), s.companyId(), placement);
        Instant now = clock.instant();
        List<WorkflowLevel> levels = workflow.map(w -> List.copyOf(w.getLevels())).orElse(List.of());
        ApprovalTransaction txn = transactions.save(new ApprovalTransaction(s.type(), s.referenceId(), s.companyId(),
                requester, s.subjectEmployeeId(), json.writeValueAsString(levels), truncate(s.summary()), now));
        actions.save(new ApprovalAction(txn.getId(), 1, Action.SUBMIT, requester, null, null, now));
        audit.record(AuditEntry.of("SUBMIT", "APPROVAL", txn.getId()).company(s.companyId())
                .after(java.util.Map.of("type", s.type(), "referenceId", s.referenceId())));
        if (levels.isEmpty()) {
            txn.complete(Status.APPROVED, now);
            actions.save(new ApprovalAction(txn.getId(), 1, Action.AUTO, null, null, "no workflow applies", now));
            audit.record(AuditEntry.of("AUTO_APPROVE", "APPROVAL", txn.getId()).company(s.companyId())
                    .reason("no workflow applies"));
            handlers.get(s.type()).onApproved(s.referenceId());
            return new ApprovalOutcome(txn.getId(), true);
        }
        enterLevel(txn, levels);
        return new ApprovalOutcome(txn.getId(), false);
    }

    // ---------------------------------------------------------------- actions

    @Transactional
    public ApprovalTransaction approve(UUID id, String comment) {
        ApprovalTransaction txn = pending(id);
        List<WorkflowLevel> levels = levels(txn);
        EligibilityResolver.Eligible eligible = eligibility.resolve(txn, levels.get(txn.getCurrentLevel() - 1));
        UUID user = CurrentUser.get().id();
        if (!eligible.contains(user)) {
            throw new ForbiddenException("You are not an approver for this item");
        }
        Instant now = clock.instant();
        actions.save(new ApprovalAction(id, txn.getCurrentLevel(), Action.APPROVE, user,
                eligible.approvers().get(user), blankToNull(comment), now));
        audit.record(AuditEntry.of("APPROVE", "APPROVAL", id).company(txn.getCompanyId())
                .onBehalfOf(eligible.approvers().get(user)).reason(comment));
        if (txn.getCurrentLevel() >= levels.size()) {
            txn.complete(Status.APPROVED, now);
            handlers.get(txn.getTxnType()).onApproved(txn.getReferenceId());
            completed(txn, "APPROVED");
        } else {
            txn.advance();
            enterLevel(txn, levels);
        }
        return txn;
    }

    @Transactional
    public ApprovalTransaction reject(UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new ValidationException("comment", "a comment is required when rejecting");
        }
        ApprovalTransaction txn = pending(id);
        EligibilityResolver.Eligible eligible = eligibility.resolve(txn, levels(txn).get(txn.getCurrentLevel() - 1));
        UUID user = CurrentUser.get().id();
        if (!eligible.contains(user)) {
            throw new ForbiddenException("You are not an approver for this item");
        }
        Instant now = clock.instant();
        actions.save(new ApprovalAction(id, txn.getCurrentLevel(), Action.REJECT, user,
                eligible.approvers().get(user), comment.trim(), now));
        txn.complete(Status.REJECTED, now);
        audit.record(AuditEntry.of("REJECT", "APPROVAL", id).company(txn.getCompanyId())
                .onBehalfOf(eligible.approvers().get(user)).reason(comment));
        handlers.get(txn.getTxnType()).onRejected(txn.getReferenceId(), comment.trim());
        completed(txn, "REJECTED");
        return txn;
    }

    /** Cancel by the requester (FR-071). */
    @Transactional
    public ApprovalTransaction cancel(UUID id, String comment) {
        ApprovalTransaction txn = pending(id);
        UUID user = CurrentUser.get().id();
        if (!user.equals(txn.getRequesterUserId())) {
            throw new ForbiddenException("Only the requester can cancel this item");
        }
        cancelInternal(txn, user, comment);
        handlers.get(txn.getTxnType()).onCancelled(txn.getReferenceId());
        return txn;
    }

    /**
     * Cancels the pending transaction of a reference on behalf of its owning module (e.g. HR cancelling a leave);
     * the handler is not called back because the module initiated it.
     */
    @Transactional
    public void cancelByModule(String type, UUID referenceId, String comment) {
        transactions.findFirstByTxnTypeAndReferenceIdAndStatus(type, referenceId, Status.PENDING)
                .ifPresent(txn -> cancelInternal(txn, CurrentUser.find().map(AuthenticatedUser::id).orElse(null), comment));
    }

    private void cancelInternal(ApprovalTransaction txn, UUID user, String comment) {
        Instant now = clock.instant();
        actions.save(new ApprovalAction(txn.getId(), txn.getCurrentLevel(), Action.CANCEL, user, null,
                blankToNull(comment), now));
        txn.complete(Status.CANCELLED, now);
        audit.record(AuditEntry.of("CANCEL", "APPROVAL", txn.getId()).company(txn.getCompanyId()).reason(comment));
    }

    // ---------------------------------------------------------------- queries

    /** FR-072 items the current user can act on now (directly or as delegate). */
    @Transactional(readOnly = true)
    public List<ApprovalTransaction> inbox() {
        UUID user = CurrentUser.get().id();
        List<ApprovalTransaction> result = new ArrayList<>();
        for (ApprovalTransaction txn : transactions.findByStatusOrderBySubmittedAt(Status.PENDING)) {
            if (eligibility.resolve(txn, levels(txn).get(txn.getCurrentLevel() - 1)).contains(user)) {
                result.add(txn);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ApprovalTransaction> mine() {
        return transactions.findByRequesterUserIdOrderBySubmittedAtDesc(CurrentUser.get().id());
    }

    @Transactional(readOnly = true)
    public TransactionView view(UUID id) {
        ApprovalTransaction txn = transactions.findById(id).orElseThrow(() -> new NotFoundException("Approval", id));
        AuthenticatedUser user = CurrentUser.get();
        List<WorkflowLevel> levels = levels(txn);
        Set<UUID> current = txn.getStatus() == Status.PENDING
                ? eligibility.resolve(txn, levels.get(txn.getCurrentLevel() - 1)).userIds() : Set.of();
        boolean canAct = current.contains(user.id());
        boolean isRequester = user.id().equals(txn.getRequesterUserId());
        boolean viewer = user.hasPermission(Permissions.of(PermissionModule.APPROVAL, PermissionAction.VIEW))
                && (txn.getSubjectEmployeeId() == null ? scope.current().coversCompany(txn.getCompanyId())
                        : scope.current().covers(placement(txn.getSubjectEmployeeId())));
        if (!canAct && !isRequester && !viewer) {
            throw new NotFoundException("Approval", id);
        }
        return new TransactionView(txn, levels, actions.findByTransactionIdOrderByActedAt(id), current, canAct,
                isRequester && txn.getStatus() == Status.PENDING);
    }

    // ---------------------------------------------------------------- helpers

    private void enterLevel(ApprovalTransaction txn, List<WorkflowLevel> levels) {
        EligibilityResolver.Eligible eligible = eligibility.resolve(txn, levels.get(txn.getCurrentLevel() - 1));
        if (eligible.fallback()) {
            actions.save(new ApprovalAction(txn.getId(), txn.getCurrentLevel(), Action.FALLBACK, null, null,
                    "no eligible approver; routed to " + EligibilityResolver.FALLBACK_ROLE, clock.instant()));
        }
        events.publishEvent(new ApprovalEvent(ApprovalEvent.Kind.REQUESTED, txn.getId(), txn.getTxnType(),
                txn.getSummary(), txn.getRequesterUserId(), Set.copyOf(eligible.userIds()), null));
    }

    private void completed(ApprovalTransaction txn, String outcome) {
        if (txn.getRequesterUserId() != null) {
            events.publishEvent(new ApprovalEvent(ApprovalEvent.Kind.COMPLETED, txn.getId(), txn.getTxnType(),
                    txn.getSummary(), txn.getRequesterUserId(), Set.of(txn.getRequesterUserId()), outcome));
        }
    }

    private ApprovalTransaction pending(UUID id) {
        ApprovalTransaction txn = transactions.findById(id).orElseThrow(() -> new NotFoundException("Approval", id));
        if (txn.getStatus() != Status.PENDING) {
            throw new ConflictException("INVALID_STATE", "This item is already " + txn.getStatus());
        }
        return txn;
    }

    List<WorkflowLevel> levels(ApprovalTransaction txn) {
        return json.readValue(txn.getLevelsSnapshot(), new TypeReference<List<WorkflowLevel>>() { });
    }

    private EmployeePlacement placement(UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee e = employees.byIds(List.of(employeeId)).get(employeeId);
        return e == null ? null : new EmployeePlacement(e.getId(), e.getCompanyId(), e.getBranchId(), e.getDepartmentId());
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
