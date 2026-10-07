package com.is2c.hrm.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.is2c.hrm.common.config.HrmProperties;

/** Stores files under {@code hrm.storage.path} (outside the web root), sharded by key prefix. */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");

    private final Path root;

    public LocalFileStorage(HrmProperties props) {
        this.root = props.storage().path().toAbsolutePath().normalize();
    }

    @Override
    public String store(InputStream content) throws IOException {
        String key = UUID.randomUUID().toString().replace("-", "");
        Path target = pathFor(key);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "upload", ".tmp");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(pathFor(key));
    }

    private Path pathFor(String key) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return root.resolve(key.substring(0, 2)).resolve(key);
    }
}
