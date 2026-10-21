package org.agui.core.adapters;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import org.agui.core.application.AgUiEventSerializer;
import org.agui.core.domain.AgUiEvent;

/**
 * Zero-dependency, sub-10ms AG-UI JSON &amp; SSE wire formatter (Level 2 Adapter).
 */
public class AgUiSseFormatter implements AgUiEventSerializer {

    @Override
    public String toJson(AgUiEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        StringBuilder sb = new StringBuilder(192);
        sb.append("{\"id\":\"").append(escapeJson(event.id()))
                .append("\",\"type\":\"").append(escapeJson(event.type()))
                .append("\",\"threadId\":\"").append(escapeJson(event.threadId()))
                .append("\",\"timestamp\":").append(event.timestamp())
                .append(",\"payload\":");
        appendValue(sb, event.payload());
        sb.append('}');
        return sb.toString();
    }

    public String formatSse(AgUiEvent<?> event) {
        return "data: " + toJson(event) + "\n\n";
    }

    public static String unwrapSseData(String rawFrame) {
        if (rawFrame == null) {
            return "";
        }
        return rawFrame.replaceFirst("^data:\\s*", "").stripTrailing();
    }

    private void appendValue(StringBuilder sb, Object val) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof String s) {
            sb.append('"').append(escapeJson(s)).append('"');
        } else if (val instanceof Number || val instanceof Boolean) {
            sb.append(val);
        } else if (val instanceof Map<?, ?> map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                sb.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                appendValue(sb, entry.getValue());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else if (val instanceof Collection<?> col) {
            sb.append('[');
            Iterator<?> it = col.iterator();
            while (it.hasNext()) {
                appendValue(sb, it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
        } else if (val.getClass().isRecord()) {
            sb.append('{');
            RecordComponent[] components = val.getClass().getRecordComponents();
            for (int i = 0; i < components.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escapeJson(components[i].getName())).append("\":");
                try {
                    appendValue(sb, components[i].getAccessor().invoke(val));
                } catch (ReflectiveOperationException e) {
                    sb.append("null");
                }
            }
            sb.append('}');
        } else {
            sb.append('"').append(escapeJson(String.valueOf(val))).append('"');
        }
    }

    static String escapeJson(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
