package com.danielhw99.common;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import com.danielhw99.common.config.OpenApiConfiguration;
import com.danielhw99.auth.security.AuthOpenApiConfiguration;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.junit.jupiter.api.Test;
class SwaggerDomainTest {
    @Test void customizersAlsoApplyToGroupedDocuments() {
        assertThat(new AuthOpenApiConfiguration().sessionAndCsrfDocumentation()).isInstanceOf(GlobalOpenApiCustomizer.class);
        assertThat(new OpenApiConfiguration().closedDtoSchemas()).isInstanceOf(GlobalOpenApiCustomizer.class);
    }
    @Test void fourIndependentDomainGroupsAreDeclared() throws Exception {
        var methods = Arrays.stream(OpenApiConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == GroupedOpenApi.class).toList();
        assertThat(methods).hasSize(4);
        var names = new java.util.ArrayList<String>();
        for (var method : methods) names.add(((GroupedOpenApi) method.invoke(new OpenApiConfiguration())).getGroup());
        assertThat(names).containsExactlyInAnyOrder("auth", "categories", "budgets", "transactions");
    }
}
