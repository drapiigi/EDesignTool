package com.ghana.gwire.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Minimal OpenAI-compatible chat completions client (OpenAI, xAI Grok, local proxies).
 * Package-visible for tests; constructed by {@link LlmDesignGenerator} / {@link AiDesignService}.
 *
 * <p>Does not log API keys.
 */
public final class OpenAiCompatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final AiSettings settings;
    private final HttpClient http;

    public OpenAiCompatClient(AiSettings settings) {
        this(settings, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    OpenAiCompatClient(AiSettings settings, HttpClient http) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.http = Objects.requireNonNull(http, "http");
    }

    /**
     * Sends a chat completion request and returns the first choice content text.
     *
     * @throws IOException on network/HTTP/parse failure
     */
    public String chat(String system, String user) throws IOException {
        if (!settings.isLlmAvailable()) {
            throw new IOException("LLM not available (missing API key or provider NONE)");
        }

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", settings.model());
        ArrayNode messages = body.putArray("messages");
        if (system != null && !system.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", system);
        }
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", user == null ? "" : user);
        body.put("temperature", 0.2);

        String url = settings.baseUrl() + "/chat/completions";
        byte[] payload = MAPPER.writeValueAsBytes(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        log.debug("POST chat/completions model={} url={}", settings.model(), settings.baseUrl());

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Chat request interrupted", e);
        }

        int code = response.statusCode();
        String respBody = response.body() == null ? "" : response.body();
        if (code < 200 || code >= 300) {
            // Never include Authorization; truncate body for log safety
            String snippet = respBody.length() > 200 ? respBody.substring(0, 200) + "…" : respBody;
            log.warn("LLM HTTP {} from {} — body snippet: {}", code, settings.baseUrl(), snippet);
            throw new IOException("LLM HTTP " + code + ": " + snippet);
        }

        try {
            JsonNode root = MAPPER.readTree(respBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IOException("LLM response missing choices[0].message.content");
            }
            return content.asText();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse LLM response: " + e.getMessage(), e);
        }
    }
}
