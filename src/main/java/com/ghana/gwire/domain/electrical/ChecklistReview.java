package com.ghana.gwire.domain.electrical;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CEWP validation checklist workflow — which issue codes were reviewed.
 */
public final class ChecklistReview {

    public record Entry(boolean reviewed, String note) {
        public Entry {
            note = note == null ? "" : note;
        }
    }

    private final Map<String, Entry> byCode = new LinkedHashMap<>();

    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(byCode);
    }

    public void setReviewed(String code, boolean reviewed, String note) {
        if (code == null || code.isBlank()) {
            return;
        }
        byCode.put(code, new Entry(reviewed, note));
    }

    public boolean isReviewed(String code) {
        Entry e = byCode.get(code);
        return e != null && e.reviewed();
    }

    public String note(String code) {
        Entry e = byCode.get(code);
        return e == null ? "" : e.note();
    }

    public void clear() {
        byCode.clear();
    }

    public void putAll(Map<String, Entry> map) {
        byCode.clear();
        if (map != null) {
            byCode.putAll(map);
        }
    }

    public ChecklistReview copy() {
        ChecklistReview c = new ChecklistReview();
        c.putAll(byCode);
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChecklistReview that)) {
            return false;
        }
        return Objects.equals(byCode, that.byCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byCode);
    }
}
