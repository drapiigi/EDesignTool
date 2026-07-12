package com.ghana.gwire.service.sld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hierarchical single-line diagram model for export and on-screen preview.
 */
public final class SingleLineDiagram {

    public enum NodeKind {
        SUPPLY,
        MAIN_SWITCH,
        RCD,
        BUSBAR,
        MCB,
        LOAD,
        EARTH
    }

    public static final class Node {
        private final NodeKind kind;
        private final String label;
        private final String detail;
        private final List<Node> children = new ArrayList<>();

        public Node(NodeKind kind, String label, String detail) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.detail = detail == null ? "" : detail;
        }

        public NodeKind kind() {
            return kind;
        }

        public String label() {
            return label;
        }

        public String detail() {
            return detail;
        }

        public List<Node> children() {
            return Collections.unmodifiableList(children);
        }

        public Node add(Node child) {
            if (child != null) {
                children.add(child);
            }
            return this;
        }
    }

    private final String title;
    private final Node root;
    private final String notes;

    public SingleLineDiagram(String title, Node root, String notes) {
        this.title = title == null ? "Single-line diagram" : title;
        this.root = root;
        this.notes = notes == null ? "" : notes;
    }

    public String title() {
        return title;
    }

    public Node root() {
        return root;
    }

    public String notes() {
        return notes;
    }
}
