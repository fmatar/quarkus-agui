package org.agui.showcase.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ephemeral local test harness for {@code @QuarkusTest} validating Quarkus LangChain4j MCP
 * ({@code QuarkusStreamableHttpMcpTransport}), {@code McpClientHealthCheck}, SmallRye Health,
 * and {@code @RegisterAiService NomadConciergeAiService} without external network dependencies.
 */
public class MockTriProtocolTestResource implements QuarkusTestResourceLifecycleManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String baseUrl = "http://127.0.0.1:" + port;

            server.createContext("/mcp", this::handleMcpJsonRpc);
            server.createContext("/q/health/ready", exchange -> writeJson(exchange, 200, Map.of(
                    "status", "UP",
                    "checks", List.of(Map.of(
                            "name", "nomad-a2a-upstreams",
                            "status", "UP",
                            "data", Map.of(
                                    "frankfurter-fx.status", "UP",
                                    "restcountries-advisory.status", "UP"))))));
            server.createContext("/api/a2a/audit", exchange -> writeJson(exchange, 200, Map.of(
                    "artifactId", "art-adv-ok",
                    "artifactTitle", "Destination Advisory",
                    "summary", "REST Countries v5 verified for France (Paris, EUR)",
                    "data", Map.of(
                            "status", "OK",
                            "isError", false,
                            "serviceAvailable", true,
                            "country", "France",
                            "capital", "Paris",
                            "currency", "EUR",
                            "source", "api.restcountries.com"))));
            server.createContext("/api/tags", exchange -> writeJson(
                    exchange, 200, Map.of("models", List.of(Map.of("name", "llama3.2:latest")))));
            server.createContext("/api/chat", this::handleOllamaChat);

            server.start();
            return Map.of(
                    "quarkus.langchain4j.mcp.nomad.url", baseUrl + "/mcp",
                    "quarkus.langchain4j.ollama.base-url", baseUrl,
                    "agui.mcp.health-url", baseUrl + "/q/health/ready",
                    "agui.a2a.base-url", baseUrl);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start MockTriProtocolTestResource", ex);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleMcpJsonRpc(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        JsonNode rpc = MAPPER.readTree(raw);
        String method = rpc.path("method").asText("");
        JsonNode idNode = rpc.get("id");
        if (idNode == null || method.startsWith("notifications/")) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", "mcp-test-session");
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        Map<String, Object> result = switch (method) {
            case "initialize" -> Map.of(
                    "protocolVersion", "2025-11-25",
                    "capabilities", Map.of(
                            "tools", Map.of("listChanged", false),
                            "resources", Map.of("listChanged", false),
                            "prompts", Map.of("listChanged", false)),
                    "serverInfo", Map.of("name", "nomad-mcp-concierge", "version", "1.0.0"));
            case "ping" -> Map.of();
            case "tools/list" -> Map.of("tools", List.of(
                    toolSpec("fetchCityWeather", "Fetch live Open-Meteo weather"),
                    toolSpec("discoverLocalCultureAndMedia", "Discover Wikipedia & iTunes media"),
                    toolSpec("updateTripItinerary", "Update trip itinerary"),
                    toolSpec("sampleAcousticMood", "Sample twilight acoustic mood"),
                    toolSpec("saveTravelerPreferences", "Persist traveler preferences"),
                    toolSpec("resetNomadState", "Reset MCP trip & preferences")));
            case "tools/call" -> handleMcpToolCall(rpc.path("params"));
            default -> Map.of();
        };

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", MAPPER.convertValue(idNode, Object.class));
        envelope.put("result", result);
        exchange.getResponseHeaders().add("Mcp-Session-Id", "mcp-test-session");
        writeJson(exchange, 200, envelope);
    }

    private static Map<String, Object> toolSpec(String name, String description) {
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", Map.of("type", "object", "properties", Map.of()));
    }

    private static Map<String, Object> handleMcpToolCall(JsonNode params) throws IOException {
        String name = params.path("name").asText("");
        JsonNode args = params.path("arguments");
        String payloadJson = switch (name) {
            case "fetchCityWeather" -> MAPPER.writeValueAsString(Map.of(
                    "status", "OK",
                    "city", args.path("city").asText("Oaxaca"),
                    "country", "Mexico",
                    "temperatureC", 22.4,
                    "condition", "Clear Sky & Crisp Light",
                    "source", "api.open-meteo.com"));
            case "updateTripItinerary" -> MAPPER.writeValueAsString(Map.of(
                    "status", "OK",
                    "uri", "itinerary://active-trip",
                    "activeCity", args.path("city").asText("Kyoto"),
                    "stopTitle", args.path("stopTitle").asText("Hoshinoya Arashiyama Pavilion"),
                    "nights", args.path("nights").asInt(4),
                    "allocatedUsd", args.path("costUsd").asDouble(1840.0)));
            default -> MAPPER.writeValueAsString(Map.of("status", "OK", "reset", true));
        };
        return Map.of("isError", false, "content", List.of(Map.of("type", "text", "text", payloadJson)));
    }

    private void handleOllamaChat(HttpExchange exchange) throws IOException {
        String reqStr = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode root = MAPPER.readTree(reqStr);
        JsonNode messages = root.path("messages");
        JsonNode lastMsg = messages.isArray() && !messages.isEmpty() ? messages.get(messages.size() - 1) : null;
        String lastRole = lastMsg == null ? "" : lastMsg.path("role").asText("");
        String lastContent = lastMsg == null ? "" : lastMsg.path("content").asText("");

        Map<String, Object> chatResp;
        if (!"tool".equalsIgnoreCase(lastRole) && lastContent.contains("Reserve Hoshinoya")) {
            chatResp = Map.of(
                    "model", "llama3.2:latest",
                    "done", true,
                    "message", Map.of(
                            "role", "assistant",
                            "content", "",
                            "tool_calls", List.of(Map.of("function", Map.of(
                                    "name", "requestSanctuaryReservation",
                                    "arguments", Map.of(
                                            "city", "Kyoto",
                                            "stopTitle", "Hoshinoya Arashiyama Pavilion",
                                            "nights", 4,
                                            "costUsd", 1840.0))))));
        } else if ("tool".equalsIgnoreCase(lastRole) && lastContent.contains("AWAITING_APPROVAL")) {
            chatResp = Map.of(
                    "model", "llama3.2:latest",
                    "done", true,
                    "message", Map.of(
                            "role", "assistant",
                            "content", "Reservation hold paused pending traveler HITL approval above $1,500 USD."));
        } else if (lastContent.contains("I want a trip in Paris")) {
            chatResp = Map.of(
                    "model", "llama3.2:latest",
                    "done", true,
                    "message", Map.of(
                            "role", "assistant",
                            "content", "Before I plan Paris, please share your trip duration (nights/days), budget & currency, and travel focus—I never assume 3 days."));
        } else {
            chatResp = Map.of(
                    "model", "llama3.2:latest",
                    "done", true,
                    "message", Map.of(
                            "role", "assistant",
                            "content", "Welcome! Which destination city would you like to explore?"));
        }
        writeJson(exchange, 200, chatResp);
    }

    private static void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = (MAPPER.writeValueAsString(payload) + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
