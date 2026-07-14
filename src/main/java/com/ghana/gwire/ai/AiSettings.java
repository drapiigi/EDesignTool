package com.ghana.gwire.ai;

import com.ghana.gwire.service.persist.GwireHome;
import com.ghana.gwire.service.security.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * AI / LLM configuration for GhanaWire design generation.
 *
 * <p>Load order: environment variables override local secrets store
 * ({@code ~/.gwire/secrets.properties}, mode 0600), then non-secret
 * {@code ~/.gwire/ai.properties}. <b>Never log the full API key</b> —
 * use {@link #maskedKey()} if a hint is required.
 */
public final class AiSettings {

    private static final Logger log = LoggerFactory.getLogger(AiSettings.class);

    public static final String DEFAULT_OPENAI_BASE = "https://api.openai.com/v1";
    public static final String DEFAULT_XAI_BASE = "https://api.x.ai/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";

    public enum Provider {
        NONE,
        OPENAI_COMPAT
    }

    private final Provider provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;

    public AiSettings(Provider provider, String apiKey, String baseUrl, String model, boolean enabled) {
        this.provider = provider == null ? Provider.NONE : provider;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.enabled = enabled;
    }

    /**
     * Loads settings: env → secrets store → ai.properties (non-secret).
     * Missing file/env is fine — returns a disabled NONE configuration.
     */
    public static AiSettings load() {
        SecretStore secrets = SecretStore.local();
        secrets.migrateFromAiPropertiesIfNeeded();

        Properties props = new Properties();
        Path file = GwireHome.aiProperties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
                log.debug("Loaded AI settings from {}", file);
            } catch (IOException e) {
                log.warn("Could not read AI properties {}: {}", file, e.getMessage());
            }
        }

        String apiKey = firstNonBlank(
                env("GWIRE_AI_API_KEY"),
                env("OPENAI_API_KEY"),
                env("XAI_API_KEY"),
                secrets.getApiKey().orElse(null),
                props.getProperty("apiKey") // legacy leftover if migration failed
        );

        String baseUrl = firstNonBlank(
                env("GWIRE_AI_BASE_URL"),
                props.getProperty("baseUrl")
        );

        String model = firstNonBlank(
                env("GWIRE_AI_MODEL"),
                props.getProperty("model"),
                DEFAULT_MODEL
        );

        String providerRaw = firstNonBlank(
                env("GWIRE_AI_PROVIDER"),
                props.getProperty("provider")
        );
        if (providerRaw == null) {
            providerRaw = "";
        }
        providerRaw = providerRaw.toLowerCase(Locale.ROOT);

        Provider provider;
        String resolvedBase = baseUrl;
        switch (providerRaw) {
            case "none", "" -> {
                // Infer provider from key presence / known base URL
                if (apiKey == null || apiKey.isBlank()) {
                    provider = Provider.NONE;
                    resolvedBase = firstNonBlank(baseUrl, DEFAULT_OPENAI_BASE);
                } else if (baseUrl != null && baseUrl.contains("api.x.ai")) {
                    provider = Provider.OPENAI_COMPAT;
                    resolvedBase = firstNonBlank(baseUrl, DEFAULT_XAI_BASE);
                } else {
                    provider = Provider.OPENAI_COMPAT;
                    resolvedBase = firstNonBlank(baseUrl, DEFAULT_OPENAI_BASE);
                }
            }
            case "openai" -> {
                provider = Provider.OPENAI_COMPAT;
                resolvedBase = firstNonBlank(baseUrl, DEFAULT_OPENAI_BASE);
            }
            case "xai" -> {
                provider = Provider.OPENAI_COMPAT;
                resolvedBase = firstNonBlank(baseUrl, DEFAULT_XAI_BASE);
            }
            case "openai_compat", "compat" -> {
                provider = Provider.OPENAI_COMPAT;
                resolvedBase = firstNonBlank(baseUrl, DEFAULT_OPENAI_BASE);
            }
            default -> {
                log.warn("Unknown GWIRE_AI_PROVIDER '{}'; treating as none", providerRaw);
                provider = Provider.NONE;
                resolvedBase = firstNonBlank(baseUrl, DEFAULT_OPENAI_BASE);
            }
        }

        boolean enabled = provider != Provider.NONE && apiKey != null && !apiKey.isBlank();
        return new AiSettings(provider, apiKey, resolvedBase, model, enabled);
    }

    /** True when a non-blank key is present and provider is not {@link Provider#NONE}. */
    public boolean isLlmAvailable() {
        return enabled && provider != Provider.NONE && apiKey != null && !apiKey.isBlank();
    }

    public Provider provider() {
        return provider;
    }

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Masked key for UI/logs (never the full secret).
     * Example: {@code sk-ab…wxyz} or {@code (empty)}.
     */
    public String maskedKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "(empty)";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "…" + apiKey.substring(apiKey.length() - 4);
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null ? null : v.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String u = baseUrl == null || baseUrl.isBlank() ? DEFAULT_OPENAI_BASE : baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    @Override
    public String toString() {
        return "AiSettings{provider=" + provider
                + ", baseUrl=" + baseUrl
                + ", model=" + model
                + ", key=" + maskedKey()
                + ", enabled=" + enabled + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiSettings that)) {
            return false;
        }
        return enabled == that.enabled
                && provider == that.provider
                && Objects.equals(apiKey, that.apiKey)
                && Objects.equals(baseUrl, that.baseUrl)
                && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, apiKey, baseUrl, model, enabled);
    }
}
