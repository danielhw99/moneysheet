package com.danielhw99.transaction;

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
import com.danielhw99.common.api.ApiId;
import com.danielhw99.transaction.api.TransactionController;
import com.danielhw99.transaction.api.TransactionResponse;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.service.TransactionService;
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

@WebMvcTest(TransactionController.class)
@Import({SecurityConfiguration.class, SecurityErrorWriter.class, ApiJsonConfiguration.class, TraceIdFilter.class})
class TransactionMvcTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    TransactionService service;
    @MockitoBean
    com.danielhw99.transaction.service.TransactionReadService reads;
    @MockitoBean
    AuthRateLimiter limiter;

    private final String path = "/api/v1/months/2026-11/transactions";
    private final String valid = "{\"date\":\"2026-11-01\",\"time\":\"12:30\",\"title\":\"점심\",\"amount\":9000,\"direction\":\"expense\",\"categoryId\":null}";
    private TransactionResponse response() {
        return new TransactionResponse(new ApiId(91L),java.time.LocalDate.of(2026,11,1),java.time.LocalTime.of(12,30),"점심",9000L,TransactionDirectionValue.EXPENSE,null);
    }
    @Test void createAndGetReturnDirectDtoAndNoStore() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.eq(7L),any(),any())).thenReturn(response());
        mvc.perform(post(path).session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.id").value("91")).andExpect(jsonPath("$.direction").value("expense"))
                .andExpect(jsonPath("$.time").value("12:30"));
        when(service.get(7L,java.time.YearMonth.of(2026,11),91L)).thenReturn(response());
        mvc.perform(get(path+"/91").session(session(7L))).andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void strictInputsAndMalformedPathsReturnValidation() throws Exception {
        for(String body:List.of(valid.replace(",\"categoryId\":null",""),valid.replace("9000","0"),valid.replace("9000","\"9000\""),valid.replace("12:30","12:30:00"),valid.replace("expense","EXPENSE"))) {
            mvc.perform(post(path).session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        mvc.perform(get(path+"/abc").session(session(7L))).andExpect(status().isBadRequest());
        mvc.perform(get(path.replace("2026-11","2026-13")+"/1").session(session(7L))).andExpect(status().isBadRequest());
    }
    @Test void securityAndEmptyDeleteContractArePreserved() throws Exception {
        mvc.perform(get(path+"/1")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).session(session(7L)).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path+"/1").session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }
    @Test void listBindsDateDirectionAndLimitStrictly() throws Exception {
        when(reads.list(7L,java.time.YearMonth.of(2026,11),java.time.LocalDate.of(2026,11,1),TransactionDirectionValue.INCOME,null,1))
                .thenReturn(new com.danielhw99.transaction.api.TransactionList(List.of(),null,0,20000,20000));
        mvc.perform(get(path).session(session(7L)).param("date","2026-11-01").param("direction","income").param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows").isEmpty()).andExpect(jsonPath("$.incomeTotal").value(20000));
        mvc.perform(get(path).session(session(7L)).param("direction","INCOME")).andExpect(status().isBadRequest());
        mvc.perform(get(path).session(session(7L)).param("date","2026-11-31")).andExpect(status().isBadRequest());
        mvc.perform(get(path).session(session(7L)).param("limit","101")).andExpect(status().isBadRequest());
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
