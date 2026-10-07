package com.is2c.hrm.attendance.domain;

import java.time.Instant;
import java.util.UUID;

import com.is2c.hrm.attendance.api.EventDirection;
import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Raw punch (FR-045). Never modified except linking an unmatched event to an employee (rematch). */
@Entity
@Table(name = "attendance_event")
public class AttendanceEvent {

    public enum Status { MATCHED, UNMATCHED }

    @Id
    private UUID id = Ids.newId();

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "device_id", updatable = false)
    private String deviceId;

    @Column(name = "employee_identifier", nullable = false, updatable = false)
    private String employeeIdentifier;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EventDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected AttendanceEvent() {
    }

    public AttendanceEvent(UUID sourceId, UUID companyId, String externalId, String deviceId, String employeeIdentifier,
            UUID employeeId, Instant eventTime, EventDirection direction, Instant receivedAt) {
        this.sourceId = sourceId;
        this.companyId = companyId;
        this.externalId = externalId;
        this.deviceId = deviceId;
        this.employeeIdentifier = employeeIdentifier;
        this.employeeId = employeeId;
        this.eventTime = eventTime;
        this.direction = direction;
        this.status = employeeId == null ? Status.UNMATCHED : Status.MATCHED;
        this.receivedAt = receivedAt;
    }

    public void match(UUID employee) {
        this.employeeId = employee;
        this.status = Status.MATCHED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getEmployeeIdentifier() {
        return employeeIdentifier;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public EventDirection getDirection() {
        return direction;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
