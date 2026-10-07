package com.is2c.hrm.approval.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Approval route for a transaction type at company, branch or department level (FR-070). */
@Entity
@Table(name = "approval_workflow")
public class ApprovalWorkflow extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "txn_type", nullable = false)
    private String txnType;

    @Column(nullable = false)
    private boolean enabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approval_workflow_level", joinColumns = @JoinColumn(name = "workflow_id"))
    @OrderColumn(name = "level_index")
    private List<WorkflowLevel> levels = new ArrayList<>();

    protected ApprovalWorkflow() {
    }

    public ApprovalWorkflow(UUID companyId, String txnType) {
        this.companyId = companyId;
        this.txnType = txnType;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<WorkflowLevel> getLevels() {
        return levels;
    }
}
