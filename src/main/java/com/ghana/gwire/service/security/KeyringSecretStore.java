package com.ghana.gwire.service.security;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Future OS keyring / credential-manager backend.
 *
 * <p><b>Not available yet</b> on Linux/Windows/macOS in this build. All calls
 * currently delegate to a {@link FileSecretStore} (0600 secrets file). When a
 * real keyring library is wired, this class should prefer the OS store and
 * fall back to file only on failure.
 */
public final class KeyringSecretStore implements SecretStore {

    private final SecretStore delegate;

    public KeyringSecretStore() {
        this(new FileSecretStore());
    }

    public KeyringSecretStore(Path secretsPath) {
        this(new FileSecretStore(secretsPath));
    }

    /**
     * @param delegate typically a {@link FileSecretStore} used until OS keyring is integrated
     */
    public KeyringSecretStore(SecretStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<String> getApiKey() {
        return delegate.getApiKey();
    }

    @Override
    public void setApiKey(String apiKey) throws IOException {
        delegate.setApiKey(apiKey);
    }

    @Override
    public void migrateFromAiPropertiesIfNeeded() {
        delegate.migrateFromAiPropertiesIfNeeded();
    }

    @Override
    public Path path() {
        return delegate.path();
    }

    /** Whether a real OS keyring is active (always {@code false} until integrated). */
    public boolean isOsKeyringAvailable() {
        return false;
    }
}
