package com.promoit.otp.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Tiny JSON helper around a shared Jackson {@link ObjectMapper}. */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }

    public static Map<String, Object> readMap(InputStream in) throws IOException {
        return MAPPER.readValue(in, MAPPER.getTypeFactory()
                .constructMapType(Map.class, String.class, Object.class));
    }
}
