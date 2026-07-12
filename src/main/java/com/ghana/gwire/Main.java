package com.ghana.gwire;

/**
 * Non-JavaFX entry point used by the shaded jar / jpackage.
 * Delegates to {@link GWireApp} so JavaFX can bootstrap correctly.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        GWireApp.main(args);
    }
}
