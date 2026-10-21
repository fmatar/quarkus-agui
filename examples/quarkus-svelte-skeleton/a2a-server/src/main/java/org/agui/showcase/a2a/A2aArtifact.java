package org.agui.showcase.a2a;

import java.util.Map;

public record A2aArtifact(
    String id,
    String type,
    String title,
    Map<String, Object> data
) {}
