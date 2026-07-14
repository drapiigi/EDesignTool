package com.ghana.gwire.service.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Write-then-replace file updates so a mid-write crash leaves the previous primary intact.
 *
 * <ol>
 *   <li>Write full payload to {@code path.tmp}</li>
 *   <li>{@code FileChannel.force(true)} best-effort</li>
 *   <li>{@code ATOMIC_MOVE} replace, with non-atomic fallback</li>
 * </ol>
 */
public final class AtomicFileWriter {

    private static final Logger log = LoggerFactory.getLogger(AtomicFileWriter.class);

    private AtomicFileWriter() {
    }

    /**
     * Writes {@code bytes} to {@code path} via a sibling temp file.
     * Does not create backups — call {@link BackupService#rotate(Path)} first if needed.
     */
    public static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(bytes, "bytes");

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buf = ByteBuffer.wrap(bytes);
                while (buf.hasRemaining()) {
                    channel.write(buf);
                }
                try {
                    channel.force(true);
                } catch (IOException forceEx) {
                    log.debug("fsync not fully supported for {}: {}", tmp, forceEx.getMessage());
                }
            }

            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                log.debug("ATOMIC_MOVE unsupported for {}; using REPLACE_EXISTING", path);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }
}
