package com.shopsphere.notificationservice.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * US40 — persists the template model as JSON so retries render the identical email.
 * Decimals round-trip as BigDecimal (keeps "100.00", not "100.0").
 */
@Component
public class TemplateModelCodec {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public String write(Map<String, Object> model) {
        try {
            return mapper.writeValueAsString(model == null ? Map.of() : model);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Template model is not serialisable: " + ex.getMessage(), ex);
        }
    }

    /** Always returns a mutable map. */
    public Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored template model is corrupt: " + ex.getMessage(), ex);
        }
    }
}
