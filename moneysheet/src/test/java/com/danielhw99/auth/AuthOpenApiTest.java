package com.danielhw99.auth;
import com.danielhw99.auth.security.AuthOpenApiConfiguration;
import com.danielhw99.common.config.OpenApiConfiguration;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AuthOpenApiTest {
 @Test void cookieAndCsrfAreRequiredTogetherForLogout() {
  var api=new OpenApiConfiguration().moneySheetOpenApi()
    .path("/api/v1/auth/logout",new PathItem().post(new Operation()))
    .path("/api/v1/auth/login",new PathItem().post(new Operation()))
    .path("/api/v1/me",new PathItem().get(new Operation()));
  new AuthOpenApiConfiguration().sessionAndCsrfDocumentation().customise(api);
  assertThat(api.getPaths().get("/api/v1/auth/logout").getPost().getSecurity()).hasSize(1);
  assertThat(api.getPaths().get("/api/v1/auth/logout").getPost().getSecurity().getFirst()).containsKeys("csrfToken","sessionCookie");
  assertThat(api.getPaths().get("/api/v1/auth/login").getPost().getSecurity().getFirst()).containsOnlyKeys("csrfToken");
  assertThat(api.getPaths().get("/api/v1/me").getGet().getSecurity().getFirst()).containsOnlyKeys("sessionCookie");
 }
}
