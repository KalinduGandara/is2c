package com.is2c.hrm.platform.storage;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.common.error.ValidationException;

/** Reads an uploaded document, enforcing size and sniffed content type (FR-020, FR-063). */
public final class Uploads {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    public record Checked(byte[] bytes, String contentType, String fileName) {
    }

    private Uploads() {
    }

    public static Checked check(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(field, "a file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException(field, "file exceeds the maximum size of 10 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String type = ContentSniffer.detect(bytes)
                .orElseThrow(() -> new ValidationException(field, "only PDF, JPEG, PNG and DOCX files are allowed"));
        return new Checked(bytes, type, safeName(file.getOriginalFilename()));
    }

    static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
