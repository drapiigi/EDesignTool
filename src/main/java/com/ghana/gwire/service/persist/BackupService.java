package com.ghana.gwire.service.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Rolling backups beside a project file: {@code path.bak2} ← {@code path.bak} ← {@code path}.
 */
public final class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private BackupService() {
    }

    /**
     * Rotates backups before an in-place replace of {@code path}.
     * No-op if {@code path} does not exist yet (first save).
     */
    public static void rotate(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            return;
        }
        Path bak = path.resolveSibling(path.getFileName().toString() + ".bak");
        Path bak2 = path.resolveSibling(path.getFileName().toString() + ".bak2");
        if (Files.isRegularFile(bak)) {
            Files.copy(bak, bak2, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Rotated backup for {}", path);
    }
}
