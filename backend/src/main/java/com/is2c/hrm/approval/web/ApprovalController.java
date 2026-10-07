package com.is2c.hrm.approval.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.approval.domain.ApprovalAction;
import com.is2c.hrm.approval.domain.ApprovalDelegation;
import com.is2c.hrm.approval.domain.ApprovalTransaction;
import com.is2c.hrm.approval.domain.ApprovalWorkflow;
import com.is2c.hrm.approval.domain.WorkflowLevel;
import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.approval.service.DelegationService;
import com.is2c.hrm.approval.service.WorkflowService;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;

@RestController
@RequestMapping("/api/v1/approval")
public class ApprovalController {

    private final WorkflowService workflows;
    private final ApprovalService approvals;
    private final DelegationService delegations;
    private final AppUserRepository users;

    public ApprovalController(WorkflowService workflows, ApprovalService approvals, DelegationService delegations,
            AppUserRepository users) {
        this.workflows = workflows;
        this.approvals = approvals;
        this.delegations = delegations;
        this.users = users;
    }

    public record WorkflowDto(UUID id, UUID companyId, UUID branchId, UUID departmentId, String txnType,
            boolean enabled, List<WorkflowLevel> levels, Long version) {
        static WorkflowDto of(ApprovalWorkflow w) {
            return new WorkflowDto(w.getId(), w.getCompanyId(), w.getBranchId(), w.getDepartmentId(), w.getTxnType(),
                    w.isEnabled(), List.copyOf(w.getLevels()), w.getVersion());
        }
    }

    public record TransactionDto(UUID id, String type, UUID referenceId, UUID companyId, UUID requesterUserId,
            String requesterName, UUID subjectEmployeeId, ApprovalTransaction.Status status, int currentLevel,
            String summary, Instant submittedAt, Instant completedAt) {
    }

    public record ActionDto(int level, ApprovalAction.Action action, UUID userId, String userName, UUID onBehalfOf,
            String comment, Instant actedAt) {
    }

    public record TransactionDetail(TransactionDto transaction, List<WorkflowLevel> levels, List<ActionDto> history,
            List<String> currentApprovers, boolean canAct, boolean canCancel) {
    }

    public record DelegationDto(UUID id, UUID delegatorUserId, String delegatorName, UUID delegateUserId,
            String delegateName, LocalDate fromDate, LocalDate toDate, boolean active) {
    }

    public record CommentRequest(String comment) {
    }

    // Workflows (FR-070)

    @GetMapping("/workflows")
    @PreAuthorize("hasAnyAuthority('APPROVAL:VIEW', 'APPROVAL:CONFIGURE')")
    public List<WorkflowDto> listWorkflows(@RequestParam UUID companyId) {
        return workflows.list(companyId).stream().map(WorkflowDto::of).toList();
    }

    @PostMapping("/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPROVAL:CONFIGURE')")
    public WorkflowDto createWorkflow(@RequestBody WorkflowService.WorkflowData body) {
        return WorkflowDto.of(workflows.create(body));
    }

    @PutMapping("/workflows/{id}")
    @PreAuthorize("hasAuthority('APPROVAL:CONFIGURE')")
    public WorkflowDto updateWorkflow(@PathVariable UUID id, @RequestBody WorkflowService.WorkflowData body) {
        return WorkflowDto.of(workflows.update(id, body));
    }

    @DeleteMapping("/workflows/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('APPROVAL:CONFIGURE')")
    public void deleteWorkflow(@PathVariable UUID id) {
        workflows.delete(id);
    }

    // Transactions (FR-071, FR-072) — eligibility is enforced by the service, not by permissions.

    @GetMapping("/inbox")
    public List<TransactionDto> inbox() {
        return toDtos(approvals.inbox());
    }

    @GetMapping("/my-requests")
    public List<TransactionDto> myRequests() {
        return toDtos(approvals.mine());
    }

    @GetMapping("/transactions/{id}")
    public TransactionDetail transaction(@PathVariable UUID id) {
        ApprovalService.TransactionView v = approvals.view(id);
        Map<UUID, String> names = names(v.history().stream().map(ApprovalAction::getUserId).toList(),
                v.currentApprovers(), v.transaction().getRequesterUserId());
        return new TransactionDetail(toDto(v.transaction(), names), v.levels(),
                v.history().stream().map(a -> new ActionDto(a.getLevelNo(), a.getAction(), a.getUserId(),
                        names.get(a.getUserId()), a.getOnBehalfOf(), a.getComment(), a.getActedAt())).toList(),
                v.currentApprovers().stream().map(names::get).sorted().toList(), v.canAct(), v.canCancel());
    }

    @PostMapping("/transactions/{id}/approve")
    public TransactionDto approve(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return toDtos(List.of(approvals.approve(id, body == null ? null : body.comment()))).get(0);
    }

    @PostMapping("/transactions/{id}/reject")
    public TransactionDto reject(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return toDtos(List.of(approvals.reject(id, body == null ? null : body.comment()))).get(0);
    }

    @PostMapping("/transactions/{id}/cancel")
    public TransactionDto cancel(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return toDtos(List.of(approvals.cancel(id, body == null ? null : body.comment()))).get(0);
    }

    // Delegations (FR-073)

    @GetMapping("/delegations")
    public List<DelegationDto> delegations(@RequestParam(defaultValue = "false") boolean all) {
        List<ApprovalDelegation> list = delegations.list(all);
        Map<UUID, String> names = names(list.stream().flatMap(d -> java.util.stream.Stream.of(d.getDelegatorUserId(),
                d.getDelegateUserId())).toList(), Set.of(), null);
        return list.stream().map(d -> new DelegationDto(d.getId(), d.getDelegatorUserId(),
                names.get(d.getDelegatorUserId()), d.getDelegateUserId(), names.get(d.getDelegateUserId()),
                d.getFromDate(), d.getToDate(), d.isActive())).toList();
    }

    @PostMapping("/delegations")
    @ResponseStatus(HttpStatus.CREATED)
    public DelegationDto createDelegation(@RequestBody DelegationService.DelegationData body) {
        ApprovalDelegation d = delegations.create(body);
        Map<UUID, String> names = names(List.of(d.getDelegatorUserId(), d.getDelegateUserId()), Set.of(), null);
        return new DelegationDto(d.getId(), d.getDelegatorUserId(), names.get(d.getDelegatorUserId()),
                d.getDelegateUserId(), names.get(d.getDelegateUserId()), d.getFromDate(), d.getToDate(), d.isActive());
    }

    @DeleteMapping("/delegations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeDelegation(@PathVariable UUID id) {
        delegations.revoke(id);
    }

    private List<TransactionDto> toDtos(List<ApprovalTransaction> list) {
        Map<UUID, String> names = names(list.stream().map(ApprovalTransaction::getRequesterUserId).toList(), Set.of(),
                null);
        return list.stream().map(t -> toDto(t, names)).toList();
    }

    private static TransactionDto toDto(ApprovalTransaction t, Map<UUID, String> names) {
        return new TransactionDto(t.getId(), t.getTxnType(), t.getReferenceId(), t.getCompanyId(),
                t.getRequesterUserId(), names.get(t.getRequesterUserId()), t.getSubjectEmployeeId(), t.getStatus(),
                t.getCurrentLevel(), t.getSummary(), t.getSubmittedAt(), t.getCompletedAt());
    }

    private Map<UUID, String> names(List<UUID> a, Set<UUID> b, UUID c) {
        Set<UUID> ids = new java.util.HashSet<>();
        a.stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        ids.addAll(b);
        if (c != null) {
            ids.add(c);
        }
        Map<UUID, String> names = new java.util.HashMap<>();
        users.findAllById(ids).forEach(u -> names.put(u.getId(), displayName(u)));
        return names;
    }

    private static String displayName(AppUser u) {
        return u.getDisplayName() + " (" + u.getUsername() + ")";
    }
}
