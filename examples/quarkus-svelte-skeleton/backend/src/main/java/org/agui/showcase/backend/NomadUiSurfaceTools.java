package org.agui.showcase.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LangChain4j {@code @Tool} beans driving interactive AG-UI ChoiceCard elicitation and
 * Human-in-the-Loop (HITL) reservation approval gates (`>= $1,500 USD`).
 */
@ApplicationScoped
public class NomadUiSurfaceTools {

    private final TripDossierStore dossierStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public NomadUiSurfaceTools(TripDossierStore dossierStore) {
        this.dossierStore = dossierStore;
    }

    @Tool("Present an interactive AG-UI choice card of curated neighborhood, architectural, and acoustic experiences for a destination city.")
    public String presentTravelChoices(
            @ToolMemoryId Object memoryId,
            @P("Destination city name (required)") String city,
            @P("Whether the user can select multiple options (true) or a single option (false)") Boolean multiSelect,
            @P("Number of options to present between 2 and 6") Integer optionCount) {
        if (city == null || city.isBlank()) {
            return missingArgsJson("city");
        }
        String trimmedCity = city.trim();
        int clampedCount = optionCount == null ? 3 : Math.max(2, Math.min(6, optionCount));
        String mode = Boolean.TRUE.equals(multiSelect) ? "multi_select" : "single_select";

        List<Map<String, Object>> allTemplates = List.of(
                Map.of(
                        "id", "exp-arch",
                        "label", trimmedCity + " Architectural Courtyards & Ateliers",
                        "description", "Morning timber, stone, and modernist gallery walk",
                        "badge", "Architecture",
                        "prompt", "Focus on architectural courtyards in " + trimmedCity),
                Map.of(
                        "id", "exp-sound",
                        "label", trimmedCity + " Twilight Listening Bar & Acoustic Session",
                        "description", "Intimate regional strings, vinyl, and chamber acoustics",
                        "badge", "Acoustics",
                        "prompt", "Add a twilight acoustic session in " + trimmedCity),
                Map.of(
                        "id", "exp-terroir",
                        "label", trimmedCity + " Seasonal Market & Terroir Table",
                        "description", "Slow market tasting with heritage local producers",
                        "badge", "Culinary",
                        "prompt", "Include a seasonal terroir tasting in " + trimmedCity),
                Map.of(
                        "id", "exp-craft",
                        "label", trimmedCity + " Ceramics, Paper & Textile Guilds",
                        "description", "Studio visits with living neighborhood craftspeople",
                        "badge", "Craft",
                        "prompt", "Visit artisan workshops in " + trimmedCity),
                Map.of(
                        "id", "exp-garden",
                        "label", trimmedCity + " Dawn Botanical & Waterfront Promenade",
                        "description", "Unrushed golden-hour walk before crowds arrive",
                        "badge", "Sanctuary",
                        "prompt", "Add a dawn garden walk in " + trimmedCity),
                Map.of(
                        "id", "exp-cinema",
                        "label", trimmedCity + " Independent Bookshop & Repertory Salon",
                        "description", "Late-evening literary salon and espresso bar",
                        "badge", "Culture",
                        "prompt", "Explore independent bookshops in " + trimmedCity));

        List<Map<String, Object>> options = new ArrayList<>(allTemplates.subList(0, clampedCount));
        Map<String, Object> interaction = Map.of(
                "id", "choices-" + UUID.randomUUID().toString().substring(0, 8),
                "mode", mode,
                "prompt", "Tailor your " + trimmedCity + " itinerary cadence:",
                "options", options);
        dossierStore.recordInteraction(resolveThreadId(memoryId), interaction);
        return toJson(Map.of(
                "status", "OK",
                "isError", false,
                "city", trimmedCity,
                "mode", mode,
                "optionCount", clampedCount,
                "interaction", interaction));
    }

    @Tool("Request a sanctuary or hotel reservation hold. Holds of $1,500 USD or above automatically pause at the Human-in-the-Loop (HITL) traveler approval gate before committing.")
    public String requestSanctuaryReservation(
            @ToolMemoryId Object memoryId,
            @P("Destination city name (required)") String city,
            @P("Sanctuary, hotel, or ryokan name (required)") String stopTitle,
            @P("Number of nights as a positive integer (required)") Integer nights,
            @P("Total reservation hold amount in USD as a positive number (required)") Double costUsd) {
        if (city == null || city.isBlank()
                || stopTitle == null || stopTitle.isBlank()
                || nights == null || nights <= 0
                || costUsd == null || costUsd <= 0.0) {
            return missingArgsJson("city, stopTitle, nights (> 0), costUsd (> 0)");
        }
        String threadId = resolveThreadId(memoryId);
        String trimmedCity = city.trim();
        String trimmedStop = stopTitle.trim();
        if (costUsd >= TripDossierStore.HITL_THRESHOLD_USD) {
            TripDossierStore.PendingHitlReservation pending = dossierStore.stagePendingApproval(
                    threadId,
                    "hitl-" + UUID.randomUUID().toString().substring(0, 8),
                    trimmedCity,
                    trimmedStop,
                    nights,
                    costUsd);
            return toJson(Map.of(
                    "status", "AWAITING_APPROVAL",
                    "isError", false,
                    "approvalRequired", true,
                    "callId", pending.callId(),
                    "city", pending.city(),
                    "stopTitle", pending.stopTitle(),
                    "nights", pending.nights(),
                    "costUsd", pending.costUsd(),
                    "message", "Reservation hold >= $1,500 USD staged for traveler HITL confirmation."));
        }
        return toJson(dossierStore.executeApprovedItineraryUpdate(trimmedCity, trimmedStop, nights, costUsd));
    }

    private String missingArgsJson(String fields) {
        return toJson(Map.of(
                "status", "ERROR",
                "isError", true,
                "code", "MISSING_REQUIRED_ARGUMENTS",
                "error", "Missing required arguments (" + fields + "). Ask the user instead of assuming defaults."));
    }

    private static String resolveThreadId(Object memoryId) {
        return memoryId == null ? "thread-nomad-1" : String.valueOf(memoryId);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return "{\"status\":\"ERROR\",\"isError\":true,\"error\":\"Serialization failure\"}";
        }
    }
}
