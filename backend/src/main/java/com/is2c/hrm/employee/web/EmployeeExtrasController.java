package com.is2c.hrm.employee.web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.employee.domain.CustomFieldDef;
import com.is2c.hrm.employee.domain.EmployeeDocument;
import com.is2c.hrm.employee.service.CustomFieldService;
import com.is2c.hrm.employee.service.DocumentService;

@RestController
@RequestMapping("/api/v1")
public class EmployeeExtrasController {

    private final CustomFieldService customFields;
    private final DocumentService documents;

    public EmployeeExtrasController(CustomFieldService customFields, DocumentService documents) {
        this.customFields = customFields;
        this.documents = documents;
    }

    public record CustomFieldDto(UUID id, UUID companyId, String key, String label, CustomFieldDef.FieldType type,
            List<String> options, boolean required, boolean active, int displayOrder, Long version) {
        static CustomFieldDto of(CustomFieldDef f) {
            return new CustomFieldDto(f.getId(), f.getCompanyId(), f.getKey(), f.getLabel(), f.getType(), f.options(),
                    f.isRequired(), f.isActive(), f.getDisplayOrder(), f.getVersion());
        }
    }

    public record DocumentDto(UUID id, UUID employeeId, String docType, String title, String fileName,
            String contentType, long sizeBytes, LocalDate expiryDate, Instant uploadedAt) {
        public static DocumentDto of(EmployeeDocument d) {
            return new DocumentDto(d.getId(), d.getEmployeeId(), d.getDocType(), d.getTitle(), d.getFileName(),
                    d.getContentType(), d.getSizeBytes(), d.getExpiryDate(), d.getUploadedAt());
        }
    }

    // Custom fields (FR-019)

    @GetMapping("/companies/{companyId}/custom-fields")
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    public List<CustomFieldDto> listFields(@PathVariable UUID companyId) {
        return customFields.list(companyId).stream().map(CustomFieldDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/custom-fields")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE:CONFIGURE')")
    public CustomFieldDto createField(@PathVariable UUID companyId, @RequestBody CustomFieldService.FieldData body) {
        return CustomFieldDto.of(customFields.create(companyId, body));
    }

    @PutMapping("/custom-fields/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE:CONFIGURE')")
    public CustomFieldDto updateField(@PathVariable UUID id, @RequestBody CustomFieldService.FieldData body) {
        return CustomFieldDto.of(customFields.update(id, body));
    }

    // Documents (FR-020)

    @GetMapping("/document-types")
    public List<String> documentTypes() {
        return documents.documentTypes();
    }

    @GetMapping("/employees/{id}/documents")
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    public List<DocumentDto> listDocuments(@PathVariable UUID id) {
        return documents.list(id).stream().map(DocumentDto::of).toList();
    }

    @PostMapping(value = "/employees/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE:EDIT')")
    public DocumentDto upload(@PathVariable UUID id, @RequestParam String docType, @RequestParam String title,
            @RequestParam(required = false) LocalDate expiryDate, @RequestPart("file") MultipartFile file) {
        return DocumentDto.of(documents.upload(id, docType, title, expiryDate, file));
    }

    @GetMapping("/employees/{id}/documents/{documentId}/content")
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id, @PathVariable UUID documentId)
            throws IOException {
        return asAttachment(documents.open(id, documentId));
    }

    @DeleteMapping("/employees/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLOYEE:DELETE')")
    public void deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        documents.delete(id, documentId);
    }

    public static ResponseEntity<InputStreamResource> asAttachment(DocumentService.Content content) {
        EmployeeDocument d = content.document();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.getFileName()).build().toString())
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .contentLength(d.getSizeBytes())
                .body(new InputStreamResource(content.stream()));
    }
}
