package com.is2c.hrm.approval.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** History entry of an approval transaction (FR-071). */
@Entity
@Immutable
@Table(name = "approval_action")
public class ApprovalAction {

    public enum Action { SUBMIT, APPROVE, REJECT, CANCEL, AUTO, FALLBACK }

    @Id
    private UUID id = Ids.newId();

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "level_no", nullable = false)
    private int levelNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "on_behalf_of")
    private UUID onBehalfOf;

    private String comment;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    protected ApprovalAction() {
    }

    public ApprovalAction(UUID transactionId, int levelNo, Action action, UUID userId, UUID onBehalfOf,
            String comment, Instant actedAt) {
        this.transactionId = transactionId;
        this.levelNo = levelNo;
        this.action = action;
        this.userId = userId;
        this.onBehalfOf = onBehalfOf;
        this.comment = comment;
        this.actedAt = actedAt;
    }

    public UUID getId() {
        return id;
    }

    public int getLevelNo() {
        return levelNo;
    }

    public Action getAction() {
        return action;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOnBehalfOf() {
        return onBehalfOf;
    }

    public String getComment() {
        return comment;
    }

    public Instant getActedAt() {
        return actedAt;
    }
}
