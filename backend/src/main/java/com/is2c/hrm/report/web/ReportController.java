package com.is2c.hrm.report.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.platform.export.ExportFormat;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableExporter;
import com.is2c.hrm.report.service.DashboardService;
import com.is2c.hrm.report.service.ReportDefinition;
import com.is2c.hrm.report.service.ReportService;

/** FR-095 reports and FR-096 dashboard. */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reports;
    private final TableExporter exporter;
    private final DashboardService dashboard;

    public ReportController(ReportService reports, TableExporter exporter, DashboardService dashboard) {
        this.reports = reports;
        this.exporter = exporter;
        this.dashboard = dashboard;
    }

    public record CatalogEntry(String key, String title, List<ReportDefinition.Param> params) {
    }

    public record ReportResult(String key, TableData table) {
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('REPORT:VIEW')")
    public List<CatalogEntry> catalog() {
        return reports.catalog().stream().map(d -> new CatalogEntry(d.key(), d.title(), d.params())).toList();
    }

    @GetMapping("/reports/{key}")
    @PreAuthorize("hasAuthority('REPORT:VIEW')")
    public TableData run(@PathVariable String key, @RequestParam Map<String, String> params) {
        return reports.run(key, params);
    }

    @GetMapping("/reports/{key}/export")
    @PreAuthorize("hasAuthority('REPORT:VIEW') and hasAuthority('REPORT:EXPORT')")
    public ResponseEntity<byte[]> export(@PathVariable String key, @RequestParam Map<String, String> params) {
        Map<String, String> rest = new HashMap<>(params);
        ExportFormat format = ExportFormat.parse(rest.remove("format"));
        byte[] content = exporter.export(reports.run(key, rest), format);
        String name = key + "-" + java.time.LocalDate.now() + "." + format.extension();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType(format.contentType())).body(content);
    }

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard(@RequestParam UUID companyId) {
        return dashboard.dashboard(companyId);
    }
}
