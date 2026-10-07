package com.is2c.hrm.payroll.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.payroll.domain.SalaryStructure;
import com.is2c.hrm.payroll.domain.StructureLine;
import com.is2c.hrm.payroll.service.SalaryStructureService;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}/salary-structures")
public class SalaryStructureController {

    private final SalaryStructureService structures;

    public SalaryStructureController(SalaryStructureService structures) {
        this.structures = structures;
    }

    public record StructureDto(UUID id, UUID employeeId, LocalDate effectiveFrom, LocalDate effectiveTo,
            BigDecimal basicSalary, List<StructureLine> lines, Long version) {
        static StructureDto of(SalaryStructure s) {
            return new StructureDto(s.getId(), s.getEmployeeId(), s.getEffectiveFrom(), s.getEffectiveTo(),
                    s.getBasicSalary(), List.copyOf(s.getLines()), s.getVersion());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<StructureDto> history(@PathVariable UUID employeeId) {
        return structures.history(employeeId).stream().map(StructureDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PAYROLL:ADD', 'PAYROLL:EDIT')")
    public StructureDto create(@PathVariable UUID employeeId, @RequestBody SalaryStructureService.StructureData body) {
        return StructureDto.of(structures.create(employeeId, body));
    }
}
