package com.ghana.gwire.ui.panels;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.ui.symbols.ComponentDragFormats;
import com.ghana.gwire.ui.symbols.SymbolRenderer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

/**
 * Browse Ghana starter component catalogue; drag items onto the floor plan to place them.
 */
public class SymbolLibraryPanel {

    private final VBox root;
    private final ListView<ElectricalComponent> listView;
    private final ObservableList<ElectricalComponent> master;
    private final FilteredList<ElectricalComponent> filtered;
    private final ComboBox<String> categoryCombo;
    private final TextField searchField;
    private final Label detailLabel;

    private Consumer<String> statusSink = s -> {
    };

    public SymbolLibraryPanel() {
        Label title = new Label("Symbols");
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
        listView.setFixedCellSize(52);
        listView.setCellFactory(lv -> new ListCell<>() {
            private final Canvas iconCanvas = new Canvas(40, 40);
            private final Label nameLabel = new Label();
            private final Label metaLabel = new Label();
            private final VBox textBox = new VBox(2, nameLabel, metaLabel);
            private final HBox row = new HBox(10, iconCanvas, textBox);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                nameLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
                nameLabel.setStyle("-fx-text-fill: #e5e9f0;");
                metaLabel.setFont(Font.font("Segoe UI", 10));
                metaLabel.setStyle("-fx-text-fill: #8f9bb0;");
                HBox.setHgrow(textBox, Priority.ALWAYS);
                setOnDragDetected(e -> {
                    ElectricalComponent item = getItem();
                    if (item == null || isEmpty()) {
                        return;
                    }
                    Dragboard db = startDragAndDrop(TransferMode.COPY);
                    ClipboardContent content = new ClipboardContent();
                    content.put(ComponentDragFormats.COMPONENT_ID, item.id());
                    content.putString(item.id());
                    db.setContent(content);
                    try {
                        db.setDragView(snapshot(null, null), e.getX(), e.getY());
                    } catch (Exception ignored) {
                        // drag view is cosmetic
                    }
                    statusSink.accept("Drop on canvas to place: " + item.name());
                    e.consume();
                });
            }

            @Override
            protected void updateItem(ElectricalComponent item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    paintLibraryIcon(iconCanvas, item.symbolKey());
                    nameLabel.setText(item.name());
                    metaLabel.setText(nullToDash(item.standardSize())
                            + "  ·  GHS " + String.format("%.2f", item.unitCostGhs()));
                    setGraphic(row);
                }
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showDetail(b));
        VBox.setVgrow(listView, Priority.ALWAYS);

        detailLabel = new Label(lib == null
                ? "Component library unavailable."
                : lib.count() + " catalogue items · drag onto the canvas to place");
        detailLabel.getStyleClass().add("panel-body");
        detailLabel.setWrapText(true);

        Label hint = new Label(
                "Drag a component onto the plan.\n"
                        + "On the canvas: drag a placed symbol to move it.\n"
                        + "L.I. 2008 / BS 1363 catalogue · costs editable later"
        );
        hint.getStyleClass().add("panel-footer");
        hint.setWrapText(true);

        VBox content = new VBox(8, title, categoryCombo, searchField, listView, detailLabel, hint);
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

    public void setStatusSink(Consumer<String> statusSink) {
        this.statusSink = statusSink == null ? s -> {
        } : statusSink;
    }

    /** @deprecated Placement is drag-and-drop; kept for menu reload only. */
    public void setPlaceListener(Consumer<ElectricalComponent> placeListener) {
        // no-op: drag-and-drop is the primary placement path
    }

    public void reload() {
        ComponentLibraryService lib = safeLibrary();
        master.setAll(lib == null ? java.util.List.of() : lib.listAll());
        applyFilter();
        detailLabel.setText(lib == null
                ? "Component library unavailable."
                : lib.count() + " catalogue items · drag onto the canvas to place");
    }

    private void showDetail(ElectricalComponent c) {
        if (c == null) {
            detailLabel.setText("Select a component, then drag it onto the plan.");
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
        sb.append("\n\nDrag onto the canvas to place.");
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

    /** Paint a CAD-quality symbol preview for the library list. */
    private static void paintLibraryIcon(Canvas canvas, String symbolKey) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setImageSmoothing(true);
        g.setFill(Color.web("#000000"));
        g.fillRoundRect(0, 0, w, h, 4, 4);
        g.setStroke(Color.web("#404040"));
        g.setLineWidth(0.8);
        g.strokeRoundRect(0.5, 0.5, w - 1, h - 1, 4, 4);
        // Fixed thumbnail size; plan symbols use world-scale via SymbolRenderer.screenSize
        SymbolRenderer.draw(g, symbolKey, w / 2, h / 2 - 1, 26, 0, false);
    }
}
