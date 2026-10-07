package com.is2c.hrm.platform.storage;

import java.io.IOException;
import java.io.InputStream;

/** Binary content store (FR-020). Keys are random and never derived from user input. */
public interface FileStorage {

    /** Stores the content and returns its storage key. */
    String store(InputStream content) throws IOException;

    InputStream open(String key) throws IOException;
}
