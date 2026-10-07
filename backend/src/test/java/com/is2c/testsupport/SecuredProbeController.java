package com.is2c.testsupport;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints guarded by specific permissions. */
@RestController
public class SecuredProbeController {

    @GetMapping("/api/v1/test-secured/employee-view")
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    String employeeView() {
        return "ok";
    }

    @GetMapping("/api/v1/test-secured/payroll-configure")
    @PreAuthorize("hasAuthority('PAYROLL:CONFIGURE')")
    String payrollConfigure() {
        return "ok";
    }
}
