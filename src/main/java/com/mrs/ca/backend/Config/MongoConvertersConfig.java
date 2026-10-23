package com.mrs.ca.backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Custom MongoDB type converters ensuring date-only fields (java.time.LocalDate)
 * are stored as ISO-8601 strings (YYYY-MM-DD) to prevent timezone offset shifts
 * when dates are stored or retrieved across UTC and Asia/Kolkata (UTC+5:30).
 * <p>
 * Backward compatible: reads both ISO strings and legacy BSON Date timestamps.
 * Active in both production and test profiles.
 */
@Configuration
public class MongoConvertersConfig {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new LocalDateToStringConverter());
        converters.add(new StringToLocalDateConverter());
        converters.add(new DateToLocalDateConverter());
        return new MongoCustomConversions(converters);
    }

    /**
     * Serializes LocalDate as pure YYYY-MM-DD string with zero timezone shift.
     */
    @WritingConverter
    public static class LocalDateToStringConverter implements Converter<LocalDate, String> {
        @Override
        public String convert(LocalDate source) {
            return source != null ? source.format(ISO_FORMATTER) : null;
        }
    }

    /**
     * Deserializes ISO YYYY-MM-DD string to LocalDate.
     */
    @ReadingConverter
    public static class StringToLocalDateConverter implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            String trimmed = source.trim();
            // Handle full ISO timestamp strings if legacy data exists as string (e.g. 2024-10-23T00:00:00)
            if (trimmed.length() > 10) {
                trimmed = trimmed.substring(0, 10);
            }
            return LocalDate.parse(trimmed, ISO_FORMATTER);
        }
    }

    /**
     * Deserializes legacy BSON Date timestamp into LocalDate using Asia/Kolkata business zone,
     * ensuring no calendar day shifts occur for dates recorded at local midnight.
     */
    @ReadingConverter
    public static class DateToLocalDateConverter implements Converter<Date, LocalDate> {
        @Override
        public LocalDate convert(Date source) {
            if (source == null) {
                return null;
            }
            return Instant.ofEpochMilli(source.getTime())
                    .atZone(IST_ZONE)
                    .toLocalDate();
        }
    }
}
