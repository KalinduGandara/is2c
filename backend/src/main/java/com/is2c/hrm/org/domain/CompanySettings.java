package com.is2c.hrm.org.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;
import com.is2c.hrm.common.domain.DayOfWeekSetConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** FR-016 per-company policy settings. Defaults are applied on company creation. */
@Entity
@Table(name = "company_settings")
public class CompanySettings extends BaseEntity {

    public static final String DEFAULT_NUMBER_FORMAT = "{COMPANY}{SEQ:5}";

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Convert(converter = DayOfWeekSetConverter.class)
    @Column(name = "weekly_off_days", nullable = false)
    private Set<DayOfWeek> weeklyOffDays = EnumSet.of(DayOfWeek.SUNDAY);

    @Column(name = "employee_number_format", nullable = false)
    private String employeeNumberFormat = DEFAULT_NUMBER_FORMAT;

    @Column(name = "payroll_start_day", nullable = false)
    private int payrollStartDay = 1;

    @Column(name = "absent_as_no_pay", nullable = false)
    private boolean absentAsNoPay = true;

    @Column(name = "no_pay_divisor", nullable = false)
    private BigDecimal noPayDivisor = BigDecimal.valueOf(30);

    @Column(name = "punch_window_before_min", nullable = false)
    private int punchWindowBeforeMinutes = 240;

    @Column(name = "punch_window_after_min", nullable = false)
    private int punchWindowAfterMinutes = 240;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_start_day", nullable = false)
    private DayOfWeek weekStartDay = DayOfWeek.MONDAY;

    protected CompanySettings() {
    }

    public CompanySettings(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public Set<DayOfWeek> getWeeklyOffDays() {
        return weeklyOffDays;
    }

    public void setWeeklyOffDays(Set<DayOfWeek> weeklyOffDays) {
        this.weeklyOffDays = weeklyOffDays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weeklyOffDays);
    }

    public String getEmployeeNumberFormat() {
        return employeeNumberFormat;
    }

    public void setEmployeeNumberFormat(String employeeNumberFormat) {
        this.employeeNumberFormat = employeeNumberFormat;
    }

    public int getPayrollStartDay() {
        return payrollStartDay;
    }

    public void setPayrollStartDay(int payrollStartDay) {
        this.payrollStartDay = payrollStartDay;
    }

    public boolean isAbsentAsNoPay() {
        return absentAsNoPay;
    }

    public void setAbsentAsNoPay(boolean absentAsNoPay) {
        this.absentAsNoPay = absentAsNoPay;
    }

    public BigDecimal getNoPayDivisor() {
        return noPayDivisor;
    }

    public void setNoPayDivisor(BigDecimal noPayDivisor) {
        this.noPayDivisor = noPayDivisor;
    }

    public int getPunchWindowBeforeMinutes() {
        return punchWindowBeforeMinutes;
    }

    public void setPunchWindowBeforeMinutes(int punchWindowBeforeMinutes) {
        this.punchWindowBeforeMinutes = punchWindowBeforeMinutes;
    }

    public int getPunchWindowAfterMinutes() {
        return punchWindowAfterMinutes;
    }

    public void setPunchWindowAfterMinutes(int punchWindowAfterMinutes) {
        this.punchWindowAfterMinutes = punchWindowAfterMinutes;
    }

    public DayOfWeek getWeekStartDay() {
        return weekStartDay;
    }

    public void setWeekStartDay(DayOfWeek weekStartDay) {
        this.weekStartDay = weekStartDay;
    }
}
