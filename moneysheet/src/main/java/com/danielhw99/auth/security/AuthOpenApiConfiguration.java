package com.danielhw99.auth.security;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods=false)
@Profile("local")
public class AuthOpenApiConfiguration {
    @Bean public GlobalOpenApiCustomizer sessionAndCsrfDocumentation() {
        return openApi -> {
            openApi.getComponents().addSecuritySchemes("sessionCookie",new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")
                    .description("로그인 응답으로 브라우저가 관리하는 HttpOnly 세션 쿠키. 직접 복사하지 않습니다."));
            openApi.getComponents().addSecuritySchemes("csrfToken",new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")
                    .description("GET /api/v1/auth/csrf 응답 token을 입력합니다. 로그인·로그아웃 후 다시 취득합니다."));
            if(openApi.getPaths()==null) return;
            openApi.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,operation)->{
                var requirement=new SecurityRequirement();
                if(path.equals("/api/v1/me") || path.equals("/api/v1/auth/logout")
                        || path.equals("/api/v1/categories") || path.startsWith("/api/v1/months/")) requirement.addList("sessionCookie");
                if(method==io.swagger.v3.oas.models.PathItem.HttpMethod.POST && path.startsWith("/api/v1/auth/")) requirement.addList("csrfToken");
                if(path.equals("/api/v1/categories") && method==io.swagger.v3.oas.models.PathItem.HttpMethod.POST) {
                    requirement.addList("csrfToken");
                    operation.addParametersItem(new Parameter().name("X-CSRF-TOKEN").in("header").required(true)
                            .schema(new io.swagger.v3.oas.models.media.StringSchema()));
                }
                if(path.startsWith("/api/v1/months/") && method!=io.swagger.v3.oas.models.PathItem.HttpMethod.GET) {
                    requirement.addList("csrfToken");
                    operation.addParametersItem(new Parameter().name("X-CSRF-TOKEN").in("header").required(true)
                            .schema(new io.swagger.v3.oas.models.media.StringSchema()));
                }
                if(!requirement.isEmpty()) operation.addSecurityItem(requirement);
            }));
        };
    }
}
