package com.is2c.hrm.employee.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeDocument;
import com.is2c.hrm.employee.domain.EmployeeDocumentRepository;
import com.is2c.hrm.platform.storage.ContentSniffer;
import com.is2c.hrm.platform.storage.FileStorage;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-020 employee documents. */
@Service
public class DocumentService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final EmployeeDocumentRepository documents;
    private final EmployeeService employees;
    private final FileStorage storage;
    private final AuditService audit;
    private final Clock clock;
    private final List<String> documentTypes;

    public DocumentService(EmployeeDocumentRepository documents, EmployeeService employees, FileStorage storage,
            AuditService audit, Clock clock,
            @Value("${hrm.documents.types:NIC,Birth Certificate,Contract,Certificate,Other}") List<String> documentTypes) {
        this.documents = documents;
        this.employees = employees;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
        this.documentTypes = List.copyOf(documentTypes);
    }

    public record Content(EmployeeDocument document, InputStream stream) {
    }

    public List<String> documentTypes() {
        return documentTypes;
    }

    @Transactional
    public EmployeeDocument upload(UUID employeeId, String docType, String title, LocalDate expiryDate,
            MultipartFile file) {
        Employee e = employees.get(employeeId);
        byte[] bytes = validated(docType, title, file);
        String key;
        try {
            key = storage.store(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store document", ex);
        }
        String contentType = ContentSniffer.detect(bytes).orElseThrow();
        EmployeeDocument doc = documents.save(new EmployeeDocument(employeeId, docType, title.trim(),
                safeName(file.getOriginalFilename()), contentType, bytes.length, key, expiryDate,
                CurrentUser.get().id(), clock.instant()));
        audit.record(AuditEntry.of("UPLOAD", "EMPLOYEE_DOCUMENT", doc.getId()).company(e.getCompanyId())
                .after(java.util.Map.of("employeeId", employeeId, "docType", docType, "title", title,
                        "fileName", doc.getFileName(), "size", bytes.length)));
        return doc;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDocument> list(UUID employeeId) {
        employees.get(employeeId);
        return documents.findByEmployeeIdOrderByUploadedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public Content open(UUID employeeId, UUID documentId) {
        employees.get(employeeId);
        EmployeeDocument doc = find(employeeId, documentId);
        try {
            return new Content(doc, storage.open(doc.getStorageKey()));
        } catch (IOException ex) {
            throw new NotFoundException("Document content", documentId);
        }
    }

    /** Self-service variant: no scope check beyond ownership (FR-020, FR-100). */
    @Transactional(readOnly = true)
    public List<EmployeeDocument> listOwn(UUID employeeId) {
        return documents.findByEmployeeIdOrderByUploadedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public Content openOwn(UUID employeeId, UUID documentId) {
        EmployeeDocument doc = find(employeeId, documentId);
        try {
            return new Content(doc, storage.open(doc.getStorageKey()));
        } catch (IOException ex) {
            throw new NotFoundException("Document content", documentId);
        }
    }

    @Transactional
    public void delete(UUID employeeId, UUID documentId) {
        Employee e = employees.get(employeeId);
        EmployeeDocument doc = find(employeeId, documentId);
        doc.markDeleted(clock.instant());
        audit.record(AuditEntry.of("DELETE", "EMPLOYEE_DOCUMENT", documentId).company(e.getCompanyId()));
    }

    private EmployeeDocument find(UUID employeeId, UUID documentId) {
        return documents.findById(documentId).filter(d -> d.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new NotFoundException("Document", documentId));
    }

    private byte[] validated(String docType, String title, MultipartFile file) {
        List<FieldError> errors = new java.util.ArrayList<>();
        if (docType == null || !documentTypes.contains(docType)) {
            errors.add(new FieldError("docType", "must be one of " + documentTypes));
        }
        if (title == null || title.isBlank()) {
            errors.add(new FieldError("title", "must not be blank"));
        }
        byte[] bytes = new byte[0];
        if (file == null || file.isEmpty()) {
            errors.add(new FieldError("file", "a file is required"));
        } else if (file.getSize() > MAX_BYTES) {
            errors.add(new FieldError("file", "file exceeds the maximum size of 10 MB"));
        } else {
            try {
                bytes = file.getBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            if (ContentSniffer.detect(bytes).isEmpty()) {
                errors.add(new FieldError("file", "only PDF, JPEG, PNG and DOCX files are allowed"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        return bytes;
    }

    static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
