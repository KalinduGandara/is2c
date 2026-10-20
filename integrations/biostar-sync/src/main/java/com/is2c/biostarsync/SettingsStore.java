package com.is2c.biostarsync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Persists {@link Settings} and {@link State} as JSON files in the data directory. Passwords are stored in plain text;
 * the files are readable by the owner only (on POSIX systems).
 */
@Component
public class SettingsStore {

    /** Sync progress, not edited by the user. */
    public static class State {
        /** Time of the latest punch the automatic sync has read; null before it has read any. */
        public Instant lastEventTime;
        /** Most recent runs first. */
        public List<SyncReport> history = new ArrayList<>();
    }

    private static final int HISTORY = 20;

    private final JsonMapper json;
    private final Path settingsFile;
    private final Path stateFile;

    public SettingsStore(JsonMapper json, @Value("${sync.data-dir}") Path dataDir) {
        this.json = json;
        this.settingsFile = dataDir.resolve("settings.json");
        this.stateFile = dataDir.resolve("state.json");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized Settings settings() {
        return read(settingsFile, Settings.class, new Settings());
    }

    public synchronized void save(Settings s) {
        write(settingsFile, s);
    }

    public synchronized State state() {
        return read(stateFile, State.class, new State());
    }

    public synchronized void saveWatermark(Instant lastEventTime) {
        State s = state();
        s.lastEventTime = lastEventTime;
        write(stateFile, s);
    }

    public synchronized void resetWatermark() {
        State s = state();
        s.lastEventTime = null;
        write(stateFile, s);
    }

    public synchronized void addReport(SyncReport report) {
        State s = state();
        s.history.addFirst(report);
        while (s.history.size() > HISTORY) {
            s.history.removeLast();
        }
        write(stateFile, s);
    }

    private <T> T read(Path file, Class<T> type, T fallback) {
        if (!Files.exists(file)) {
            return fallback;
        }
        return json.readValue(file.toFile(), type);
    }

    /** Writes to a temp file then moves it into place, so a crash never leaves a half-written file. */
    private void write(Path file, Object value) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.deleteIfExists(tmp);
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            }
            json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
