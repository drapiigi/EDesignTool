package com.ghana.gwire.ui.panels;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Browse Ghana starter component catalogue; select an item to place on the plan.
 */
public class SymbolLibraryPanel {

    private final VBox root;
    private final ListView<ElectricalComponent> listView;
    private final ObservableList<ElectricalComponent> master;
    private final FilteredList<ElectricalComponent> filtered;
    private final ComboBox<String> categoryCombo;
    private final TextField searchField;
    private final Label detailLabel;

    private Consumer<ElectricalComponent> placeListener = c -> {
    };
    private Consumer<String> statusSink = s -> {
    };

    public SymbolLibraryPanel() {
        Label title = new Label("Symbol library");
        title.getStyleClass().add("panel-title");

        ComponentLibraryService lib = safeLibrary();
        master = FXCollections.observableArrayList(
                lib == null ? java.util.List.of() : lib.listAll()
        );
        filtered = new FilteredList<>(master, c -> true);

        categoryCombo = new ComboBox<>();
        categoryCombo.getItems().add("All categories");
        for (ComponentCategory cat : ComponentCategory.values()) {
            categoryCombo.getItems().add(cat.name());
        }
        categoryCombo.getSelectionModel().selectFirst();
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        categoryCombo.setOnAction(e -> applyFilter());

        searchField = new TextField();
        searchField.setPromptText("Search name, size, id…");
        searchField.textProperty().addListener((o, a, b) -> applyFilter());

        listView = new ListView<>(filtered);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ElectricalComponent item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.name() + "  ·  " + item.standardSize()
                            + "  ·  GHS " + String.format("%.2f", item.unitCostGhs()));
                }
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showDetail(b));
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                placeSelected();
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        detailLabel = new Label(lib == null
                ? "Component library unavailable."
                : lib.count() + " catalogue items · double-click or Place to insert");
        detailLabel.getStyleClass().add("panel-body");
        detailLabel.setWrapText(true);

        javafx.scene.control.Button placeBtn = new javafx.scene.control.Button("Place on plan");
        placeBtn.getStyleClass().add("tool-action");
        placeBtn.setMaxWidth(Double.MAX_VALUE);
        placeBtn.setOnAction(e -> placeSelected());

        Label hint = new Label("L.I. 2008 / BS 1363 catalogue · costs editable later");
        hint.getStyleClass().add("panel-footer");
        hint.setWrapText(true);

        VBox content = new VBox(8, title, categoryCombo, searchField, listView, detailLabel, placeBtn, hint);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");
        VBox.setVgrow(listView, Priority.ALWAYS);

        root = new VBox(content);
        root.getStyleClass().addAll("side-panel", "symbol-library-panel");
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    public VBox getRoot() {
        return root;
    }

    public void setPlaceListener(Consumer<ElectricalComponent> placeListener) {
        this.placeListener = placeListener == null ? c -> {
        } : placeListener;
    }

    public void setStatusSink(Consumer<String> statusSink) {
        this.statusSink = statusSink == null ? s -> {
        } : statusSink;
    }

    public void reload() {
        ComponentLibraryService lib = safeLibrary();
        master.setAll(lib == null ? java.util.List.of() : lib.listAll());
        applyFilter();
    }

    private void placeSelected() {
        ElectricalComponent c = listView.getSelectionModel().getSelectedItem();
        if (c == null) {
            statusSink.accept("Select a component first");
            return;
        }
        placeListener.accept(c);
        statusSink.accept("Place mode: click canvas to insert " + c.name());
    }

    private void showDetail(ElectricalComponent c) {
        if (c == null) {
            detailLabel.setText("Select a component to place.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(c.name()).append("\n");
        sb.append(c.category()).append(" · ").append(nullToDash(c.standardSize())).append("\n");
        sb.append("Symbol: ").append(c.symbolKey()).append("\n");
        sb.append(String.format("Cost: GHS %.2f / %s%n", c.unitCostGhs(), c.unit()));
        if (c.currentRatingA() != null) {
            sb.append(String.format("Rating: %.0f A%n", c.currentRatingA()));
        }
        if (c.crossSectionMm2() != null) {
            sb.append(String.format("CSA: %.1f mm²%n", c.crossSectionMm2()));
        }
        if (c.ghanaReference() != null) {
            sb.append(c.ghanaReference());
        }
        detailLabel.setText(sb.toString().trim());
    }

    private void applyFilter() {
        String cat = categoryCombo.getSelectionModel().getSelectedItem();
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.setPredicate(c -> {
            if (c == null) {
                return false;
            }
            if (cat != null && !"All categories".equals(cat) && !c.category().name().equals(cat)) {
                return false;
            }
            if (q.isEmpty()) {
                return true;
            }
            return contains(c.name(), q)
                    || contains(c.id(), q)
                    || contains(c.standardSize(), q)
                    || contains(c.symbolKey(), q)
                    || contains(c.description(), q);
        });
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static ComponentLibraryService safeLibrary() {
        try {
            return LibraryBootstrap.get();
        } catch (Exception e) {
            try {
                return LibraryBootstrap.initialize();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
