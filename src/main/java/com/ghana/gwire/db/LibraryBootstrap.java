package com.ghana.gwire.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application-level hook to initialise the embedded component library.
 * Call from {@code GWireApp.start} (or tests) once at startup.
 */
public final class LibraryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(LibraryBootstrap.class);

    private static ComponentLibraryService shared;

    private LibraryBootstrap() {
    }

    /**
     * Ensures the default file-backed library is ready (schema + seed if empty).
     * Safe to call multiple times; returns the shared service instance.
     */
    public static synchronized ComponentLibraryService initialize() {
        if (shared == null) {
            shared = new ComponentLibraryService();
            shared.ensureInitialized();
            log.info("Component library bootstrap complete ({} components)", shared.count());
        }
        return shared;
    }

    /** Shared instance after {@link #initialize()}, or null if not yet started. */
    public static synchronized ComponentLibraryService get() {
        return shared;
    }

    /** Test/support: replace shared instance (does not close previous). */
    public static synchronized void setForTesting(ComponentLibraryService service) {
        shared = service;
    }

    public static synchronized void shutdown() {
        if (shared != null) {
            shared.close();
            shared = null;
        }
    }
}
