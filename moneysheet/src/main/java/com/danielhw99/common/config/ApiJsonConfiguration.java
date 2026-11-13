package com.danielhw99.common.config;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.function.Function;

import com.danielhw99.common.api.ApiFormats;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class ApiJsonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer strictApiJson() {
        return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Boolean, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    @Bean
    public SimpleModule apiTemporalModule() {
        SimpleModule module = new SimpleModule("MoneySheetApiTemporal");
        addTemporal(module, LocalDate.class, ApiFormats::parseDate, ApiFormats::formatDate);
        addTemporal(module, LocalTime.class, ApiFormats::parseTime, ApiFormats::formatTime);
        addTemporal(module, YearMonth.class, ApiFormats::parseMonth, ApiFormats::formatMonth);
        return module;
    }

    private static <T> void addTemporal(SimpleModule module, Class<T> type,
            Function<String, T> parse, Function<T, String> format) {
        module.addDeserializer(type, new TemporalDeserializer<>(type, parse));
        module.addSerializer(type, new TemporalSerializer<>(type, format));
    }

    private static class TemporalDeserializer<T> extends StdDeserializer<T> {

        private final Class<T> type;
        private final Function<String, T> parse;

        TemporalDeserializer(Class<T> type, Function<String, T> parse) {
            super(type);
            this.type = type;
            this.parse = parse;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return context.reportInputMismatch(type, "Date/time must be a JSON string.");
            }
            try {
                return parse.apply(parser.getString());
            } catch (IllegalArgumentException | DateTimeException exception) {
                return context.reportInputMismatch(type, "Invalid API date/time format.");
            }
        }
    }

    private static class TemporalSerializer<T> extends StdSerializer<T> {

        private final Function<T, String> format;

        TemporalSerializer(Class<T> type, Function<T, String> format) {
            super(type);
            this.format = format;
        }

        @Override
        public void serialize(T value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(format.apply(value));
        }
    }
}
