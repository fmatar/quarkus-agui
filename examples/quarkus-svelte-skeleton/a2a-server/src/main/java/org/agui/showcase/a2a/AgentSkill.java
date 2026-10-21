package org.agui.showcase.a2a;

import java.util.List;

public record AgentSkill(
    String id,
    String name,
    String description,
    List<String> tags
) {}
