package com.is2c.hrm.employee.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Employee document metadata (FR-020); content lives in FileStorage. */
@Entity
@Table(name = "employee_document")
@SQLRestriction("deleted_at IS NULL")
public class EmployeeDocument {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeDocument() {
    }

    public EmployeeDocument(UUID employeeId, String docType, String title, String fileName, String contentType,
            long sizeBytes, String storageKey, LocalDate expiryDate, UUID uploadedBy, Instant uploadedAt) {
        this.employeeId = employeeId;
        this.docType = docType;
        this.title = title;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.expiryDate = expiryDate;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getDocType() {
        return docType;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
