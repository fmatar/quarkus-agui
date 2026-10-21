package org.agui.showcase.a2a;

import java.util.List;

public record A2aTaskResponse(
    String taskId,
    String agentName,
    String skillId,
    String status,
    String summary,
    List<A2aArtifact> artifacts
) {}
