package com.is2c.hrm.report.service;

import static com.is2c.hrm.report.service.ReportDefinition.param;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.is2c.hrm.audit.service.AuditQueryService;
import com.is2c.hrm.payroll.service.PayslipService;
import com.is2c.hrm.platform.export.TableData;

/** FR-095 reports 8 (payroll register & department summary, PAYROLL:VIEW) and 9 (audit log, AUDIT:VIEW). */
@Component
class PayrollAuditReports implements ReportProvider {

    private final PayslipService payroll;
    private final AuditQueryService audit;

    PayrollAuditReports(PayslipService payroll, AuditQueryService audit) {
        this.payroll = payroll;
        this.audit = audit;
    }

    @Override
    public List<ReportDefinition> reports() {
        var period = param("periodId", "Payroll period", "payrollPeriod", true);
        return List.of(
                new ReportDefinition("payroll-register", "Payroll register", List.of(period), Set.of("PAYROLL:VIEW"), true,
                        p -> payroll.registerTable(p.uuid("periodId"))),
                new ReportDefinition("payroll-department-summary", "Department summary", List.of(period),
                        Set.of("PAYROLL:VIEW"), false, p -> payroll.departmentSummaryTable(p.uuid("periodId"))),
                new ReportDefinition("audit-log", "Audit log", List.of(param("from", "From", "date", false),
                        param("to", "To", "date", false), param("companyId", "Company", "company", false),
                        param("entityType", "Entity type", "text", false), param("entityId", "Entity ID", "text", false),
                        param("action", "Action", "text", false)), Set.of("AUDIT:VIEW"), true, this::audit));
    }

    private TableData audit(ReportParams p) {
        LocalDate from = p.date("from");
        LocalDate to = p.date("to");
        p.requireOrder(from, to);
        return audit.table(new AuditQueryService.Filter(from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), null, p.text("entityType"),
                p.text("entityId"), p.text("action"), p.uuid("companyId")));
    }
}
