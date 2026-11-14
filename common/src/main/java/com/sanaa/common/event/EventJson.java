package com.sanaa.common.event;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 JSON helper for Kafka event payloads.
 *
 * <p>WHY a dedicated mapper: Boot's HTTP mapper is not used for Kafka, and event contracts must not
 * change silently when a service customizes its web serialization. Unknown properties are ignored
 * so an older consumer can still read a newer producer's envelope.
 */
public final class EventJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private EventJson() {
    }

    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /**
     * Reads an envelope whose payload has the given concrete type. The payload type is explicit
     * because the generic record alone erases it to {@code Object}, which Jackson would turn into a
     * map instead of the typed payload record.
     */
    public static <T> EventEnvelope<T> fromJson(String json, Class<T> payloadType) {
        return MAPPER.readValue(json,
                MAPPER.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType));
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }
}
