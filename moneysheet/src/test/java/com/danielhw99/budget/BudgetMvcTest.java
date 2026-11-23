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
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.danielhw99.idempotency.IdempotentWrites writes;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.danielhw99.user.repository.UserRepository sessionUsers;
 @org.junit.jupiter.api.BeforeEach void activeSessionUser(){
  org.mockito.Mockito.when(sessionUsers.findById(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call->{
   var user=org.mockito.Mockito.mock(com.danielhw99.user.entity.User.class);
   org.mockito.Mockito.when(user.getId()).thenReturn(call.getArgument(0));
   return java.util.Optional.of(user);
  });
 }

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
                .content("{\"items\":[{\"name\":\"Rent\",\"categoryId\":\"1\",\"amount\":100}],\"food\":{\"paymentMode\":\"threeInstallments\",\"weekday\":1,\"saturday\":1,\"sunday\":1}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        verifyNoInteractions(budgets);
    }

    @Test void setupAcceptsExplicitNullIdForNewItem() throws Exception {
        mvc.perform(put("/api/v1/months/2026-11/budget-setup")
                .session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":null,\"name\":\"Rent\",\"categoryId\":\"1\",\"amount\":100}],\"food\":{\"paymentMode\":\"threeInstallments\",\"weekday\":1,\"saturday\":1,\"sunday\":1}}"))
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

    @Test void foodInputUsesStrictBooleanAndPaymentModeAndRequiresExplicitChoice() throws Exception {
        for(String input:List.of(
            "{\"enabled\":\"false\",\"paymentMode\":\"threeInstallments\",\"weekday\":null,\"saturday\":null,\"sunday\":null}",
            "{\"enabled\":false,\"paymentMode\":\"MONTH_START\",\"weekday\":null,\"saturday\":null,\"sunday\":null}",
            "{\"paymentMode\":\"threeInstallments\",\"weekday\":null,\"saturday\":null,\"sunday\":null}")) {
            mvc.perform(put("/api/v1/months/2026-11/food-budget").session(session(7L))
                .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        verifyNoInteractions(budgets);
    }
    @Test void foodModeUsesTheSameLowerCamelCaseValueInRequestsAndResponses() throws Exception {
        var mode=com.danielhw99.budget.entity.FoodPaymentMode.MONTH_START;
        when(budgets.updateFood(eq(7L),eq(YearMonth.of(2026,11)),any()))
            .thenReturn(new BudgetDtos.Summary("2026-11","ready",List.of(),new BudgetDtos.Food(mode,1L,1L,1L),0L,new BudgetDtos.FoodSummary(30L,List.of(30L,0L,0L))));
        mvc.perform(put("/api/v1/months/2026-11/food-budget").session(session(7L))
            .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"paymentMode\":\"monthStart\",\"weekday\":1,\"saturday\":1,\"sunday\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.food.enabled").doesNotExist())
            .andExpect(jsonPath("$.food.paymentMode").value("monthStart"));
        verify(budgets).updateFood(eq(7L),eq(YearMonth.of(2026,11)),argThat(food->food.paymentMode()==mode&&food.weekday()==1L));
    }
    @Test void foodAmountTypesAndRangesAreRejectedBeforeService() throws Exception {
        for(String value:List.of("null","0","-1","1.5","true","\"11000\"","10000000000")) {
            String food="{\"paymentMode\":\"monthStart\",\"weekday\":"+value+",\"saturday\":1,\"sunday\":1}";
            for(String endpoint:List.of("food-budget","budget-setup")) {
                String body=endpoint.equals("food-budget")?food:"{\"items\":[],\"food\":"+food+"}";
                mvc.perform(put("/api/v1/months/2026-11/"+endpoint).session(session(7L))
                    .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
            }
        }
        verifyNoInteractions(budgets);
    }

}
