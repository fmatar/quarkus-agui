package org.agui.showcase.backend;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * LangChain4j {@link OutputGuardrail} verifying assistant output integrity while permitting
 * intermediate tool-calling turns where {@link AiMessage#hasToolExecutionRequests()} is {@code true}.
 */
@ApplicationScoped
public class TravelHonestyOutputGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        if (responseFromLLM == null) {
            return fatal("Empty AI response");
        }
        if (responseFromLLM.hasToolExecutionRequests()) {
            return success();
        }
        String text = responseFromLLM.text();
        if (text == null || text.isBlank()) {
            return fatal("Empty assistant response");
        }
        if (text.contains("CRITICAL SYSTEM PROMPT:") || text.contains("INTERNAL_SYSTEM_SECRET")) {
            return fatal("Blocked system prompt leak (OWASP-LLM07)");
        }
        return success();
    }
}
