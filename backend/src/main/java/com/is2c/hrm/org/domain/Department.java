package com.is2c.hrm.org.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.is2c.hrm.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "department")
@SQLRestriction("deleted_at IS NULL")
public class Department extends SoftDeletableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "cost_center_code")
    private String costCenterCode;

    @Column(name = "manager_employee_id")
    private UUID managerEmployeeId;

    protected Department() {
    }

    public Department(UUID companyId, String code) {
        this.companyId = companyId;
        this.code = code;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCostCenterCode() {
        return costCenterCode;
    }

    public void setCostCenterCode(String costCenterCode) {
        this.costCenterCode = costCenterCode;
    }

    public UUID getManagerEmployeeId() {
        return managerEmployeeId;
    }

    public void setManagerEmployeeId(UUID managerEmployeeId) {
        this.managerEmployeeId = managerEmployeeId;
    }
}
