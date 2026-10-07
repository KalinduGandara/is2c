package com.is2c.hrm.leave.domain;

import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Supporting document of a leave request (FR-063). */
@Entity
@Table(name = "leave_attachment")
public class LeaveAttachment {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    protected LeaveAttachment() {
    }

    public LeaveAttachment(UUID requestId, String fileName, String contentType, long sizeBytes, String storageKey) {
        this.requestId = requestId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
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
}
