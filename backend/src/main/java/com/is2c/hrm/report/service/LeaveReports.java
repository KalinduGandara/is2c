package com.is2c.hrm.report.service;

import static com.is2c.hrm.report.service.ReportDefinition.param;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.leave.domain.LeaveRequest;
import com.is2c.hrm.leave.domain.LeaveType;
import com.is2c.hrm.leave.service.LeaveBalanceService;
import com.is2c.hrm.leave.service.LeaveCalendarService;
import com.is2c.hrm.leave.service.LeaveTypeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableData.Column;

/** FR-095 reports 5–6: leave balances and leave usage. */
@Component
class LeaveReports implements ReportProvider {

    private final LeaveBalanceService balances;
    private final LeaveCalendarService requests;
    private final LeaveTypeService types;
    private final EmployeeService employees;
    private final OrgDirectory org;

    LeaveReports(LeaveBalanceService balances, LeaveCalendarService requests, LeaveTypeService types,
            EmployeeService employees, OrgDirectory org) {
        this.balances = balances;
        this.requests = requests;
        this.types = types;
        this.employees = employees;
        this.org = org;
    }

    @Override
    public List<ReportDefinition> reports() {
        var company = param("companyId", "Company", "company", true);
        return List.of(
                new ReportDefinition("leave-balances", "Leave balances", List.of(company, param("year", "Year", "year", true),
                        param("leaveTypeId", "Leave type", "leaveType", false)), Set.of(), true, this::balances),
                new ReportDefinition("leave-usage", "Leave usage", List.of(company, param("from", "From", "date", true),
                        param("to", "To", "date", true), param("leaveTypeId", "Leave type", "leaveType", false)),
                        Set.of(), false, this::usage));
    }

    private TableData balances(ReportParams p) {
        UUID companyId = p.uuid("companyId");
        int year = p.integer("year");
        UUID typeId = p.uuid("leaveTypeId");
        List<Employee> people = employees.employedInScope(companyId, null, null, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)).stream().sorted(Comparator.comparing(Employee::getEmployeeNumber)).toList();
        List<List<Object>> rows = new ArrayList<>();
        for (Employee e : people) {
            for (LeaveBalanceService.BalanceView v : balances.forEmployee(e.getId(), year)) {
                if (typeId != null && !typeId.equals(v.type().getId())) {
                    continue;
                }
                var b = v.balance();
                boolean unlimited = v.type().isUnlimited();
                rows.add(java.util.Arrays.asList(e.getEmployeeNumber(), ReportSupport.name(e), v.type().getName(),
                        unlimited ? null : b.getOpening(), unlimited ? null : b.getEntitled(),
                        unlimited ? null : b.getCarriedForward(), unlimited ? null : b.getAdjustment(), b.getTaken(),
                        b.getPending(), unlimited ? null : b.getExpired(), unlimited ? null : b.available()));
            }
        }
        List<String> header = new ArrayList<>(List.of(org.company(companyId).getLegalName(), "Year: " + year));
        if (typeId != null) {
            header.add("Leave type: " + typeName(companyId, typeId));
        }
        return p.table(header, List.of(Column.text("Employee No"), Column.text("Name"), Column.text("Leave type"),
                Column.number("Opening"), Column.number("Entitled"), Column.number("Carried"), Column.number("Adjustment"),
                Column.number("Taken"), Column.number("Pending"), Column.number("Expired"), Column.number("Available")),
                rows, null);
    }

    private String typeName(UUID companyId, UUID typeId) {
        return types.list(companyId).stream().filter(t -> t.getId().equals(typeId)).map(LeaveType::getName).findFirst()
                .orElse(typeId.toString());
    }

    private TableData usage(ReportParams p) {
        UUID companyId = p.uuid("companyId");
        LocalDate from = p.date("from");
        LocalDate to = p.date("to");
        p.requireOrder(from, to);
        UUID typeId = p.uuid("leaveTypeId");
        List<LeaveRequest> list = requests.requests(new LeaveCalendarService.RequestFilter(companyId,
                LeaveRequest.Status.APPROVED, null, from, to)).stream()
                .filter(r -> typeId == null || typeId.equals(r.getLeaveTypeId())).toList();
        Map<UUID, String> typeNames = types.list(companyId).stream().collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));
        Map<UUID, Employee> people = employees.byIds(list.stream().map(LeaveRequest::getEmployeeId).toList());
        List<List<Object>> rows = new ArrayList<>();
        list.stream().sorted(Comparator.comparing(LeaveRequest::getFromDate)
                .thenComparing(r -> ReportSupport.number(people.get(r.getEmployeeId())))).forEach(r -> {
                    Employee e = people.get(r.getEmployeeId());
                    rows.add(java.util.Arrays.asList(ReportSupport.number(e), ReportSupport.name(e),
                            typeNames.get(r.getLeaveTypeId()), r.getFromDate(), r.getToDate(), r.getDays(),
                            r.getHalfDayPart() == null ? null : r.getHalfDayPart().name(), r.getStatus().name()));
                });
        java.math.BigDecimal total = list.stream().map(LeaveRequest::getDays).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        List<String> header = new ArrayList<>(List.of(org.company(companyId).getLegalName(), "From " + from + " to " + to));
        if (typeId != null) {
            header.add("Leave type: " + typeNames.get(typeId));
        }
        return p.table(header, List.of(Column.text("Employee No"), Column.text("Name"), Column.text("Leave type"),
                Column.text("From"), Column.text("To"), Column.number("Days"), Column.text("Half day"), Column.text("Status")),
                rows, java.util.Arrays.asList("Total", null, null, null, null, total, null, null));
    }
}
