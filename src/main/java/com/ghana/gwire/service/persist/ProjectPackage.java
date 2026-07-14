package com.ghana.gwire.service.persist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Format 1.2 package: ZIP with {@code project.json} + {@code media/*} for embedded backgrounds.
 */
public final class ProjectPackage {

    private static final Logger log = LoggerFactory.getLogger(ProjectPackage.class);
    public static final String PROJECT_JSON = "project.json";

    private ProjectPackage() {
    }

    public static void save(Project project, Path packagePath, ProjectStore store) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(packagePath, "packagePath");
        Objects.requireNonNull(store, "store");

        // Collect readable backgrounds → hash → bytes
        Map<String, byte[]> mediaByRef = new HashMap<>();
        for (BuildingStorey storey : project.storeys()) {
            embedBackground(storey.floorPlan(), mediaByRef);
        }
        embedBackground(project.floorPlan(), mediaByRef);

        ObjectNode root = store.buildRoot(project);
        byte[] json = store.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(root);

        Path parent = packagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BackupService.rotate(packagePath);

        Path tmp = packagePath.resolveSibling(packagePath.getFileName().toString() + ".tmp");
        boolean moved = false;
        try {
            try (OutputStream fos = Files.newOutputStream(tmp);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                ZipEntry je = new ZipEntry(PROJECT_JSON);
                zos.putNextEntry(je);
                zos.write(json);
                zos.closeEntry();
                for (Map.Entry<String, byte[]> e : mediaByRef.entrySet()) {
                    ZipEntry me = new ZipEntry("media/" + e.getKey());
                    zos.putNextEntry(me);
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            }
            try {
                Files.move(tmp, packagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, packagePath, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            log.info("Saved package '{}' with {} media file(s)", packagePath, mediaByRef.size());
        } finally {
            if (!moved) {
                Files.deleteIfExists(tmp);
            }
        }
    }

    private static void embedBackground(FloorPlan fp, Map<String, byte[]> mediaByRef) {
        BackgroundImage bg = fp.background();
        if (bg == null) {
            return;
        }
        Path src = Path.of(bg.sourcePath());
        if (!Files.isRegularFile(src)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(src);
            String hash = sha256Hex(bytes);
            String ext = extensionOf(src.getFileName().toString());
            String ref = hash.substring(0, Math.min(16, hash.length())) + ext;
            mediaByRef.putIfAbsent(ref, bytes);
            bg.setMediaHash(hash);
            bg.setEmbeddedRef(ref);
        } catch (IOException e) {
            log.warn("Could not embed background {}: {}", src, e.getMessage());
        }
    }

    public static Project load(Path packagePath, ProjectStore store) throws IOException {
        Objects.requireNonNull(packagePath, "packagePath");
        Map<String, byte[]> media = new HashMap<>();
        byte[] jsonBytes = null;
        try (InputStream in = Files.newInputStream(packagePath);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] data = zis.readAllBytes();
                if (PROJECT_JSON.equals(name) || name.endsWith("/" + PROJECT_JSON)) {
                    jsonBytes = data;
                } else if (name.startsWith("media/") || name.contains("/media/")) {
                    String ref = name.substring(name.lastIndexOf('/') + 1);
                    media.put(ref, data);
                }
            }
        }
        if (jsonBytes == null) {
            throw new IOException("Invalid .gwirez package: missing project.json");
        }
        JsonNode root = store.mapper().readTree(jsonBytes);
        Project project = store.loadFromTree(root);
        Files.createDirectories(GwireHome.cacheMediaDir());
        for (BuildingStorey s : project.storeys()) {
            resolveEmbedded(s.floorPlan(), media);
        }
        resolveEmbedded(project.floorPlan(), media);
        return project;
    }

    private static void resolveEmbedded(FloorPlan fp, Map<String, byte[]> media) throws IOException {
        BackgroundImage bg = fp.background();
        if (bg == null || bg.embeddedRef() == null) {
            return;
        }
        byte[] bytes = media.get(bg.embeddedRef());
        if (bytes == null) {
            return;
        }
        String fileName = bg.mediaHash() != null
                ? bg.mediaHash() + extensionOf(bg.embeddedRef())
                : bg.embeddedRef();
        Path cache = GwireHome.cacheMediaDir().resolve(fileName);
        if (!Files.isRegularFile(cache)) {
            Files.write(cache, bytes);
        }
        // Rebuild BackgroundImage with cache path (sourcePath is final)
        BackgroundImage resolved = new BackgroundImage(
                cache.toString(),
                bg.sourceLabel(),
                bg.mmPerPixel()
        );
        resolved.setOrigin(bg.originXMm(), bg.originYMm());
        resolved.setOpacity(bg.opacity());
        resolved.setEmbeddedRef(bg.embeddedRef());
        resolved.setMediaHash(bg.mediaHash());
        fp.setBackground(resolved);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".bin";
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 8) {
            return ".bin";
        }
        return ext;
    }
}
