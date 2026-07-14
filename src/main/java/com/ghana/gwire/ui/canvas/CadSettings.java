package com.ghana.gwire.ui.canvas;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Session CAD toggles: ortho, endpoint OSNAP, layer visibility.
 */
public final class CadSettings {

    private final BooleanProperty ortho = new SimpleBooleanProperty(false);
    private final BooleanProperty endpointSnap = new SimpleBooleanProperty(true);
    private final BooleanProperty showArchitecture = new SimpleBooleanProperty(true);
    private final BooleanProperty showElectrical = new SimpleBooleanProperty(true);

    public BooleanProperty orthoProperty() {
        return ortho;
    }

    public boolean isOrtho() {
        return ortho.get();
    }

    public void setOrtho(boolean v) {
        ortho.set(v);
    }

    public void toggleOrtho() {
        ortho.set(!ortho.get());
    }

    public BooleanProperty endpointSnapProperty() {
        return endpointSnap;
    }

    public boolean isEndpointSnap() {
        return endpointSnap.get();
    }

    public void setEndpointSnap(boolean v) {
        endpointSnap.set(v);
    }

    public BooleanProperty showArchitectureProperty() {
        return showArchitecture;
    }

    public boolean isShowArchitecture() {
        return showArchitecture.get();
    }

    public void setShowArchitecture(boolean v) {
        showArchitecture.set(v);
    }

    public BooleanProperty showElectricalProperty() {
        return showElectrical;
    }

    public boolean isShowElectrical() {
        return showElectrical.get();
    }

    public void setShowElectrical(boolean v) {
        showElectrical.set(v);
    }
}
