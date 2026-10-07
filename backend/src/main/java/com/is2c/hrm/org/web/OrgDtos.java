package com.is2c.hrm.org.web;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.Company;
import com.is2c.hrm.org.domain.CompanyLookup;
import com.is2c.hrm.org.domain.CompanySettings;
import com.is2c.hrm.org.domain.Department;
import com.is2c.hrm.org.service.StructureService;

/** Response DTOs for the org API. */
final class OrgDtos {

    private OrgDtos() {
    }

    record CompanyDto(UUID id, String code, String legalName, String registrationNo, String address, String phone,
            String email, String currency, String epfEmployerNo, String etfNo, String statutoryPack, String timeZone,
            Long version) {
        static CompanyDto of(Company c) {
            return new CompanyDto(c.getId(), c.getCode(), c.getLegalName(), c.getRegistrationNo(), c.getAddress(),
                    c.getPhone(), c.getEmail(), c.getCurrency(), c.getEpfEmployerNo(), c.getEtfNo(),
                    c.getStatutoryPack(), c.getTimeZone(), c.getVersion());
        }
    }

    record SettingsDto(UUID companyId, Set<DayOfWeek> weeklyOffDays, String employeeNumberFormat, int payrollStartDay,
            boolean absentAsNoPay, BigDecimal noPayDivisor, int punchWindowBeforeMinutes, int punchWindowAfterMinutes,
            DayOfWeek weekStartDay, Long version) {
        static SettingsDto of(CompanySettings s) {
            return new SettingsDto(s.getCompanyId(), s.getWeeklyOffDays(), s.getEmployeeNumberFormat(),
                    s.getPayrollStartDay(), s.isAbsentAsNoPay(), s.getNoPayDivisor(), s.getPunchWindowBeforeMinutes(),
                    s.getPunchWindowAfterMinutes(), s.getWeekStartDay(), s.getVersion());
        }
    }

    record BranchDto(UUID id, UUID companyId, String code, String name, String address, String phone, Long version) {
        static BranchDto of(Branch b) {
            return new BranchDto(b.getId(), b.getCompanyId(), b.getCode(), b.getName(), b.getAddress(), b.getPhone(),
                    b.getVersion());
        }
    }

    record DepartmentDto(UUID id, UUID companyId, UUID parentId, UUID branchId, String code, String name,
            String costCenterCode, UUID managerEmployeeId, Long version, List<DepartmentDto> children) {
        static DepartmentDto of(Department d) {
            return new DepartmentDto(d.getId(), d.getCompanyId(), d.getParentId(), d.getBranchId(), d.getCode(),
                    d.getName(), d.getCostCenterCode(), d.getManagerEmployeeId(), d.getVersion(), List.of());
        }

        static DepartmentDto of(StructureService.DepartmentNode n) {
            Department d = n.department();
            return new DepartmentDto(d.getId(), d.getCompanyId(), d.getParentId(), d.getBranchId(), d.getCode(),
                    d.getName(), d.getCostCenterCode(), d.getManagerEmployeeId(), d.getVersion(),
                    n.children().stream().map(DepartmentDto::of).toList());
        }
    }

    record LookupDto(UUID id, UUID companyId, String code, String name, Integer rank, Boolean otEligible, Long version) {
        static LookupDto of(CompanyLookup l) {
            StructureService.LookupData d = StructureService.toLookupData(l);
            return new LookupDto(l.getId(), l.getCompanyId(), d.code(), d.name(), d.rank(), d.otEligible(),
                    l.getVersion());
        }
    }
}
