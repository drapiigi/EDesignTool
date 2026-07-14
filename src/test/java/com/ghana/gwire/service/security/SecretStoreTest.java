package com.ghana.gwire.service.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretStoreTest {

    @TempDir
    Path temp;

    @Test
    void roundTripApiKeyAndPermissions() throws Exception {
        Path secrets = temp.resolve("secrets.properties");
        SecretStore store = SecretStore.local(secrets);
        store.setApiKey("sk-secret-test-key");
        assertEquals("sk-secret-test-key", store.getApiKey().orElseThrow());
        assertTrue(Files.isRegularFile(secrets));
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(secrets);
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
            assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE));
            assertTrue(!perms.contains(PosixFilePermission.GROUP_READ));
            assertTrue(!perms.contains(PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX
        }
    }

    @Test
    void fileSecretStoreDirect() throws Exception {
        Path secrets = temp.resolve("file-secrets.properties");
        FileSecretStore store = new FileSecretStore(secrets);
        store.setApiKey("abc");
        assertEquals("abc", store.getApiKey().orElseThrow());
    }

    @Test
    void keyringStubDelegatesToFile() throws Exception {
        Path secrets = temp.resolve("keyring-secrets.properties");
        KeyringSecretStore store = new KeyringSecretStore(secrets);
        assertFalse(store.isOsKeyringAvailable());
        store.setApiKey("via-keyring-stub");
        assertEquals("via-keyring-stub", store.getApiKey().orElseThrow());
        assertTrue(Files.isRegularFile(secrets));
    }
}
