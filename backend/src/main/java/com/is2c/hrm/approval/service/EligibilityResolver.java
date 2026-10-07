package com.is2c.hrm.approval.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.domain.ApprovalDelegation;
import com.is2c.hrm.approval.domain.ApprovalDelegationRepository;
import com.is2c.hrm.approval.domain.ApprovalTransaction;
import com.is2c.hrm.approval.domain.WorkflowLevel;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.api.EmployeeScope;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.service.PrincipalLoader;

/**
 * Who may act on a transaction's current level (FR-071, FR-073): direct approvers per level type, never the
 * requester or the subject employee; HR_MANAGER fallback when nobody qualifies; plus active delegates.
 */
@Component
public class EligibilityResolver {

    static final String FALLBACK_ROLE = "HR_MANAGER";

    private final AppUserRepository users;
    private final PrincipalLoader principals;
    private final DataScopeService scope;
    private final EmployeeService employees;
    private final ApprovalDelegationRepository delegations;
    private final Clock clock;

    public EligibilityResolver(AppUserRepository users, PrincipalLoader principals, DataScopeService scope,
            EmployeeService employees, ApprovalDelegationRepository delegations, Clock clock) {
        this.users = users;
        this.principals = principals;
        this.scope = scope;
        this.employees = employees;
        this.delegations = delegations;
        this.clock = clock;
    }

    /** approverUserId → on-behalf-of user id (null when acting directly). */
    public record Eligible(Map<UUID, UUID> approvers, boolean fallback) {

        public boolean contains(UUID userId) {
            return approvers.containsKey(userId);
        }

        public Set<UUID> userIds() {
            return approvers.keySet();
        }
    }

    @Transactional(readOnly = true)
    public Eligible resolve(ApprovalTransaction txn, WorkflowLevel level) {
        Employee subject = txn.getSubjectEmployeeId() == null ? null : employees.byIds(List.of(txn.getSubjectEmployeeId()))
                .get(txn.getSubjectEmployeeId());
        EmployeePlacement placement = subject == null ? null : new EmployeePlacement(subject.getId(),
                subject.getCompanyId(), subject.getBranchId(), subject.getDepartmentId());
        Set<UUID> excluded = new HashSet<>();
        if (txn.getRequesterUserId() != null) {
            excluded.add(txn.getRequesterUserId());
        }
        if (subject != null) {
            users.findByEmployeeId(subject.getId()).ifPresent(u -> excluded.add(u.getId()));
        }

        Set<UUID> direct = new HashSet<>();
        switch (level.approverType()) {
            case REPORTING_MANAGER -> {
                if (subject != null && subject.getReportingManagerId() != null) {
                    users.findByEmployeeId(subject.getReportingManagerId()).filter(AppUser::isActive)
                            .ifPresent(u -> direct.add(u.getId()));
                }
            }
            case ROLE -> direct.addAll(withRoleCovering(level.roleCode(), txn.getCompanyId(), placement));
            case USER -> users.findById(level.userId()).filter(AppUser::isActive).ifPresent(u -> direct.add(u.getId()));
        }
        direct.removeAll(excluded);
        boolean fallback = false;
        if (direct.isEmpty()) {
            fallback = true;
            direct.addAll(withRoleCovering(FALLBACK_ROLE, txn.getCompanyId(), placement));
            direct.removeAll(excluded);
        }

        Map<UUID, UUID> result = new LinkedHashMap<>();
        direct.forEach(u -> result.put(u, null));
        LocalDate today = LocalDate.now(clock);
        for (UUID approver : direct) {
            for (ApprovalDelegation d : delegations.findByDelegatorUserIdAndActiveTrue(approver)) {
                if (d.activeOn(today) && !excluded.contains(d.getDelegateUserId())
                        && !result.containsKey(d.getDelegateUserId())
                        && users.findById(d.getDelegateUserId()).filter(AppUser::isActive).isPresent()) {
                    result.put(d.getDelegateUserId(), approver);
                }
            }
        }
        return new Eligible(result, fallback);
    }

    private Set<UUID> withRoleCovering(String roleCode, UUID companyId, EmployeePlacement placement) {
        Set<UUID> ids = new HashSet<>();
        for (AppUser u : users.findActiveByRoleCode(roleCode)) {
            Optional<AuthenticatedUser> principal = principals.load(u.getId());
            if (principal.isEmpty()) {
                continue;
            }
            EmployeeScope s = scope.forUser(principal.get());
            boolean covers = placement != null ? s.covers(placement) : s.coversCompany(companyId);
            if (covers) {
                ids.add(u.getId());
            }
        }
        return ids;
    }
}
