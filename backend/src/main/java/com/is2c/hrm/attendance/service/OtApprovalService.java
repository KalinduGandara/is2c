package com.is2c.hrm.attendance.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.domain.AttendanceDailyRepository;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.service.EmployeeService;

/** FR-056 approve or reject overtime that requires approval. */
@Service
public class OtApprovalService {

    private final AttendanceDailyRepository dailies;
    private final EmployeeService employees;
    private final AttendanceLocks locks;
    private final AuditService audit;

    public OtApprovalService(AttendanceDailyRepository dailies, EmployeeService employees, AttendanceLocks locks,
            AuditService audit) {
        this.dailies = dailies;
        this.employees = employees;
        this.locks = locks;
        this.audit = audit;
    }

    @Transactional
    public int decide(List<UUID> dailyIds, boolean approve, String comment) {
        if (dailyIds == null || dailyIds.isEmpty()) {
            throw new ValidationException("dailyIds", "select at least one day");
        }
        List<AttendanceDaily> rows = dailies.findAllById(dailyIds);
        if (rows.size() != new HashSet<>(dailyIds).size()) {
            throw new NotFoundException("Some attendance records were not found");
        }
        Set<java.time.LocalDate> dates = new HashSet<>();
        UUID company = null;
        for (AttendanceDaily d : rows) {
            employees.get(d.getEmployeeId()); // scope check (404 outside scope)
            if (!d.isOtRequiresApproval()) {
                throw new ValidationException("dailyIds", "overtime on " + d.getWorkDate() + " does not need approval");
            }
            locks.assertUnlocked(d.getCompanyId(), List.of(d.getWorkDate()));
            d.decideOt(approve);
            dates.add(d.getWorkDate());
            company = d.getCompanyId();
            audit.record(AuditEntry.of(approve ? "OT_APPROVE" : "OT_REJECT", "ATTENDANCE_DAILY", d.getId())
                    .company(d.getCompanyId()).reason(comment)
                    .after(Map.of("employeeId", d.getEmployeeId(), "date", d.getWorkDate(),
                            "otMinutes", d.getOtWorkdayMinutes() + d.getOtOffdayMinutes() + d.getOtHolidayMinutes())));
        }
        locks.changed(company, dates);
        return rows.size();
    }
}
