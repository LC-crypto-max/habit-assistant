package com.example.assistant.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public class IsoLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // Try offset and instant ISO-8601 values used by browser and PowerShell clients.
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through to instant parsing before returning a client-friendly error.
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
        } catch (DateTimeParseException exception) {
            throw context.weirdStringException(value, LocalDateTime.class,
                    "must be an ISO-8601 datetime, for example 2026-06-12T10:00:00 or 2026-06-12T10:00:00+08:00");
        }
    }
}
