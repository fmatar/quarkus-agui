package org.agui.showcase.backend;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Locale;

/**
 * LangChain4j {@link InputGuardrail} enforcing OWASP-LLM01 prompt-injection safety
 * on inbound traveler messages before model or MCP tool invocation.
 */
@ApplicationScoped
public class TravelSafetyInputGuardrail implements InputGuardrail {

    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "reveal system prompt",
            "drop table",
            "rm -rf /");

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (userMessage == null || userMessage.singleText() == null) {
            return success();
        }
        String lower = userMessage.singleText().toLowerCase(Locale.ROOT);
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                return fatal("Blocked prompt-injection pattern (OWASP-LLM01)");
            }
        }
        return success();
    }
}
