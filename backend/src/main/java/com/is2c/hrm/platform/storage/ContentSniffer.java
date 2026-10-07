package com.is2c.hrm.platform.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Detects allowed document types from file content, not from client-supplied names (FR-020). */
public final class ContentSniffer {

    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private ContentSniffer() {
    }

    public static Optional<String> detect(byte[] bytes) {
        if (startsWith(bytes, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (startsWith(bytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 'P', 'K', 0x03, 0x04) && isDocx(bytes)) {
            return Optional.of(DOCX);
        }
        return Optional.empty();
    }

    private static boolean isDocx(byte[] bytes) {
        boolean contentTypes = false;
        boolean word = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int guard = 0;
            while ((entry = zip.getNextEntry()) != null && guard++ < 10_000) {
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                word |= entry.getName().startsWith("word/");
            }
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
        return contentTypes && word;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
