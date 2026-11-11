package com.danielhw99.common.config;

import com.danielhw99.common.error.ApiError;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class OpenApiConfiguration {

    @Bean
    public OpenAPI moneySheetOpenApi() {
        ResolvedSchema error = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
        Components components = new Components().schemas(error.referencedSchemas).addSchemas("ApiError", error.schema);
        return new OpenAPI().info(new Info().title("MoneySheet API").version("v1")
                .description("개발용 API 문서입니다. 실제 구현된 API만 표시합니다. "
                        + "성공은 DTO 직접 반환, 오류는 code/message/fieldErrors/traceId 형식입니다. "
                        + "삭제 성공 204는 본문이 없습니다. 설계 기준 계약은 v0.1.1입니다."))
                .components(components);
    }
}
