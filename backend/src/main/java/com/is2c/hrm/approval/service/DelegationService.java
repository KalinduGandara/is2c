package com.is2c.hrm.approval.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.domain.ApprovalDelegation;
import com.is2c.hrm.approval.domain.ApprovalDelegationRepository;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.api.PermissionAction;
import com.is2c.hrm.security.api.PermissionModule;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;

/** FR-073 approval delegation. */
@Service
public class DelegationService {

    private static final String CONFIGURE = Permissions.of(PermissionModule.APPROVAL, PermissionAction.CONFIGURE);

    private final ApprovalDelegationRepository delegations;
    private final AppUserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public DelegationService(ApprovalDelegationRepository delegations, AppUserRepository users, AuditService audit,
            Clock clock) {
        this.delegations = delegations;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    public record DelegationData(UUID delegatorUserId, UUID delegateUserId, LocalDate fromDate, LocalDate toDate) {
    }

    @Transactional(readOnly = true)
    public List<ApprovalDelegation> list(boolean all) {
        AuthenticatedUser me = CurrentUser.get();
        if (all && me.hasPermission(CONFIGURE)) {
            return delegations.findByActiveTrue();
        }
        List<ApprovalDelegation> mine = new ArrayList<>(delegations.findByDelegatorUserIdAndActiveTrue(me.id()));
        mine.addAll(delegations.findByDelegateUserIdAndActiveTrue(me.id()));
        return mine;
    }

    @Transactional
    public ApprovalDelegation create(DelegationData d) {
        AuthenticatedUser me = CurrentUser.get();
        UUID delegator = d.delegatorUserId() == null ? me.id() : d.delegatorUserId();
        if (!delegator.equals(me.id()) && !me.hasPermission(CONFIGURE)) {
            throw new ForbiddenException("Only administrators can create delegations for other users");
        }
        List<FieldError> errors = new ArrayList<>();
        if (d.delegateUserId() == null || users.findById(d.delegateUserId()).filter(AppUser::isActive).isEmpty()) {
            errors.add(new FieldError("delegateUserId", "an active delegate is required"));
        } else if (d.delegateUserId().equals(delegator)) {
            errors.add(new FieldError("delegateUserId", "you cannot delegate to yourself"));
        }
        if (d.fromDate() == null || d.toDate() == null || d.toDate().isBefore(d.fromDate())) {
            errors.add(new FieldError("toDate", "a valid date range is required"));
        } else if (delegations.findByDelegatorUserIdAndActiveTrue(delegator).stream()
                .anyMatch(x -> x.overlaps(d.fromDate(), d.toDate()))) {
            errors.add(new FieldError("fromDate", "overlaps an existing delegation"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        ApprovalDelegation saved = delegations.save(new ApprovalDelegation(delegator, d.delegateUserId(), d.fromDate(),
                d.toDate(), clock.instant()));
        audit.record(AuditEntry.of("CREATE", "APPROVAL_DELEGATION", saved.getId()).after(d));
        return saved;
    }

    @Transactional
    public void revoke(UUID id) {
        AuthenticatedUser me = CurrentUser.get();
        ApprovalDelegation d = delegations.findById(id).orElseThrow(() -> new NotFoundException("Delegation", id));
        if (!d.getDelegatorUserId().equals(me.id()) && !me.hasPermission(CONFIGURE)) {
            throw new NotFoundException("Delegation", id);
        }
        d.deactivate();
        audit.record(AuditEntry.of("REVOKE", "APPROVAL_DELEGATION", id));
    }
}
