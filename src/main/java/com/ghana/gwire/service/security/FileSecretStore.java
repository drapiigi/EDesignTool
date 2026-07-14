package com.ghana.gwire.service.security;

import com.ghana.gwire.service.persist.GwireHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * File-backed {@link SecretStore}: {@code ~/.gwire/secrets.properties} with mode {@code 0600}.
 *
 * <p>Threat model is world-readable home files / other local users — not same-UID attackers.
 * No AES/KDF required for Phase 10/15. Env vars always win over this store in {@code AiSettings}.
 */
public final class FileSecretStore implements SecretStore {

    private static final Logger log = LoggerFactory.getLogger(FileSecretStore.class);

    private final Path secretsPath;

    public FileSecretStore() {
        this(GwireHome.secretsFile());
    }

    public FileSecretStore(Path secretsPath) {
        this.secretsPath = secretsPath;
    }

    @Override
    public Path path() {
        return secretsPath;
    }

    @Override
    public Optional<String> getApiKey() {
        Properties p = loadProps();
        String v = p.getProperty(KEY_API);
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(v.trim());
    }

    @Override
    public void setApiKey(String apiKey) throws IOException {
        Properties p = loadProps();
        if (apiKey == null || apiKey.isBlank()) {
            p.remove(KEY_API);
        } else {
            p.setProperty(KEY_API, apiKey.trim());
        }
        saveProps(p);
    }

    @Override
    public void migrateFromAiPropertiesIfNeeded() {
        Path aiProps = GwireHome.aiProperties();
        if (!Files.isRegularFile(aiProps)) {
            return;
        }
        if (getApiKey().isPresent()) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(aiProps)) {
            props.load(in);
        } catch (IOException e) {
            log.warn("Could not read AI properties for key migration: {}", e.getMessage());
            return;
        }
        String key = props.getProperty("apiKey");
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            setApiKey(key);
            props.remove("apiKey");
            try (OutputStream out = Files.newOutputStream(aiProps)) {
                props.store(out, "GhanaWire AI settings (apiKey moved to secrets.properties)");
            }
            log.info("Migrated API key from ai.properties into local secrets store");
        } catch (IOException e) {
            log.warn("API key migration failed: {}", e.getMessage());
        }
    }

    private Properties loadProps() {
        Properties p = new Properties();
        if (!Files.isRegularFile(secretsPath)) {
            return p;
        }
        try (InputStream in = Files.newInputStream(secretsPath)) {
            p.load(in);
        } catch (IOException e) {
            log.warn("Could not read secrets store: {}", e.getMessage());
        }
        return p;
    }

    private void saveProps(Properties p) throws IOException {
        Path parent = secretsPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(secretsPath)) {
            p.store(out, "GhanaWire local secrets store — mode 0600; do not share");
        }
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(secretsPath, perms);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Could not set 0600 on secrets file (non-POSIX FS?): {}", e.getMessage());
        }
    }
}
