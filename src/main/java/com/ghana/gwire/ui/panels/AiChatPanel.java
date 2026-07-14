package com.ghana.gwire.ui.panels;

import com.ghana.gwire.ai.AiSettings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Dedicated AI co-pilot chat dock (always available, not a modal dialog).
 */
public class AiChatPanel {

    public enum Role {
        USER, ASSISTANT, SYSTEM
    }

    public record ChatMessage(Role role, String text, String time) {
    }

    private final VBox root;
    private final ListView<ChatMessage> messageList;
    private final TextField inputField;
    private final Label modeLabel;
    private final Button sendBtn;

    private Consumer<String> onSend = s -> {
    };
    private Runnable onGenerate = () -> {
    };
    private Runnable onRecalculate = () -> {
    };
    private Runnable onValidate = () -> {
    };

    public AiChatPanel() {
        Label title = new Label("AI co-pilot");
        title.getStyleClass().add("panel-title");

        modeLabel = new Label(modeHint());
        modeLabel.getStyleClass().add("panel-footer");
        modeLabel.setWrapText(true);

        messageList = new ListView<>();
        messageList.getStyleClass().add("ai-chat-list");
        messageList.setCellFactory(lv -> new ChatCell());
        messageList.setFocusTraversable(false);
        VBox.setVgrow(messageList, Priority.ALWAYS);

        // Quick actions
        Button gen = chip("Generate design", () -> onGenerate.run());
        Button calc = chip("Recalculate", () -> onRecalculate.run());
        Button validate = chip("Validate", () -> onValidate.run());
        Button help = chip("Help", () -> sendQuick("help"));
        FlowPane chips = new FlowPane(6, 6, gen, calc, validate, help);
        chips.getStyleClass().add("ai-chip-row");

        inputField = new TextField();
        inputField.setPromptText("Ask co-pilot… e.g. add socket in Living");
        inputField.getStyleClass().add("ai-chat-input");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                sendCurrent();
                e.consume();
            }
        });

        sendBtn = new Button("Send");
        sendBtn.getStyleClass().addAll("tool-action", "ai-send-btn");
        sendBtn.setDefaultButton(true);
        sendBtn.setOnAction(e -> sendCurrent());

        HBox inputRow = new HBox(8, inputField, sendBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, title, modeLabel, messageList, chips, inputRow);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");
        VBox.setVgrow(messageList, Priority.ALWAYS);

        root = new VBox(content);
        root.getStyleClass().addAll("side-panel", "ai-chat-panel");
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(root, Priority.ALWAYS);

        appendSystem(
                "Hi — I'm the GhanaWire co-pilot.\n"
                        + "• \"add socket in Living\" · \"add light in Kitchen\"\n"
                        + "• \"recalculate\" · \"generate design\"\n"
                        + "Or use the quick actions below. Design stays on the canvas until you accept AI previews."
        );
    }

    private static Button chip(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("ai-chip");
        b.setOnAction(e -> action.run());
        return b;
    }

    private void sendQuick(String msg) {
        inputField.setText(msg);
        sendCurrent();
    }

    private void sendCurrent() {
        String text = inputField.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        inputField.clear();
        appendUser(text.trim());
        onSend.accept(text.trim());
    }

    public VBox getRoot() {
        return root;
    }

    public void setOnSend(Consumer<String> onSend) {
        this.onSend = onSend == null ? s -> {
        } : onSend;
    }

    public void setOnGenerate(Runnable onGenerate) {
        this.onGenerate = onGenerate == null ? () -> {
        } : onGenerate;
    }

    public void setOnRecalculate(Runnable onRecalculate) {
        this.onRecalculate = onRecalculate == null ? () -> {
        } : onRecalculate;
    }

    public void setOnValidate(Runnable onValidate) {
        this.onValidate = onValidate == null ? () -> {
        } : onValidate;
    }

    public void focusInput() {
        Platform.runLater(() -> {
            inputField.requestFocus();
            refreshMode();
        });
    }

    public void refreshMode() {
        modeLabel.setText(modeHint());
    }

    public void setBusy(boolean busy) {
        sendBtn.setDisable(busy);
        inputField.setDisable(busy);
        sendBtn.setText(busy ? "…" : "Send");
    }

    public void appendUser(String text) {
        append(Role.USER, text);
    }

    public void appendAssistant(String text) {
        append(Role.ASSISTANT, text);
    }

    public void appendSystem(String text) {
        append(Role.SYSTEM, text);
    }

    public void append(Role role, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Platform.runLater(() -> {
            messageList.getItems().add(new ChatMessage(role, text.trim(), time));
            int last = messageList.getItems().size() - 1;
            if (last >= 0) {
                messageList.scrollTo(last);
            }
        });
    }

    public void clearConversation() {
        messageList.getItems().clear();
        appendSystem("Conversation cleared. How can I help with this design?");
    }

    private static String modeHint() {
        try {
            AiSettings s = AiSettings.load();
            if (s.isLlmAvailable()) {
                return "Mode: LLM + offline rules · " + s.model();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Mode: offline rules (add API key for LLM chat)";
    }

    private static final class ChatCell extends ListCell<ChatMessage> {
        private final VBox box = new VBox(4);
        private final Label meta = new Label();
        private final Label body = new Label();

        ChatCell() {
            meta.getStyleClass().add("ai-msg-meta");
            body.getStyleClass().add("ai-msg-body");
            body.setWrapText(true);
            body.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().addAll(meta, body);
            box.setPadding(new Insets(6, 8, 6, 8));
            box.setMaxWidth(Double.MAX_VALUE);
            setPrefWidth(0); // allow wrap in ListView
        }

        @Override
        protected void updateItem(ChatMessage item, boolean empty) {
            super.updateItem(item, empty);
            box.getStyleClass().removeAll("ai-msg-user", "ai-msg-assistant", "ai-msg-system");
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            String who = switch (item.role()) {
                case USER -> "You";
                case ASSISTANT -> "Co-pilot";
                case SYSTEM -> "Tip";
            };
            meta.setText(who + "  ·  " + item.time());
            body.setText(item.text());
            box.getStyleClass().add(switch (item.role()) {
                case USER -> "ai-msg-user";
                case ASSISTANT -> "ai-msg-assistant";
                case SYSTEM -> "ai-msg-system";
            });
            setGraphic(box);
            setText(null);
        }
    }
}
