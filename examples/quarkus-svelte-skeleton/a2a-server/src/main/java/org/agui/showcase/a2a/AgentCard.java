package org.agui.showcase.a2a;

import java.util.List;
import java.util.Map;

public record AgentCard(
    String name,
    String description,
    String url,
    String version,
    Map<String, Boolean> capabilities,
    List<AgentSkill> skills
) {}
