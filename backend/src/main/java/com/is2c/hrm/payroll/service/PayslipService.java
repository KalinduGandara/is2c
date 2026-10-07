package com.is2c.hrm.payroll.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.InvalidStateException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.notification.api.Notification;
import com.is2c.hrm.notification.service.NotificationService;
import com.is2c.hrm.org.domain.Company;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.domain.PayrollResult;
import com.is2c.hrm.payroll.domain.PayrollResultLine;
import com.is2c.hrm.payroll.domain.PayrollPeriodRepository;
import com.is2c.hrm.payroll.domain.PayrollResultLineRepository;
import com.is2c.hrm.payroll.domain.PayrollResultRepository;
import com.is2c.hrm.platform.export.ExportFormat;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableData.Column;
import com.is2c.hrm.platform.export.TableExporter;
import com.is2c.hrm.security.api.CurrentUser;

/**
 * FR-088 payslips (single, bulk, email) and FR-089 register / department summary exports. Everything is rendered
 * from the stored result snapshot only, so repeated generation yields identical figures.
 */
@Service
public class PayslipService {

    private final PayrollPeriodService periods;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollResultRepository results;
    private final PayrollResultLineRepository lines;
    private final EmployeeService employees;
    private final OrgDirectory org;
    private final NotificationService notifications;
    private final TableExporter exporter;
    private final AuditService audit;
    private final Clock clock;

    public PayslipService(PayrollPeriodService periods, PayrollPeriodRepository periodRepository,
            PayrollResultRepository results, PayrollResultLineRepository lines, EmployeeService employees,
            OrgDirectory org, NotificationService notifications, TableExporter exporter, AuditService audit,
            Clock clock) {
        this.periods = periods;
        this.periodRepository = periodRepository;
        this.results = results;
        this.lines = lines;
        this.employees = employees;
        this.org = org;
        this.notifications = notifications;
        this.exporter = exporter;
        this.audit = audit;
        this.clock = clock;
    }

    public record Export(String fileName, String contentType, byte[] content) {
    }

    public enum DeliveryStatus { SENT, FAILED, NO_EMAIL }

    public record Delivery(UUID employeeId, String employeeNumber, String employeeName, DeliveryStatus status) {
    }

    public record EmailReport(int sent, int failed, List<Delivery> results) {
    }

    // ---------------------------------------------------------------- payslips (FR-088)

    @Transactional(readOnly = true)
    public Export payslip(UUID periodId, UUID employeeId) {
        PayrollPeriod p = finalized(periodId);
        PayrollPeriodService.ResultView v = periods.result(periodId, employeeId);
        return new Export("payslip-" + v.result().getEmployeeNumber() + "-" + key(p) + ".pdf", "application/pdf",
                render(p, List.of(v)));
    }

    @Transactional(readOnly = true)
    public Export bulk(UUID periodId, UUID departmentId) {
        PayrollPeriod p = finalized(periodId);
        List<PayrollResult> selected = periods.results(periodId);
        if (departmentId != null) {
            Map<UUID, Employee> byId = employees.byIds(selected.stream().map(PayrollResult::getEmployeeId).toList());
            selected = selected.stream().filter(r -> byId.containsKey(r.getEmployeeId())
                    && departmentId.equals(byId.get(r.getEmployeeId()).getDepartmentId())).toList();
        }
        return new Export("payslips-" + key(p) + ".pdf", "application/pdf", render(p, withLines(selected)));
    }

    @Transactional
    public EmailReport email(UUID periodId, Collection<UUID> employeeIds) {
        PayrollPeriod p = finalized(periodId);
        if (!notifications.emailEnabled()) {
            throw new ValidationException("Email delivery is disabled; enable mail (hrm.mail.enabled) to email payslips");
        }
        Set<UUID> wanted = employeeIds == null || employeeIds.isEmpty() ? null : Set.copyOf(employeeIds);
        List<PayrollResult> selected = periods.results(periodId).stream()
                .filter(r -> wanted == null || wanted.contains(r.getEmployeeId())).toList();
        Map<UUID, Employee> byId = employees.byIds(selected.stream().map(PayrollResult::getEmployeeId).toList());
        String company = org.company(p.getCompanyId()).getLegalName();
        List<Delivery> results = new ArrayList<>();
        int sent = 0;
        for (PayrollPeriodService.ResultView v : withLines(selected)) {
            PayrollResult r = v.result();
            Employee e = byId.get(r.getEmployeeId());
            String address = e == null ? null : e.getPersonalEmail();
            DeliveryStatus status;
            if (address == null || address.isBlank()) {
                status = DeliveryStatus.NO_EMAIL;
            } else {
                String file = "payslip-" + r.getEmployeeNumber() + "-" + key(p) + ".pdf";
                boolean ok = notifications.deliver(new Notification(List.of(address),
                        "Payslip " + label(p) + " — " + company,
                        "Dear " + r.getEmployeeName() + ",\n\nPlease find attached your payslip for " + label(p) + ".",
                        List.of(new Notification.Attachment(file, "application/pdf", render(p, List.of(v))))));
                status = ok ? DeliveryStatus.SENT : DeliveryStatus.FAILED;
            }
            if (status == DeliveryStatus.SENT) {
                sent++;
            }
            results.add(new Delivery(r.getEmployeeId(), r.getEmployeeNumber(), r.getEmployeeName(), status));
        }
        audit.record(AuditEntry.of("EMAIL_PAYSLIPS", "PAYROLL_PERIOD", periodId).company(p.getCompanyId())
                .after(Map.of("sent", sent, "notSent", results.size() - sent)));
        return new EmailReport(sent, results.size() - sent, results);
    }

    private PayrollPeriod finalized(UUID periodId) {
        PayrollPeriod p = periods.get(periodId);
        if (p.getStatus() != PayrollPeriod.Status.FINALIZED) {
            throw new InvalidStateException("Payslips are available once the period is finalized");
        }
        return p;
    }

    private List<PayrollPeriodService.ResultView> withLines(List<PayrollResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PayrollResultLine>> byResult = lines.findByResultIdIn(
                results.stream().map(PayrollResult::getId).toList()).stream()
                .sorted(Comparator.comparingInt(PayrollResultLine::getSortOrder))
                .collect(Collectors.groupingBy(PayrollResultLine::getResultId, LinkedHashMap::new, Collectors.toList()));
        return results.stream()
                .map(r -> new PayrollPeriodService.ResultView(r, byResult.getOrDefault(r.getId(), List.of()))).toList();
    }

    private byte[] render(PayrollPeriod p, List<PayrollPeriodService.ResultView> views) {
        Company company = org.company(p.getCompanyId());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        if (views.isEmpty()) {
            doc.add(new Paragraph("No payslips match the selection."));
        }
        boolean first = true;
        for (PayrollPeriodService.ResultView v : views) {
            if (!first) {
                doc.newPage();
            }
            first = false;
            payslipPage(doc, company, p, v);
        }
        doc.close();
        return out.toByteArray();
    }

    private static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font BOLD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 9);
    private static final Font SMALL = new Font(Font.HELVETICA, 7);

    private void payslipPage(Document doc, Company company, PayrollPeriod p, PayrollPeriodService.ResultView v) {
        PayrollResult r = v.result();
        doc.add(new Paragraph(company.getLegalName(), TITLE));
        if (company.getAddress() != null && !company.getAddress().isBlank()) {
            doc.add(new Paragraph(company.getAddress(), SMALL));
        }
        doc.add(new Paragraph("Payslip — " + label(p) + "  (" + p.getStartDate() + " to " + p.getEndDate() + ")", BOLD));
        doc.add(new Paragraph("Payslip No: " + r.getPayslipNo() + "    Currency: " + company.getCurrency(), NORMAL));
        doc.add(new Paragraph(" ", SMALL));

        PdfPTable who = new PdfPTable(4);
        who.setWidthPercentage(100);
        pair(who, "Employee No", r.getEmployeeNumber());
        pair(who, "Name", r.getEmployeeName());
        pair(who, "Department", nz(r.getDepartmentName()));
        pair(who, "Designation", nz(r.getDesignationName()));
        doc.add(who);
        doc.add(new Paragraph(" ", SMALL));

        PdfPTable pay = new PdfPTable(new float[] { 3, 1.4f, 3, 1.4f });
        pay.setWidthPercentage(100);
        List<PayrollResultLine> earnings = visible(v.lines(), ComponentKind.EARNING);
        List<PayrollResultLine> deductions = visible(v.lines(), ComponentKind.DEDUCTION);
        head(pay, "Earnings");
        head(pay, "Deductions");
        for (int i = 0; i < Math.max(earnings.size(), deductions.size()); i++) {
            lineCells(pay, i < earnings.size() ? earnings.get(i) : null);
            lineCells(pay, i < deductions.size() ? deductions.get(i) : null);
        }
        total(pay, "Gross pay", r.getGross());
        total(pay, "Total deductions", r.getDeductions());
        doc.add(pay);
        Paragraph net = new Paragraph("Net pay: " + TableExporter.amount(r.getNet()), TITLE);
        net.setAlignment(Element.ALIGN_RIGHT);
        doc.add(net);

        List<PayrollResultLine> employer = visible(v.lines(), ComponentKind.EMPLOYER_CONTRIBUTION);
        if (!employer.isEmpty()) {
            doc.add(new Paragraph(" ", SMALL));
            PdfPTable er = new PdfPTable(new float[] { 3, 1.4f });
            er.setWidthPercentage(50);
            er.setHorizontalAlignment(Element.ALIGN_LEFT);
            head(er, "Employer contributions");
            employer.forEach(l -> lineCells(er, l));
            doc.add(er);
        }

        doc.add(new Paragraph(" ", SMALL));
        PdfPTable att = new PdfPTable(6);
        att.setWidthPercentage(100);
        pair(att, "Worked days", plain(r.getWorkedDays()));
        pair(att, "No-pay days", plain(r.getNoPayDays()));
        pair(att, "OT hours", plain(r.getOtHours()));
        doc.add(att);
        doc.add(new Paragraph(" ", SMALL));
        doc.add(new Paragraph("Computer-generated payslip from the finalized payroll of " + label(p) + ".", SMALL));
    }

    private static List<PayrollResultLine> visible(List<PayrollResultLine> all, ComponentKind kind) {
        return all.stream().filter(l -> l.getKind() == kind && l.isShowOnPayslip()).toList();
    }

    private static void pair(PdfPTable t, String label, String value) {
        t.addCell(cell(label, BOLD, false, true));
        t.addCell(cell(value, NORMAL, false, false));
    }

    private static void head(PdfPTable t, String label) {
        PdfPCell c = cell(label, BOLD, false, true);
        c.setColspan(2);
        t.addCell(c);
    }

    private static void lineCells(PdfPTable t, PayrollResultLine l) {
        if (l == null) {
            t.addCell(cell("", NORMAL, false, false));
            t.addCell(cell("", NORMAL, true, false));
            return;
        }
        String name = l.getUnits() == null ? l.getComponentName()
                : l.getComponentName() + " (" + plain(l.getUnits()) + ")";
        t.addCell(cell(name, NORMAL, false, false));
        t.addCell(cell(TableExporter.amount(l.getAmount()), NORMAL, true, false));
    }

    private static void total(PdfPTable t, String label, BigDecimal amount) {
        t.addCell(cell(label, BOLD, false, true));
        t.addCell(cell(TableExporter.amount(amount), BOLD, true, true));
    }

    private static PdfPCell cell(String text, Font font, boolean right, boolean shaded) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(right ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setPadding(4);
        if (shaded) {
            c.setGrayFill(0.92f);
        }
        return c;
    }

    // ---------------------------------------------------------------- self-service (FR-088, FR-100)

    public record OwnPayslip(UUID periodId, int year, int month, String payslipNo, BigDecimal gross, BigDecimal net) {
    }

    /** Own finalized payslips, newest first (no scope check: the caller passes the linked employee). */
    @Transactional(readOnly = true)
    public List<OwnPayslip> own(UUID employeeId) {
        List<PayrollResult> locked = results.findByEmployeeIdAndLockedTrue(employeeId);
        Map<UUID, PayrollPeriod> byId = periodRepository.findAllById(locked.stream().map(PayrollResult::getPeriodId).toList())
                .stream().collect(Collectors.toMap(PayrollPeriod::getId, x -> x));
        return locked.stream().filter(r -> byId.containsKey(r.getPeriodId())).map(r -> {
            PayrollPeriod p = byId.get(r.getPeriodId());
            return new OwnPayslip(p.getId(), p.getYear(), p.getMonth(), r.getPayslipNo(), r.getGross(), r.getNet());
        }).sorted(Comparator.comparing((OwnPayslip o) -> o.year() * 100 + o.month()).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public Export ownPayslip(UUID employeeId, UUID periodId) {
        PayrollPeriod p = periodRepository.findById(periodId)
                .filter(x -> x.getStatus() == PayrollPeriod.Status.FINALIZED)
                .orElseThrow(() -> new NotFoundException("No finalized payslip for this period"));
        PayrollResult r = results.findByPeriodIdAndEmployeeId(periodId, employeeId).filter(PayrollResult::isLocked)
                .orElseThrow(() -> new NotFoundException("No finalized payslip for this period"));
        return new Export("payslip-" + r.getEmployeeNumber() + "-" + key(p) + ".pdf", "application/pdf",
                render(p, withLines(List.of(r))));
    }

    // ---------------------------------------------------------------- exports (FR-089)

    @Transactional(readOnly = true)
    public Export register(UUID periodId, ExportFormat format) {
        PayrollPeriod p = periods.get(periodId);
        return new Export("payroll-register-" + key(p) + "." + format.extension(), format.contentType(),
                exporter.export(registerTable(periodId), format));
    }

    /** FR-089 register: one row per employee in scope, one column per component, totals footer. */
    @Transactional(readOnly = true)
    public TableData registerTable(UUID periodId) {
        PayrollPeriod p = periods.get(periodId);
        List<PayrollPeriodService.ResultView> views = withLines(periods.results(periodId));
        // component columns in calculation order, first-seen name per code
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Integer> order = new TreeMap<>();
        views.forEach(v -> v.lines().forEach(l -> {
            names.putIfAbsent(l.getComponentCode(), l.getComponentName());
            order.merge(l.getComponentCode(), l.getSortOrder(), Math::min);
        }));
        List<String> codes = names.keySet().stream().sorted(Comparator.comparing(order::get)).toList();

        List<Column> columns = new ArrayList<>(List.of(Column.text("Employee No"), Column.text("Name"),
                Column.text("Department"), Column.text("Designation")));
        codes.forEach(c -> columns.add(Column.amount(names.get(c))));
        List.of("Gross", "Deductions", "Net", "Employer contributions").forEach(h -> columns.add(Column.amount(h)));

        List<List<Object>> rows = new ArrayList<>();
        BigDecimal[] totals = new BigDecimal[codes.size() + 4];
        java.util.Arrays.fill(totals, BigDecimal.ZERO);
        for (PayrollPeriodService.ResultView v : views) {
            PayrollResult r = v.result();
            Map<String, BigDecimal> amounts = v.lines().stream().collect(Collectors.toMap(
                    PayrollResultLine::getComponentCode, PayrollResultLine::getAmount, BigDecimal::add));
            List<Object> row = new ArrayList<>(List.of(r.getEmployeeNumber(), r.getEmployeeName(),
                    nz(r.getDepartmentName()), nz(r.getDesignationName())));
            List<BigDecimal> figures = new ArrayList<>();
            codes.forEach(c -> figures.add(amounts.get(c)));
            figures.addAll(List.of(r.getGross(), r.getDeductions(), r.getNet(), r.getEmployerContributions()));
            for (int i = 0; i < figures.size(); i++) {
                if (figures.get(i) != null) {
                    totals[i] = totals[i].add(figures.get(i));
                }
            }
            row.addAll(figures);
            rows.add(row);
        }
        List<Object> footer = new ArrayList<>(List.of("Total", views.size() + " employees", "", ""));
        footer.addAll(List.of(totals));
        return new TableData("Payroll register", header(p), columns, rows, footer, true,
                CurrentUser.get().username(), clock.instant());
    }

    @Transactional(readOnly = true)
    public Export departmentSummary(UUID periodId, ExportFormat format) {
        PayrollPeriod p = periods.get(periodId);
        return new Export("department-summary-" + key(p) + "." + format.extension(), format.contentType(),
                exporter.export(departmentSummaryTable(periodId), format));
    }

    /** FR-089 department summary: employees and totals per department snapshot name. */
    @Transactional(readOnly = true)
    public TableData departmentSummaryTable(UUID periodId) {
        PayrollPeriod p = periods.get(periodId);
        Map<String, BigDecimal[]> byDept = new TreeMap<>();
        for (PayrollResult r : periods.results(periodId)) {
            BigDecimal[] t = byDept.computeIfAbsent(nz(r.getDepartmentName()),
                    k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO });
            t[0] = t[0].add(BigDecimal.ONE);
            t[1] = t[1].add(r.getGross());
            t[2] = t[2].add(r.getDeductions());
            t[3] = t[3].add(r.getNet());
            t[4] = t[4].add(r.getEmployerContributions());
        }
        BigDecimal[] total = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        List<List<Object>> rows = new ArrayList<>();
        byDept.forEach((dept, t) -> {
            rows.add(List.of(dept, t[0].intValue(), t[1], t[2], t[3], t[4]));
            for (int i = 0; i < t.length; i++) {
                total[i] = total[i].add(t[i]);
            }
        });
        return new TableData("Department summary", header(p),
                List.of(Column.text("Department"), Column.number("Employees"), Column.amount("Gross"),
                        Column.amount("Deductions"), Column.amount("Net"), Column.amount("Employer contributions")),
                rows, List.of("Total", total[0].intValue(), total[1], total[2], total[3], total[4]), false,
                CurrentUser.get().username(), clock.instant());
    }

    private List<String> header(PayrollPeriod p) {
        return List.of(org.company(p.getCompanyId()).getLegalName(),
                "Period: " + label(p) + " (" + p.getStartDate() + " to " + p.getEndDate() + ")",
                "Status: " + p.getStatus());
    }

    // ---------------------------------------------------------------- helpers

    static String label(PayrollPeriod p) {
        return Month.of(p.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + p.getYear();
    }

    private static String key(PayrollPeriod p) {
        return String.format("%d-%02d", p.getYear(), p.getMonth());
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "—" : s;
    }
}
