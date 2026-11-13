package com.danielhw99.category;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.auth.security.SecurityConfiguration;
import com.danielhw99.auth.security.SecurityErrorWriter;
import com.danielhw99.auth.security.SessionExpiryFilter;
import com.danielhw99.category.api.CategoryController;
import com.danielhw99.category.api.CategoryInput;
import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryResponse;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.service.CategoryService;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.config.ApiJsonConfiguration;
import com.danielhw99.common.web.TraceIdFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfiguration.class, SecurityErrorWriter.class, ApiJsonConfiguration.class, TraceIdFilter.class})
class CategoryMvcTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    CategoryService categories;
    @MockitoBean
    AuthRateLimiter limiter;

    @Test
    void authenticatedGetReturnsWrappedOwnCategoriesWithoutCache() throws Exception {
        when(categories.list(7L, CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE))
                .thenReturn(List.of(new CategoryResponse(new ApiId(101L), "식비",
                        CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE)));

        mvc.perform(get("/api/v1/categories").session(session(7L))
                        .param("scope", "mainTransaction").param("direction", "expense"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].id").value("101"))
                .andExpect(jsonPath("$.items[0].scope").value("mainTransaction"))
                .andExpect(jsonPath("$.items[0].direction").value("expense"));
    }

    @Test
    void emptyListRetainsItemsWrapper() throws Exception {
        when(categories.list(7L, null, null)).thenReturn(List.of());
        mvc.perform(get("/api/v1/categories").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void invalidEnumReturnsValidationError() throws Exception {
        mvc.perform(get("/api/v1/categories").session(session(7L)).param("scope", "FIXED_BUDGET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void postCreatesWith200AndNoStore() throws Exception {
        when(categories.createOrReuse(org.mockito.ArgumentMatchers.eq(7L), any(CategoryInput.class)))
                .thenReturn(new CategoryResponse(new ApiId(103L), "Housing", CategoryScopeValue.FIXED_BUDGET, null));
        mvc.perform(post("/api/v1/categories").session(session(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Housing\",\"scope\":\"fixedBudget\",\"direction\":null}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value("103"))
                .andExpect(jsonPath("$.direction").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void invalidPostBodyIsRejected() throws Exception {
        for (String body : List.of(
                "{\"name\":\"Rent\",\"scope\":\"fixedBudget\"}",
                "{\"name\":\"Rent\",\"scope\":\"fixedBudget\",\"direction\":null,\"extra\":1}",
                "{\"name\":\"\",\"scope\":\"fixedBudget\",\"direction\":null}",
                "{\"name\":\"Rent\",\"scope\":\"bad\",\"direction\":null}")) {
            mvc.perform(post("/api/v1/categories").session(session(7L))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void postRequiresSessionAndCsrf() throws Exception {
        String body = "{\"name\":\"Rent\",\"scope\":\"fixedBudget\",\"direction\":null}";
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categories").session(session(7L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession session(Long userId) {
        var principal = new AuthPrincipal(userId, "user@example.com", "User");
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute(SessionExpiryFilter.LOGIN_AT, Instant.now());
        session.setAttribute(SessionExpiryFilter.LAST_SEEN, Instant.now());
        return session;
    }
}
