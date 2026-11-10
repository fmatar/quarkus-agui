package org.agui.langchain4j.domain;

import com.agui.community.core.event.JsonPatchOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 6901 JSON Pointer and RFC 6902 JSON Patch helpers producing canonical {@link JsonPatchOperation} records.
 */
public final class AgUiStatePatch {

    private AgUiStatePatch() {
    }

    public static String toPointer(String keyOrPointer) {
        Objects.requireNonNull(keyOrPointer, "keyOrPointer must not be null");
        if (keyOrPointer.isBlank()) {
            throw new IllegalArgumentException("keyOrPointer must not be blank");
        }
        if (keyOrPointer.startsWith("/")) {
            return keyOrPointer;
        }
        String escaped = keyOrPointer.replace("~", "~0").replace("/", "~1");
        return "/" + escaped;
    }

    public static JsonPatchOperation add(String keyOrPointer, Object value) {
        return new JsonPatchOperation("add", toPointer(keyOrPointer), value);
    }

    public static JsonPatchOperation replace(String keyOrPointer, Object value) {
        return new JsonPatchOperation("replace", toPointer(keyOrPointer), value);
    }

    public static JsonPatchOperation remove(String keyOrPointer) {
        return new JsonPatchOperation("remove", toPointer(keyOrPointer), null);
    }

    /**
     * Converts a map of top-level state fields into RFC 6902 {@code add} operations
     * (RFC 6902 Section 4.1 adds missing object members and replaces existing ones).
     */
    public static List<JsonPatchOperation> upsertRootFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        List<JsonPatchOperation> patches = new ArrayList<>(fields.size());
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            patches.add(add(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(patches);
    }
}
