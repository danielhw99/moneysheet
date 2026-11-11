package com.danielhw99.common.config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;

import com.danielhw99.common.error.ApiError;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class OpenApiConfiguration {

    @Bean
    public OpenAPI moneySheetOpenApi() {
        ResolvedSchema error = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
        Components components = new Components().schemas(new LinkedHashMap<>(
                error.referencedSchemas != null ? error.referencedSchemas : Collections.emptyMap()))
                .addSchemas("ApiError", error.schema);
        return new OpenAPI().info(new Info().title("MoneySheet API").version("v1")
                .description("개발용 API 문서입니다. 실제 구현된 API만 표시합니다. "
                        + "성공은 DTO 직접 반환, 오류는 code/message/fieldErrors/traceId 형식입니다. "
                        + "삭제 성공 204는 본문이 없습니다. 설계 기준 계약은 v0.1.1입니다."))
                .components(components);
    }

    @Bean
    public PropertyCustomizer temporalApiSchemas() {
        return (schema, type) -> {
            if (type.getType() == null) {
                return schema;
            }
            Class<?> rawType = Json.mapper().constructType(type.getType()).getRawClass();
            if (rawType == LocalTime.class) {
                schema.setType("string");
                schema.setFormat(null);
                schema.setPattern("^([01][0-9]|2[0-3]):[0-5][0-9]$");
                schema.setDescription("분 단위 사용 시각 (HH:mm)");
                schema.setExample("09:30");
            } else if (rawType == YearMonth.class) {
                schema.setType("string");
                schema.setPattern("^[0-9]{4}-(0[1-9]|1[0-2])$");
                schema.setDescription("관리 월 (yyyy-MM)");
                schema.setExample("2026-11");
            } else if (rawType == LocalDate.class) {
                schema.setPattern("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
            }
            return schema;
        };
    }

    @Bean
    public OpenApiCustomizer closedDtoSchemas() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(schema -> {
                if (schema.getProperties() != null && !schema.getProperties().isEmpty()
                        && schema.getAdditionalProperties() == null) {
                    schema.setAdditionalProperties(false);
                }
            });
        };
    }

}
