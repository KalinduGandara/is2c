package com.is2c.hrm.employee.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.is2c.hrm.common.domain.DayOfWeekSetConverter;
import com.is2c.hrm.common.domain.SoftDeletableEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Employee master profile (FR-015). Contains no salary data (FR-007). */
@Entity
@Table(name = "employee")
@SQLRestriction("deleted_at IS NULL")
public class Employee extends SoftDeletableEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "branch_id")
    private UUID branchId;
    @Column(name = "department_id")
    private UUID departmentId;
    @Column(name = "designation_id")
    private UUID designationId;
    @Column(name = "grade_id")
    private UUID gradeId;
    @Column(name = "category_id")
    private UUID categoryId;
    @Column(name = "employee_number")
    private String employeeNumber;
    @Column(name = "title")
    private String title;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "preferred_name")
    private String preferredName;
    @Column(name = "nic")
    private String nic;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;
    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status")
    private MaritalStatus maritalStatus;
    @Column(name = "nationality")
    private String nationality;
    @Column(name = "personal_email")
    private String personalEmail;
    @Column(name = "mobile")
    private String mobile;
    @Column(name = "address")
    private String address;
    @Column(name = "join_date")
    private LocalDate joinDate;
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;
    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;
    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;
    @Column(name = "exit_date")
    private LocalDate exitDate;
    @Column(name = "exit_reason")
    private String exitReason;
    @Column(name = "reporting_manager_id")
    private UUID reportingManagerId;
    @Column(name = "default_shift_id")
    private UUID defaultShiftId;
    @Convert(converter = DayOfWeekSetConverter.class)
    @Column(name = "weekly_off_days")
    private Set<DayOfWeek> weeklyOffDays;
    @Column(name = "badge_id")
    private String badgeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EmployeeStatus status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "emergency_contact", joinColumns = @JoinColumn(name = "employee_id"))
    @OrderColumn(name = "sort_order")
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    protected Employee() {
    }

    public Employee(UUID companyId) {
        this.companyId = companyId;
        this.status = EmployeeStatus.ACTIVE;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public List<EmergencyContact> getEmergencyContacts() {
        return emergencyContacts;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getDesignationId() {
        return designationId;
    }

    public void setDesignationId(UUID designationId) {
        this.designationId = designationId;
    }

    public UUID getGradeId() {
        return gradeId;
    }

    public void setGradeId(UUID gradeId) {
        this.gradeId = gradeId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public LocalDate getProbationEndDate() {
        return probationEndDate;
    }

    public void setProbationEndDate(LocalDate probationEndDate) {
        this.probationEndDate = probationEndDate;
    }

    public LocalDate getConfirmationDate() {
        return confirmationDate;
    }

    public void setConfirmationDate(LocalDate confirmationDate) {
        this.confirmationDate = confirmationDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public void setContractEndDate(LocalDate contractEndDate) {
        this.contractEndDate = contractEndDate;
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    public UUID getReportingManagerId() {
        return reportingManagerId;
    }

    public void setReportingManagerId(UUID reportingManagerId) {
        this.reportingManagerId = reportingManagerId;
    }

    public UUID getDefaultShiftId() {
        return defaultShiftId;
    }

    public void setDefaultShiftId(UUID defaultShiftId) {
        this.defaultShiftId = defaultShiftId;
    }

    public Set<DayOfWeek> getWeeklyOffDays() {
        return weeklyOffDays;
    }

    public void setWeeklyOffDays(Set<DayOfWeek> weeklyOffDays) {
        this.weeklyOffDays = weeklyOffDays;
    }

    public String getBadgeId() {
        return badgeId;
    }

    public void setBadgeId(String badgeId) {
        this.badgeId = badgeId;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }
}
