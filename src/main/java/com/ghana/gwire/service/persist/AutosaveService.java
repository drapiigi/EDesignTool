package com.ghana.gwire.service.persist;

import com.ghana.gwire.domain.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dirty-session autosave under {@code ~/.gwire/autosave/{projectId}.gwire}.
 *
 * <p>Clean-exit marker at {@code ~/.gwire/clean-exit}: written on graceful quit;
 * recovery is offered only when the previous session did not write the marker
 * and at least one autosave file exists.
 */
public final class AutosaveService {

    private static final Logger log = LoggerFactory.getLogger(AutosaveService.class);

    private final ProjectStore store;

    public AutosaveService() {
        this(new ProjectStore());
    }

    public AutosaveService(ProjectStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public Path autosavePath(String projectId) {
        String id = projectId == null || projectId.isBlank() ? "unknown" : projectId;
        // Keep filename safe
        id = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        return GwireHome.autosaveDir().resolve(id + ".gwire");
    }

    /** Serializes the full multi-storey project to the autosave location. */
    public void autosave(Project project) throws IOException {
        Objects.requireNonNull(project, "project");
        Files.createDirectories(GwireHome.autosaveDir());
        Path target = autosavePath(project.id());
        // Autosave skips backup rotation (ephemeral)
        store.save(project, target, false);
        log.debug("Autosaved project {} → {}", project.id(), target);
    }

    public void deleteAutosave(String projectId) {
        try {
            Files.deleteIfExists(autosavePath(projectId));
        } catch (IOException e) {
            log.warn("Could not delete autosave for {}: {}", projectId, e.getMessage());
        }
    }

    public void writeCleanExitMarker() {
        try {
            Files.createDirectories(GwireHome.root());
            Files.writeString(
                    GwireHome.cleanExitMarker(),
                    Instant.now().toString(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            log.warn("Could not write clean-exit marker: {}", e.getMessage());
        }
    }

    /**
     * @return true if previous session likely crashed (no clean-exit marker).
     */
    public boolean previousSessionUnclean() {
        return !Files.isRegularFile(GwireHome.cleanExitMarker());
    }

    /** Call after recovery check so a crash mid-session is detectable next launch. */
    public void clearCleanExitMarker() {
        try {
            Files.deleteIfExists(GwireHome.cleanExitMarker());
        } catch (IOException e) {
            log.debug("Could not clear clean-exit marker: {}", e.getMessage());
        }
    }

    public List<Path> listAutosaves() {
        Path dir = GwireHome.autosaveDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.gwire")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            log.warn("Could not list autosaves: {}", e.getMessage());
        }
        out.sort(Comparator.comparing((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException ex) {
                return 0L;
            }
        }).reversed());
        return out;
    }

    /**
     * Newest autosave if previous session was unclean and at least one file exists.
     */
    public Optional<Path> recoveryCandidate() {
        if (!previousSessionUnclean()) {
            return Optional.empty();
        }
        List<Path> list = listAutosaves();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Project loadAutosave(Path path) throws IOException {
        return store.load(path);
    }
}
