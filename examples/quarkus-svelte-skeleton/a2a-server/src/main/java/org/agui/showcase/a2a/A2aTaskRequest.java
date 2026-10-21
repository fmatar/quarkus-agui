package org.agui.showcase.a2a;

import java.util.Map;

public record A2aTaskRequest(
    String taskId,
    String skillId,
    String prompt,
    Map<String, Object> context
) {}
