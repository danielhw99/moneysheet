package com.danielhw99.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.danielhw99.auth.security.AuthOpenApiConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

class CategoryOpenApiTest {
    @Test
    void categoryOperationsDocumentSessionAndPostCsrfHeader() {
        var get = new Operation();
        var post = new Operation();
        var api = new OpenAPI().components(new Components()).paths(new Paths()
                .addPathItem("/api/v1/categories", new PathItem().get(get).post(post)));

        new AuthOpenApiConfiguration().sessionAndCsrfDocumentation().customise(api);

        assertThat(get.getSecurity()).anySatisfy(requirement ->
                assertThat(requirement).containsKey("sessionCookie"));
        assertThat(post.getSecurity()).anySatisfy(requirement ->
                assertThat(requirement).containsKeys("sessionCookie", "csrfToken"));
        assertThat(post.getParameters()).anySatisfy(parameter -> {
            assertThat(parameter.getName()).isEqualTo("X-CSRF-TOKEN");
            assertThat(parameter.getIn()).isEqualTo("header");
            assertThat(parameter.getRequired()).isTrue();
        });
    }
}
