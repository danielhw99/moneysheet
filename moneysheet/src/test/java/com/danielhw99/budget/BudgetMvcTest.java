package com.danielhw99.budget;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.auth.security.SecurityConfiguration;
import com.danielhw99.auth.security.SecurityErrorWriter;
import com.danielhw99.auth.security.SessionExpiryFilter;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.budget.api.BudgetController;
import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.service.BudgetService;
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

@WebMvcTest(BudgetController.class)
@Import({SecurityConfiguration.class, SecurityErrorWriter.class, ApiJsonConfiguration.class, TraceIdFilter.class})
class BudgetMvcTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    BudgetService budgets;
    @MockitoBean
    AuthRateLimiter limiter;

    @Test void setupRejectsMissingIdBeforeCallingService() throws Exception {
        mvc.perform(put("/api/v1/months/2026-11/budget-setup")
                .session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"name\":\"Rent\",\"categoryId\":\"1\",\"amount\":100}],\"food\":{\"weekday\":1,\"saturday\":1,\"sunday\":1}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        verifyNoInteractions(budgets);
    }

    @Test void setupAcceptsExplicitNullIdForNewItem() throws Exception {
        mvc.perform(put("/api/v1/months/2026-11/budget-setup")
                .session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":null,\"name\":\"Rent\",\"categoryId\":\"1\",\"amount\":100}],\"food\":{\"weekday\":1,\"saturday\":1,\"sunday\":1}}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(budgets).setup(eq(7L),
                eq(YearMonth.of(2026, 11)),
                argThat(input -> input.items().get(0).id() == null));
    }

    @Test void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/months/2026-11/budget"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        for (String path : List.of("budget-setup", "food-budget", "budget-items/1")) {
            mvc.perform(put("/api/v1/months/2026-11/" + path)
                    .session(session(7L)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/months/2026-11/budget-preparation").session(session(7L)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/months/2026-11/budget-items").session(session(7L)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/months/2026-11/budget-items/1")
                .session(session(7L))).andExpect(status().isForbidden());
        verifyNoInteractions(budgets);
    }

    @Test void itemCreationSerializesStringIdAndDeletionHasNoBody() throws Exception {
        when(budgets.createItem(eq(7L), any(), any()))
                .thenReturn(new BudgetDtos.Item(new ApiId(101L), "Rent", new ApiId(1L), 100L));
        mvc.perform(post("/api/v1/months/2026-11/budget-items").session(session(7L))
                .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rent\",\"categoryId\":\"1\",\"amount\":100}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value("101")).andExpect(jsonPath("$.kind").doesNotExist());
        mvc.perform(delete("/api/v1/months/2026-11/budget-items/101")
                .session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
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
