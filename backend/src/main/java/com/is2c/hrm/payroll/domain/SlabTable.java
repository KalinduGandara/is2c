package com.is2c.hrm.payroll.domain;

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

/** Progressive rate table, e.g. APIT (FR-081). */
@Entity
@Table(name = "slab_table")
public class SlabTable extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean statutory;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "slab_row", joinColumns = @JoinColumn(name = "table_id"))
    @OrderColumn(name = "row_index")
    private List<SlabRow> rows = new ArrayList<>();

    protected SlabTable() {
    }

    public SlabTable(UUID companyId, String code) {
        this.companyId = companyId;
        this.code = code;
    }

    public UUID getCompanyId() {
        return companyId;
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

    public boolean isStatutory() {
        return statutory;
    }

    public void setStatutory(boolean statutory) {
        this.statutory = statutory;
    }

    public List<SlabRow> getRows() {
        return rows;
    }
}
