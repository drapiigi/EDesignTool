package com.ghana.gwire.service.security;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Local secrets store for API keys and similar credentials.
 *
 * <p>P0/Phase 15 default backend is a file under {@code ~/.gwire/} with mode {@code 0600}
 * ({@link FileSecretStore}). An OS keyring backend is stubbed as {@link KeyringSecretStore}
 * (currently delegates to file).
 *
 * <p>Use {@link #local()} / {@link #local(Path)} for the file-backed store.
 */
public interface SecretStore {

    String KEY_API = "apiKey";

    Optional<String> getApiKey();

    void setApiKey(String apiKey) throws IOException;

    /**
     * One-time migrate {@code apiKey} from {@code ~/.gwire/ai.properties} into secrets,
     * then strip the key from properties (non-secret keys remain).
     */
    void migrateFromAiPropertiesIfNeeded();

    /** Path of the backing store (file path for file-backed implementations). */
    Path path();

    /** Default file-backed store at {@code ~/.gwire/secrets.properties}. */
    static SecretStore local() {
        return new FileSecretStore();
    }

    /** File-backed store at an explicit path (tests). */
    static SecretStore local(Path secretsPath) {
        return new FileSecretStore(secretsPath);
    }
}
