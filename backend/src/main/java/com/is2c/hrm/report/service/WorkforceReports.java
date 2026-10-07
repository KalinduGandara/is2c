package com.is2c.hrm.report.service;

import static com.is2c.hrm.report.service.ReportDefinition.param;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeStatus;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableData.Column;
import com.is2c.hrm.roster.domain.RosterEntry;
import com.is2c.hrm.roster.service.RosterService;
import com.is2c.hrm.shift.domain.Shift;
import com.is2c.hrm.shift.service.ShiftService;

/** FR-095 reports 1 (employee list) and 7 (roster). */
@Component
class WorkforceReports implements ReportProvider {

    static final int MAX_ROWS = 10_000;

    private final EmployeeService employees;
    private final OrgDirectory org;
    private final RosterService rosters;
    private final ShiftService shifts;

    WorkforceReports(EmployeeService employees, OrgDirectory org, RosterService rosters, ShiftService shifts) {
        this.employees = employees;
        this.org = org;
        this.rosters = rosters;
        this.shifts = shifts;
    }

    @Override
    public List<ReportDefinition> reports() {
        return List.of(
                new ReportDefinition("employee-list", "Employee list", List.of(
                        param("companyId", "Company", "company", true), param("q", "Search", "text", false),
                        param("branchId", "Branch", "branch", false), param("departmentId", "Department", "department", false),
                        param("designationId", "Designation", "designation", false),
                        param("categoryId", "Employment category", "category", false),
                        param("status", "Status", "employeeStatus", false)),
                        Set.of(), true, this::employeeList),
                new ReportDefinition("roster", "Roster", List.of(param("rosterId", "Roster", "roster", true)),
                        Set.of(), true, this::roster));
    }

    private TableData employeeList(ReportParams p) {
        UUID companyId = p.uuid("companyId");
        EmployeeService.SearchFilter f = new EmployeeService.SearchFilter(p.text("q"), companyId, p.uuid("branchId"),
                p.uuid("departmentId"), p.uuid("designationId"), p.uuid("categoryId"),
                p.enumValue("status", EmployeeStatus.class));
        List<Employee> list = employees.search(f, PageRequest.of(0, MAX_ROWS, Sort.by("employeeNumber"))).getContent();
        Map<UUID, String> branches = org.branchNames(list.stream().map(Employee::getBranchId).toList());
        Map<UUID, String> departments = org.departmentNames(list.stream().map(Employee::getDepartmentId).toList());
        Map<UUID, String> designations = org.designationNames(list.stream().map(Employee::getDesignationId).toList());
        List<List<Object>> rows = new ArrayList<>();
        for (Employee e : list) {
            rows.add(java.util.Arrays.asList(e.getEmployeeNumber(), ReportSupport.name(e), branches.get(e.getBranchId()),
                    departments.get(e.getDepartmentId()), designations.get(e.getDesignationId()), e.getJoinDate(),
                    e.getStatus().name()));
        }
        List<String> header = new ArrayList<>(List.of(org.company(companyId).getLegalName()));
        filter(header, "Search", p.text("q"));
        filter(header, "Branch", p.uuid("branchId") == null ? null : org.branchNames(List.of(p.uuid("branchId"))).get(p.uuid("branchId")));
        filter(header, "Department", p.uuid("departmentId") == null ? null
                : org.departmentNames(List.of(p.uuid("departmentId"))).get(p.uuid("departmentId")));
        filter(header, "Status", p.text("status"));
        return p.table(header, List.of(Column.text("Employee No"), Column.text("Name"), Column.text("Branch"),
                Column.text("Department"), Column.text("Designation"), Column.text("Join date"), Column.text("Status")),
                rows, List.of("Total", rows.size() + " employees"));
    }

    private static void filter(List<String> header, String label, String value) {
        if (value != null) {
            header.add(label + ": " + value);
        }
    }

    private TableData roster(ReportParams p) {
        RosterService.RosterView v = rosters.view(p.uuid("rosterId"));
        LocalDate start = v.roster().getStartDate();
        LocalDate end = v.roster().getEndDate();
        Map<UUID, String> codes = shifts.forCompany(v.roster().getCompanyId()).stream()
                .collect(Collectors.toMap(Shift::getId, Shift::getCode));
        Map<UUID, Map<LocalDate, String>> grid = new HashMap<>();
        for (RosterEntry e : v.entries()) {
            grid.computeIfAbsent(e.getEmployeeId(), k -> new HashMap<>())
                    .put(e.getWorkDate(), e.getShiftId() == null ? "OFF" : codes.getOrDefault(e.getShiftId(), "?"));
        }
        List<Column> columns = new ArrayList<>(List.of(Column.text("Employee No"), Column.text("Name")));
        List<LocalDate> dates = start.datesUntil(end.plusDays(1)).toList();
        dates.forEach(d -> columns.add(Column.text(d.toString())));
        List<List<Object>> rows = new ArrayList<>();
        v.employees().stream().sorted(Comparator.comparing(Employee::getEmployeeNumber)).forEach(e -> {
            List<Object> row = new ArrayList<>(List.of(e.getEmployeeNumber(), ReportSupport.name(e)));
            Map<LocalDate, String> cells = grid.getOrDefault(e.getId(), Map.of());
            dates.forEach(d -> row.add(cells.get(d)));
            rows.add(row);
        });
        return p.table(List.of(org.company(v.roster().getCompanyId()).getLegalName(),
                v.roster().getName() + " (" + start + " to " + end + "), " + v.roster().getStatus()),
                columns, rows, null);
    }
}
