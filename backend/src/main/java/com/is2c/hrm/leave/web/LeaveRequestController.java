package com.is2c.hrm.leave.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.leave.domain.LeaveRequest;
import com.is2c.hrm.leave.domain.LeaveTypeRepository;
import com.is2c.hrm.leave.service.LeaveCalendarService;
import com.is2c.hrm.leave.service.LeaveRequestService;

@RestController
@RequestMapping("/api/v1")
public class LeaveRequestController {

    private final LeaveRequestService requests;
    private final LeaveCalendarService calendar;
    private final EmployeeService employees;
    private final LeaveTypeRepository types;

    public LeaveRequestController(LeaveRequestService requests, LeaveCalendarService calendar,
            EmployeeService employees, LeaveTypeRepository types) {
        this.requests = requests;
        this.calendar = calendar;
        this.employees = employees;
        this.types = types;
    }

    public record RequestDto(UUID id, UUID employeeId, String employeeNumber, String employeeName, UUID leaveTypeId,
            String leaveType, LocalDate fromDate, LocalDate toDate, LeaveRequest.HalfDayPart halfDayPart,
            BigDecimal days, String reason, LeaveRequest.Status status, UUID approvalId, Instant appliedAt,
            Instant decidedAt, String decisionNote, List<AttachmentDto> attachments) {
    }

    public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes) {
    }

    public record CancelRequest(String reason) {
    }

    // HR / managers (FR-063 on behalf, FR-064, FR-065)

    @PostMapping(value = "/leave/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAVE:ADD')")
    public RequestDto applyFor(@RequestPart("data") LeaveRequestService.ApplyData data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return dto(requests.applyFor(data, files), true);
    }

    @PostMapping(value = "/leave/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAVE:ADD')")
    public RequestDto applyForJson(@RequestBody LeaveRequestService.ApplyData data) {
        return dto(requests.applyFor(data, List.of()), true);
    }

    @GetMapping("/leave/requests")
    @PreAuthorize("hasAuthority('LEAVE:VIEW')")
    public List<RequestDto> list(@RequestParam UUID companyId, @RequestParam(required = false) LeaveRequest.Status status,
            @RequestParam(required = false) UUID employeeId, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return dtos(calendar.requests(new LeaveCalendarService.RequestFilter(companyId, status, employeeId, from, to)));
    }

    @GetMapping("/leave/requests/{id}")
    @PreAuthorize("hasAuthority('LEAVE:VIEW')")
    public RequestDto get(@PathVariable UUID id) {
        return dto(requests.get(id), true);
    }

    @PostMapping("/leave/requests/{id}/cancel")
    @PreAuthorize("hasAuthority('LEAVE:EDIT')")
    public RequestDto cancel(@PathVariable UUID id, @RequestBody CancelRequest body) {
        return dto(requests.cancelByHr(id, body.reason()), true);
    }

    @GetMapping("/leave/requests/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('LEAVE:VIEW')")
    public ResponseEntity<InputStreamResource> attachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return file(requests.openAttachment(requests.get(id), attachmentId));
    }

    @GetMapping("/leave/calendar")
    public List<LeaveCalendarService.CalendarEntry> calendar(@RequestParam UUID companyId, @RequestParam LocalDate from,
            @RequestParam LocalDate to, @RequestParam(defaultValue = "false") boolean includePending) {
        return calendar.calendar(companyId, from, to, includePending);
    }

    // Self-service (FR-063, FR-064, FR-100)

    @PostMapping(value = "/me/leave/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto applyOwn(@RequestPart("data") LeaveRequestService.ApplyData data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return dto(requests.applyOwn(data, files), true);
    }

    @PostMapping(value = "/me/leave/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto applyOwnJson(@RequestBody LeaveRequestService.ApplyData data) {
        return dto(requests.applyOwn(data, List.of()), true);
    }

    @GetMapping("/me/leave/requests")
    public List<RequestDto> mine() {
        return dtos(requests.own());
    }

    @PostMapping("/me/leave/requests/{id}/cancel")
    public RequestDto cancelOwn(@PathVariable UUID id, @RequestBody(required = false) CancelRequest body) {
        return dto(requests.cancelOwn(id, body == null ? null : body.reason()), true);
    }

    @GetMapping("/me/leave/requests/{id}/attachments/{attachmentId}")
    public ResponseEntity<InputStreamResource> ownAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return file(requests.openAttachment(requests.getOwn(id), attachmentId));
    }

    private List<RequestDto> dtos(List<LeaveRequest> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(r -> ids.add(r.getEmployeeId()));
        Map<UUID, Employee> emps = employees.byIds(ids);
        return list.stream().map(r -> dto(r, emps.get(r.getEmployeeId()), false)).toList();
    }

    private RequestDto dto(LeaveRequest r, boolean withAttachments) {
        return dto(r, employees.byIds(List.of(r.getEmployeeId())).get(r.getEmployeeId()), withAttachments);
    }

    private RequestDto dto(LeaveRequest r, Employee e, boolean withAttachments) {
        String typeName = types.findById(r.getLeaveTypeId()).map(t -> t.getName()).orElse(null);
        List<AttachmentDto> files = withAttachments ? requests.attachmentsOf(r.getId()).stream()
                .map(a -> new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes())).toList()
                : List.of();
        return new RequestDto(r.getId(), r.getEmployeeId(), e == null ? null : e.getEmployeeNumber(),
                e == null ? null : e.fullName(), r.getLeaveTypeId(), typeName, r.getFromDate(), r.getToDate(),
                r.getHalfDayPart(), r.getDays(), r.getReason(), r.getStatus(), r.getApprovalId(), r.getAppliedAt(),
                r.getDecidedAt(), r.getDecisionNote(), files);
    }

    private static ResponseEntity<InputStreamResource> file(LeaveRequestService.Content c) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(c.attachment().getFileName()).build().toString())
                .contentType(MediaType.parseMediaType(c.attachment().getContentType()))
                .body(new InputStreamResource(c.stream()));
    }
}
