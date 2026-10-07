package com.is2c.hrm.payroll.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.payroll.service.PayslipService;
import com.is2c.hrm.platform.export.ExportFormat;

/** FR-088 payslips and FR-089 payroll exports. */
@RestController
@RequestMapping("/api/v1/payroll/periods/{id}")
public class PayslipController {

    private final PayslipService payslips;

    public PayslipController(PayslipService payslips) {
        this.payslips = payslips;
    }

    public record EmailRequest(List<UUID> employeeIds) {
    }

    @GetMapping("/payslips/{employeeId}.pdf")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public ResponseEntity<byte[]> payslip(@PathVariable UUID id, @PathVariable UUID employeeId) {
        return file(payslips.payslip(id, employeeId));
    }

    @GetMapping("/payslips.pdf")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public ResponseEntity<byte[]> bulk(@PathVariable UUID id, @RequestParam(required = false) UUID departmentId) {
        return file(payslips.bulk(id, departmentId));
    }

    @PostMapping("/payslips/email")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW') and hasAuthority('PAYROLL:EXPORT')")
    public PayslipService.EmailReport email(@PathVariable UUID id, @RequestBody(required = false) EmailRequest body) {
        return payslips.email(id, body == null ? null : body.employeeIds());
    }

    @GetMapping("/register")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW') and hasAuthority('PAYROLL:EXPORT')")
    public ResponseEntity<byte[]> register(@PathVariable UUID id, @RequestParam String format) {
        return file(payslips.register(id, ExportFormat.parse(format)));
    }

    @GetMapping("/department-summary")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW') and hasAuthority('PAYROLL:EXPORT')")
    public ResponseEntity<byte[]> departmentSummary(@PathVariable UUID id, @RequestParam String format) {
        return file(payslips.departmentSummary(id, ExportFormat.parse(format)));
    }

    static ResponseEntity<byte[]> file(PayslipService.Export export) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }
}
