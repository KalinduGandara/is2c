package com.is2c.hrm.payroll.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.payroll.service.PayslipService;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-088 / FR-100 employees' own finalized payslips. */
@RestController
@RequestMapping("/api/v1/me/payslips")
public class MyPayslipController {

    private final PayslipService payslips;

    public MyPayslipController(PayslipService payslips) {
        this.payslips = payslips;
    }

    private static UUID employee() {
        UUID id = CurrentUser.get().employeeId();
        if (id == null) {
            throw new NotFoundException("No employee is linked to this user");
        }
        return id;
    }

    @GetMapping
    public List<PayslipService.OwnPayslip> list() {
        return payslips.own(employee());
    }

    @GetMapping("/{periodId}.pdf")
    public ResponseEntity<byte[]> download(@PathVariable UUID periodId) {
        return PayslipController.file(payslips.ownPayslip(employee(), periodId));
    }
}
