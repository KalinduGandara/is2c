**PREMIUM HRM SYSTEM**

*Software Requirements Specification (SRS)*

**Prepared for IS 2 C Technologies (Pvt) Ltd.**

| Field | Details |
| :---- | :---- |
| Document Type | Software Requirements Specification |
| Version | 1.0 |
| Product | Premium HRM, Payroll & Workforce Management System |
| Deployment | Local On-Premises and Cloud Hosted |
| Target Market | Premium SME and Enterprise Customers |

**CONFIDENTIAL \- Product Planning & Software Engineering Document**

**1\. Executive Summary**

This document defines the requirements for a new Premium Human Resource Management (HRM) System to be developed as a separate product from IS2C TimePro. The new platform is intended for organizations that need integrated Employee Management, Payroll, Leave Management, Attendance, Shift and Roster Planning, Approval Workflows, Reporting and Administrative Controls.

The system must support both Local/On-Premises and Cloud Hosted deployment models from one maintainable code base. Business policies such as attendance rules, leave policies, overtime rules and payroll components must be configurable through administration screens wherever practical instead of being hard-coded.

**1.1 Product Positioning**

| Product | Purpose |
| :---- | :---- |
| IS2C TimePro | Operational Time Attendance & Access Control platform with device integration, events, reports and manual attendance editing. |
| Premium HRM System | Full HR platform including payroll, leave, attendance, shift/roster, employee lifecycle, approvals and management reporting. |

**1.2 Primary Goals**

* Provide a complete HRM platform rather than only attendance software.  
* Integrate attendance with payroll using configurable business rules.  
* Support fixed shifts, rotating shifts, roster-based operations and overnight shifts.  
* Support configurable payroll components and customer-specific policies.  
* Allow local or cloud deployment using the same application.  
* Provide strong auditability, role-based security and management reporting.

**2\. Scope**

**2.1 In Scope**

| Module | Included Functionality |
| :---- | :---- |
| Employee Master | Profiles, employment history, documents, department, designation and status. |
| Organization Structure | Company, branches, departments, locations, cost centers and hierarchy. |
| Attendance | Biometric/manual attendance, work hours, late/early, overtime and corrections. |
| Shift & Roster | Shift templates, rotating shifts, weekly/monthly rosters and overnight shifts. |
| Leave Management | Leave types, balances, accrual, applications, approvals and leave calendar. |
| Payroll | Salary structures, allowances, deductions, OT, attendance-driven payroll and payslips. |
| Approval Engine | Configurable multi-level workflows for leave, attendance and payroll changes. |
| Reports | HR, payroll, attendance, leave and management reports with export capability. |
| Security | Role-based access, audit logs and branch/department restrictions. |
| Deployment | Local On-Premises and Cloud Hosted editions. |

**2.2 Out of Scope for Version 1**

* Recruitment / Applicant Tracking  
* Performance Appraisal and KPI Management  
* Learning Management  
* Employee Expense Claims  
* Dedicated Mobile Application  
* Advanced AI Workforce Forecasting

**3\. User Roles & Access Control**

| Role | Permissions Summary |
| :---- | :---- |
| Super Administrator | System configuration, licensing, deployment and global administration. |
| Company Administrator | Company setup, branches, departments, users and policies. |
| HR Manager | Employee records, leave, attendance corrections, approvals and HR reports. |
| Payroll Officer | Salary setup, payroll processing, adjustments and payslips. |
| Branch Manager | Branch-level employee visibility, approvals and reports. |
| Department Manager | Team roster, leave approvals and attendance review. |
| Supervisor | Roster management and first-level approvals where permitted. |
| Employee | Self-service view of profile, attendance, leave and payslips through web portal. |
| Auditor | Read-only access to selected modules and audit logs. |

* Permissions must support View, Add, Edit, Delete, Approve, Export and Configure actions.  
* Data access must be restrictable by branch and department.  
* Sensitive payroll data must only be visible to authorized users.  
* Critical changes must generate immutable audit records.

**4\. Functional Requirements**

**4.1 Organization & Employee Master**

* Company profile and multi-branch support.  
* Department, designation, grade and employment category management.  
* Employee number generation with configurable format.  
* Personal details, emergency contacts and document repository.  
* Employment dates, confirmation/contract dates and reporting manager.  
* Employee statuses such as active, resigned, and terminated.  
* Custom employee fields for customer-specific data.

**4.2 Attendance Management**

* Import attendance events from supported biometric middleware/API.  
* Support multiple punches per day.  
* Manual attendance record creation by authorized HR/Admin users.  
* Manual editing of IN/OUT time with mandatory reason and audit trail.  
* Late arrival, early departure, and short-hours calculation.  
* Break deduction rules.  
* Overtime and extra-hours calculation based on policy.  
* Holiday and weekly-off awareness.  
* Attendance lock after payroll closure with authorized override.

**4.3 Shift Management**

| Shift Type | Description |
| :---- | :---- |
| Fixed Shift | Standard office shift assigned permanently. |
| Flexible Shift | Required hours with flexible start/end. |
| Rotating Shift | Employees rotate between predefined shifts. |
| Night Shift | Cross-midnight shift with correct attendance date handling. |
| Split Shift | Two working sessions in one day. |
| Custom Shift | Customer-defined start/end, grace, break and OT rules. |

**4.4 Roster Planning**

* Weekly roster creation  
* Monthly roster creation  
* Department-wise roster  
* Employee-wise roster  
* Bulk shift assignment  
* Copy previous roster  
* Roster publish/lock  
* Revision history  
* Conflict detection for overlapping assignments

**4.5 Leave Management**

| Feature | Requirement |
| :---- | :---- |
| Leave Types | Annual, Casual, Medical, No Pay and fully configurable custom leave types. |
| Leave Balances | Opening balance, accrual, carry forward and expiry rules. |
| Leave Application | Date range, reason and supporting documents. |
| Half Day Leave | Morning/afternoon support. |
| Approval Workflow | Single or multi-level configurable workflow. |
| Leave Calendar | Holiday and leave visibility based on permission. |
| Payroll Impact | Approved unpaid leave automatically affects payroll where configured. |

**4.6 Payroll Management**

Payroll must be implemented as a configurable calculation engine. Salary components, overtime rules, deductions and customer policies must be configurable without source-code modification wherever practical.

* Employee salary structure assignment.  
* Basic salary.  
* Recurring and one-time allowances.  
* Recurring and one-time deductions.  
* Attendance-based deductions.  
* Unpaid leave deductions.  
* Overtime earnings.  
* Extra-hours earnings.  
* Holiday/public-holiday treatment configurable.  
* Bonus, incentive and commission components.  
* Payroll period management.  
* Draft, review, approval and finalization stages.  
* Payroll locking after approval.  
* Individual and bulk payslip generation.  
* Excel/PDF payroll export.

**4.7 Approval Workflow Engine**

* Configurable approval levels.  
* Different workflows by branch, department or transaction type.  
* Approver delegation for defined dates.  
* Pending, approved, rejected and cancelled states.  
* Mandatory rejection comments.  
* Notification hooks for email and future messaging integrations.

**5\. Core Business Workflows**

**5.1 Monthly Payroll Workflow**

1. Open payroll period.  
2. Import and validate attendance data.  
3. Review attendance exceptions.  
4. Approve attendance corrections.  
5. Finalize approved leave.  
6. Generate preliminary payroll.  
7. Review salary adjustments, overtime and deductions.  
8. Approve payroll.  
9. Lock payroll period.  
10. Generate payslips and management reports.

**5.2 Attendance Correction Workflow**

| Step | Requirement |
| :---- | :---- |
| 1 | Attendance issue is identified. |
| 2 | Authorized user creates or edits the attendance record. |
| 3 | Correction reason is mandatory. |
| 4 | Approval workflow executes if enabled. |
| 5 | Before/after values, user and timestamp are stored in audit history. |
| 6 | Payroll reflects the updated attendance if the payroll period is not locked. |

**5.3 Leave Workflow**

11. Employee submits leave request.  
12. System validates balance.  
13. Approver reviews request.  
14. Approved leave updates balance and attendance calendar.  
15. Payroll recognizes unpaid leave where applicable.

**5.4 Roster Workflow**

16. Create shift templates.  
17. Assign employees/departments.  
18. Publish weekly/monthly roster.  
19. Employees/supervisors view roster.  
20. Changes after publication create revision history.

**6\. Reporting & Dashboards**

| Report | Contents |
| :---- | :---- |
| Executive Dashboard | Headcount, attendance %, absence, leave and payroll cost overview. |
| HR Dashboard | Employee movement, probation, document expiry and leave utilization. |
| Attendance Dashboard | Present, absent, late, early departure, OT and shift compliance. |
| Payroll Dashboard | Payroll totals, allowances, deductions, OT cost and department comparison. |
| Leave Reports | Balances, usage, pending approvals and trends. |
| Shift Reports | Roster coverage, staffing gaps and overtime dependency. |
| Employee Reports | Individual attendance, leave and payroll history. |
| Audit Reports | Attendance edits, payroll edits, approvals and configuration changes. |

* PDF export for official reports and payslips.  
* Excel export for HR/payroll analysis.  
* CSV export where required for integrations.  
* Print-friendly layouts.

**7\. Deployment Architecture**

| Edition | Description |
| :---- | :---- |
| On-Premises | Installed on customer server with local database and LAN/browser access. |
| Cloud Hosted | Secure HTTPS hosting with customer isolation and centralized administration. |

* Single application code base for both deployment models.  
* Responsive web user interface.  
* REST API backend.  
* SQL Server or PostgreSQL database.  
* Role-based authentication.  
* Automated backup support.  
* Environment/configuration-driven deployment.  
* Tenant isolation strategy for cloud deployment.

**7.1 Suggested Technology Direction**

| Layer | Recommendation |
| :---- | :---- |
| Frontend | React with vite |
| Backend |  Java Spring boot |
| Database | PostgreSQL. |
| Authentication | Secure identity management with role-based authorization. |
| Reporting | Integrated PDF and Excel reporting engine. |
| Hosting | Windows/Linux compatible hosting using containers or standard application services. |

**8\. Core Data Model**

| Entity | Purpose |
| :---- | :---- |
| Company | Organization master |
| Branch | Branch/location |
| Department | Department hierarchy |
| Designation | Job title |
| Employee | Employee master profile |
| EmploymentHistory | Employment lifecycle |
| Shift | Shift template |
| Roster | Published employee roster |
| AttendanceEvent | Raw biometric event |
| AttendanceDaily | Processed daily attendance |
| AttendanceAdjustment | Manual correction with audit |
| LeaveType | Leave policy |
| LeaveBalance | Employee leave balance |
| LeaveRequest | Leave application |
| SalaryStructure | Employee salary setup |
| PayrollComponent | Allowance/deduction definitions |
| PayrollPeriod | Payroll cycle |
| PayrollTransaction | Employee payroll result |
| Payslip | Generated salary statement |
| ApprovalTransaction | Workflow approvals |
| HolidayCalendar | Holiday configuration |
| AuditLog | Immutable activity history |

* Use unique primary keys and enforce referential integrity.  
* Payroll records must remain immutable after finalization.  
* Attendance edits must preserve original values.  
* Use soft delete where historical business records must remain.  
* Audit logs must not be editable through standard application screens.

**9\. Integration Requirements**

| Integration | Requirement |
| :---- | :---- |
| Biometric Middleware | Import attendance events through an adapter/integration layer. |
| Email | Approval notifications, payslip delivery and scheduled reports. |
| PDF Generator | Payslips and official reports. |
| Excel Export | Operational and management reporting. |
| Future ERP/API | REST endpoints for future third-party integrations. |

The HRM product should not depend directly on a single biometric manufacturer. Attendance ingestion must be abstracted so supported device integrations can map into a standard attendance event model.

**10\. Non-Functional Requirements**

| Area | Requirement |
| :---- | :---- |
| Performance | Business screens and common reports should respond within acceptable enterprise application response times. |
| Scalability | Support growth from SME to multi-branch enterprise customers. |
| Security | HTTPS, password hashing, role-based authorization and audit logs. |
| Backup | Scheduled database backup with restore procedure. |
| Localization | English initially; architecture should support Sinhala/Tamil localization later. |
| Configurability | Policies and payroll rules configurable without code changes where possible. |
| Maintainability | Modular source code, documented APIs and database migrations. |
| Logging | Application, error and security event logging. |
| Browser Support | Modern Chromium-based and enterprise browsers. |

**11\. Suggested UI Modules**

* Dashboard  
* Employee Hub  
* Organization Setup  
* Attendance  
* Shift & Roster Calendar  
* Leave Management  
* Payroll  
* Reports  
* Administration

**11.1 Dashboard KPI Suggestions**

* Total active employees  
* Present today  
* Absent today  
* Late arrivals  
* Employees on leave  
* Pending approvals  
* Current payroll status  
* Monthly overtime cost  
* Department headcount distribution

**12\. Acceptance Criteria**

| Module | Acceptance Requirement |
| :---- | :---- |
| Employee | Create, edit, deactivate and search employee records. |
| Attendance | Import events, calculate attendance and allow audited manual edits. |
| Shift | Support fixed, rotating and overnight shifts. |
| Roster | Publish weekly/monthly rosters and track revisions. |
| Leave | Manage balances, applications and approvals. |
| Payroll | Generate payroll from configurable salary and attendance rules. |
| Approval | Multi-level workflows operate according to configuration. |
| Reports | Generate PDF and Excel reports. |
| Security | Enforce permissions and audit logging. |
| Deployment | Operate in both local and cloud deployment modes. |

**13\. Recommended Development Roadmap**

| Phase | Deliverables |
| :---- | :---- |
| Phase 1 | Authentication, users, roles, organization and employee master. |
| Phase 2 | Attendance engine and biometric integration layer. |
| Phase 3 | Shift, roster, holiday calendar and leave management. |
| Phase 4 | Payroll engine, salary structures and payslips. |
| Phase 5 | Dashboards, reports, approvals and analytics. |
| Phase 6 | Cloud multi-tenant deployment, licensing, backup automation and APIs. |

**13.1 MVP Modules**

* Employee Master  
* Organization Structure  
* Shift & Roster  
* Attendance  
* Manual Attendance Editing  
* Leave Management  
* Payroll  
* Payslip  
* Basic Reports  
* Role-Based Security

**14\. Engineering Notes & Open Decisions**

* Final product name is not defined in this document.  
* Statutory payroll rules must be configurable; do not hard-code country-specific legal values directly into source code.  
* Employee self-service can initially be a responsive web portal rather than a mobile app.  
* Attendance integration should use an adapter layer to support additional biometric vendors later.  
* Cloud and on-premises editions should share core modules.  
* Every payroll-affecting attendance edit must store before/after values and user identity.

**15\. Engineering Deliverables**

* Complete source code repository  
* Database schema and migration scripts  
* API documentation  
* System architecture document  
* On-Premises installation guide  
* Cloud deployment guide  
* Administrator user manual  
* HR and Payroll user manual  
* Test plan and test cases  
* UAT checklist  
* Backup and recovery procedure  
* Version release/update procedure

**End of Software Requirements Specification**