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
 * Supports text and multimodal (vision) messages. Does not log API keys.
 */
public final class OpenAiCompatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration VISION_TIMEOUT = Duration.ofSeconds(120);

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
     * Text-only chat completion; returns first choice content.
     */
    public String chat(String system, String user) throws IOException {
        ObjectNode body = baseBody(system);
        ObjectNode usr = body.withArray("messages").addObject();
        usr.put("role", "user");
        usr.put("content", user == null ? "" : user);
        return postCompletions(body, TIMEOUT);
    }

    /**
     * Multimodal chat: text + one base64 image (data URL assembled here).
     *
     * @param mediaType e.g. {@code image/png} or {@code image/jpeg}
     * @param base64    raw base64 payload (no data: prefix)
     */
    public String chatWithImage(String system, String userText, String mediaType, String base64)
            throws IOException {
        if (base64 == null || base64.isBlank()) {
            throw new IOException("Image payload is empty");
        }
        String mt = mediaType == null || mediaType.isBlank() ? "image/png" : mediaType;
        String dataUrl = "data:" + mt + ";base64," + base64;

        ObjectNode body = baseBody(system);
        ObjectNode usr = body.withArray("messages").addObject();
        usr.put("role", "user");
        ArrayNode content = usr.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", userText == null ? "" : userText);
        ObjectNode imgPart = content.addObject();
        imgPart.put("type", "image_url");
        ObjectNode imageUrl = imgPart.putObject("image_url");
        imageUrl.put("url", dataUrl);
        imageUrl.put("detail", "high");

        return postCompletions(body, VISION_TIMEOUT);
    }

    private ObjectNode baseBody(String system) throws IOException {
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
        body.put("temperature", 0.2);
        return body;
    }

    private String postCompletions(ObjectNode body, Duration timeout) throws IOException {
        String url = settings.baseUrl() + "/chat/completions";
        byte[] payload = MAPPER.writeValueAsBytes(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        boolean hasVision = body.path("messages").toString().contains("\"image_url\"");
        // Do not log request body — may include large base64 images or secrets
        log.debug("POST chat/completions model={} url={} vision={}",
                settings.model(), settings.baseUrl(), hasVision);

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
