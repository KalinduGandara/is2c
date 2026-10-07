package com.is2c.hrm.payroll.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.domain.PayrollException;
import com.is2c.hrm.payroll.domain.PayrollOneTimeItem;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.domain.PayrollResult;
import com.is2c.hrm.payroll.domain.PayrollResultLine;
import com.is2c.hrm.payroll.service.PayrollPeriodService;
import com.is2c.hrm.payroll.service.PayrollProcessor;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollPeriodController {

    private final PayrollPeriodService periods;

    public PayrollPeriodController(PayrollPeriodService periods) {
        this.periods = periods;
    }

    public record OpenRequest(UUID companyId, int year, int month) {
    }

    public record PeriodDto(UUID id, UUID companyId, int year, int month, LocalDate startDate, LocalDate endDate,
            PayrollPeriod.Status status, boolean stale, UUID approvalId, Instant processedAt, Instant finalizedAt,
            Long version) {
        public static PeriodDto of(PayrollPeriod p) {
            return new PeriodDto(p.getId(), p.getCompanyId(), p.getYear(), p.getMonth(), p.getStartDate(),
                    p.getEndDate(), p.getStatus(), p.isStale(), p.getApprovalId(), p.getProcessedAt(),
                    p.getFinalizedAt(), p.getVersion());
        }
    }

    public record ResultDto(UUID id, UUID employeeId, String employeeNumber, String employeeName,
            String departmentName, String designationName, BigDecimal gross, BigDecimal deductions, BigDecimal net,
            BigDecimal employerContributions, BigDecimal workedDays, BigDecimal noPayDays, BigDecimal otHours,
            String payslipNo, boolean locked) {
        static ResultDto of(PayrollResult r) {
            return new ResultDto(r.getId(), r.getEmployeeId(), r.getEmployeeNumber(), r.getEmployeeName(),
                    r.getDepartmentName(), r.getDesignationName(), r.getGross(), r.getDeductions(), r.getNet(),
                    r.getEmployerContributions(), r.getWorkedDays(), r.getNoPayDays(), r.getOtHours(), r.getPayslipNo(),
                    r.isLocked());
        }
    }

    public record LineDto(String componentCode, String componentName, ComponentKind kind, BigDecimal units,
            BigDecimal rate, BigDecimal amount, boolean showOnPayslip) {
        static LineDto of(PayrollResultLine l) {
            return new LineDto(l.getComponentCode(), l.getComponentName(), l.getKind(), l.getUnits(), l.getRate(),
                    l.getAmount(), l.isShowOnPayslip());
        }
    }

    public record ResultDetail(ResultDto result, List<LineDto> lines) {
    }

    public record ExceptionDto(UUID employeeId, String code, String message, boolean blocking) {
    }

    public record ItemDto(UUID id, UUID employeeId, UUID componentId, BigDecimal amount, String note, Long version) {
        static ItemDto of(PayrollOneTimeItem i) {
            return new ItemDto(i.getId(), i.getEmployeeId(), i.getComponentId(), i.getAmount(), i.getNote(),
                    i.getVersion());
        }
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<PeriodDto> list(@RequestParam UUID companyId) {
        return periods.list(companyId).stream().map(PeriodDto::of).toList();
    }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL:ADD')")
    public PeriodDto open(@RequestBody OpenRequest body) {
        return PeriodDto.of(periods.open(body.companyId(), body.year(), body.month()));
    }

    @GetMapping("/periods/{id}")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public PeriodDto get(@PathVariable UUID id) {
        return PeriodDto.of(periods.get(id));
    }

    @PostMapping("/periods/{id}/process")
    @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
    public PayrollProcessor.Summary process(@PathVariable UUID id) {
        return periods.process(id);
    }

    @GetMapping("/periods/{id}/results")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<ResultDto> results(@PathVariable UUID id) {
        return periods.results(id).stream().map(ResultDto::of).toList();
    }

    @GetMapping("/periods/{id}/results/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public ResultDetail result(@PathVariable UUID id, @PathVariable UUID employeeId) {
        PayrollPeriodService.ResultView v = periods.result(id, employeeId);
        return new ResultDetail(ResultDto.of(v.result()), v.lines().stream().map(LineDto::of).toList());
    }

    @GetMapping("/periods/{id}/exceptions")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<ExceptionDto> exceptions(@PathVariable UUID id) {
        return periods.exceptions(id).stream().map((PayrollException e) -> new ExceptionDto(e.getEmployeeId(),
                e.getCode(), e.getMessage(), e.isBlocking())).toList();
    }

    @GetMapping("/periods/{id}/totals")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public PayrollPeriodService.Totals totals(@PathVariable UUID id) {
        return periods.totals(id);
    }

    @GetMapping("/periods/{id}/items")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<ItemDto> items(@PathVariable UUID id) {
        return periods.items(id).stream().map(ItemDto::of).toList();
    }

    @PostMapping("/periods/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
    public ItemDto addItem(@PathVariable UUID id, @RequestBody PayrollPeriodService.ItemData body) {
        return ItemDto.of(periods.addItem(id, body));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
    public ItemDto updateItem(@PathVariable UUID itemId, @RequestBody PayrollPeriodService.ItemData body) {
        return ItemDto.of(periods.updateItem(itemId, body));
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
    public void deleteItem(@PathVariable UUID itemId) {
        periods.deleteItem(itemId);
    }
}
