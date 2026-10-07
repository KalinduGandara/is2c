package com.is2c.hrm.approval.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.api.ApprovalTypes;
import com.is2c.hrm.approval.domain.ApprovalWorkflow;
import com.is2c.hrm.approval.domain.ApprovalWorkflowRepository;
import com.is2c.hrm.approval.domain.WorkflowLevel;
import com.is2c.hrm.approval.domain.WorkflowLevel.ApproverType;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.api.CompanyCreatedEvent;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.RoleRepository;

/** FR-070 workflow definitions, most-specific resolution and default seeding. */
@Service
public class WorkflowService {

    public static final int MAX_LEVELS = 5;

    private final ApprovalWorkflowRepository workflows;
    private final HandlerRegistry handlers;
    private final RoleRepository roles;
    private final AppUserRepository users;
    private final OrgDirectory org;
    private final DataScopeService scope;
    private final AuditService audit;

    public WorkflowService(ApprovalWorkflowRepository workflows, HandlerRegistry handlers, RoleRepository roles,
            AppUserRepository users, OrgDirectory org, DataScopeService scope, AuditService audit) {
        this.workflows = workflows;
        this.handlers = handlers;
        this.roles = roles;
        this.users = users;
        this.org = org;
        this.scope = scope;
        this.audit = audit;
    }

    public record WorkflowData(UUID companyId, UUID branchId, UUID departmentId, String txnType, Boolean enabled,
            List<WorkflowLevel> levels, Long version) {
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflow> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return workflows.findByCompanyIdOrderByTxnType(companyId);
    }

    @Transactional
    public ApprovalWorkflow create(WorkflowData d) {
        if (d.companyId() == null) {
            throw new ValidationException("companyId", "is required");
        }
        scope.assertCompanyWritable(d.companyId());
        validate(d, null);
        ApprovalWorkflow w = new ApprovalWorkflow(d.companyId(), d.txnType());
        apply(w, d);
        workflows.save(w);
        audit.record(AuditEntry.of("CREATE", "APPROVAL_WORKFLOW", w.getId()).company(d.companyId()).after(d));
        return w;
    }

    @Transactional
    public ApprovalWorkflow update(UUID id, WorkflowData d) {
        ApprovalWorkflow w = workflows.findById(id).orElseThrow(() -> new NotFoundException("Workflow", id));
        scope.assertCompanyWritable(w.getCompanyId());
        Versions.check(w, d.version());
        WorkflowData merged = new WorkflowData(w.getCompanyId(), d.branchId(), d.departmentId(), d.txnType(),
                d.enabled(), d.levels(), d.version());
        validate(merged, id);
        apply(w, merged);
        audit.record(AuditEntry.of("UPDATE", "APPROVAL_WORKFLOW", id).company(w.getCompanyId()).after(merged));
        return w;
    }

    @Transactional
    public void delete(UUID id) {
        ApprovalWorkflow w = workflows.findById(id).orElseThrow(() -> new NotFoundException("Workflow", id));
        scope.assertCompanyWritable(w.getCompanyId());
        workflows.delete(w);
        audit.record(AuditEntry.of("DELETE", "APPROVAL_WORKFLOW", id).company(w.getCompanyId()));
    }

    /** Most specific enabled workflow: department (nearest ancestor first) > branch > company. */
    @Transactional(readOnly = true)
    public Optional<ApprovalWorkflow> resolve(String type, UUID companyId, EmployeePlacement subject) {
        List<ApprovalWorkflow> candidates = workflows.findByCompanyIdAndTxnType(companyId, type).stream()
                .filter(ApprovalWorkflow::isEnabled).toList();
        if (subject != null && subject.departmentId() != null) {
            for (UUID dept : org.departmentAncestry(subject.departmentId())) {
                Optional<ApprovalWorkflow> match = candidates.stream()
                        .filter(w -> dept.equals(w.getDepartmentId())).findFirst();
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        if (subject != null && subject.branchId() != null) {
            Optional<ApprovalWorkflow> match = candidates.stream()
                    .filter(w -> w.getDepartmentId() == null && subject.branchId().equals(w.getBranchId())).findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return candidates.stream().filter(w -> w.getBranchId() == null && w.getDepartmentId() == null).findFirst();
    }

    /** FR-070 defaults for a new company. */
    @EventListener
    @Transactional
    public void seed(CompanyCreatedEvent event) {
        seed(event.companyId(), ApprovalTypes.LEAVE_REQUEST, true, new WorkflowLevel(ApproverType.REPORTING_MANAGER, null, null));
        seed(event.companyId(), ApprovalTypes.ATTENDANCE_CORRECTION, false, new WorkflowLevel(ApproverType.ROLE, "HR_MANAGER", null));
        seed(event.companyId(), ApprovalTypes.PAYROLL_APPROVAL, true, new WorkflowLevel(ApproverType.ROLE, "HR_MANAGER", null));
    }

    private void seed(UUID companyId, String type, boolean enabled, WorkflowLevel level) {
        ApprovalWorkflow w = new ApprovalWorkflow(companyId, type);
        w.setEnabled(enabled);
        w.getLevels().add(level);
        workflows.save(w);
    }

    private void apply(ApprovalWorkflow w, WorkflowData d) {
        w.setTxnType(d.txnType());
        w.setBranchId(d.branchId());
        w.setDepartmentId(d.departmentId());
        w.setEnabled(d.enabled() == null || d.enabled());
        w.getLevels().clear();
        w.getLevels().addAll(d.levels());
    }

    private void validate(WorkflowData d, UUID selfId) {
        List<FieldError> errors = new ArrayList<>();
        Set<String> known = handlers.types();
        if (d.txnType() == null || !known.contains(d.txnType())) {
            errors.add(new FieldError("txnType", "must be one of " + known));
        }
        if (d.branchId() != null && d.departmentId() != null) {
            errors.add(new FieldError("departmentId", "choose either a branch or a department, not both"));
        }
        if (d.branchId() != null && org.branch(d.branchId()).filter(b -> b.getCompanyId().equals(d.companyId())).isEmpty()) {
            errors.add(new FieldError("branchId", "branch must belong to the company"));
        }
        if (d.departmentId() != null && org.department(d.departmentId())
                .filter(x -> x.getCompanyId().equals(d.companyId())).isEmpty()) {
            errors.add(new FieldError("departmentId", "department must belong to the company"));
        }
        if (d.levels() == null || d.levels().isEmpty() || d.levels().size() > MAX_LEVELS) {
            errors.add(new FieldError("levels", "a workflow needs 1-" + MAX_LEVELS + " levels"));
        } else {
            for (int i = 0; i < d.levels().size(); i++) {
                WorkflowLevel l = d.levels().get(i);
                String field = "levels[" + i + "]";
                if (l == null || l.approverType() == null) {
                    errors.add(new FieldError(field, "approver type is required"));
                } else if (l.approverType() == ApproverType.ROLE
                        && (l.roleCode() == null || roles.findByCode(l.roleCode()).isEmpty())) {
                    errors.add(new FieldError(field, "a valid role is required"));
                } else if (l.approverType() == ApproverType.USER
                        && (l.userId() == null || !users.existsById(l.userId()))) {
                    errors.add(new FieldError(field, "a valid user is required"));
                }
            }
        }
        if (errors.isEmpty()) {
            boolean duplicate = workflows.findByCompanyIdAndTxnType(d.companyId(), d.txnType()).stream()
                    .anyMatch(w -> !w.getId().equals(selfId) && Objects.equals(w.getBranchId(), d.branchId())
                            && Objects.equals(w.getDepartmentId(), d.departmentId()));
            if (duplicate) {
                errors.add(new FieldError("txnType", "a workflow already exists for this type and scope"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}
