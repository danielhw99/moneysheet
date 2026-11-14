package com.danielhw99.payment;

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
import com.danielhw99.payment.api.PaymentController;
import com.danielhw99.payment.api.PaymentResponse;
import com.danielhw99.payment.service.PaymentService;
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

@WebMvcTest(PaymentController.class)
@Import({SecurityConfiguration.class, SecurityErrorWriter.class, ApiJsonConfiguration.class, TraceIdFilter.class})
class PaymentMvcTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    PaymentService service;
    @MockitoBean
    AuthRateLimiter limiter;

    private final String path = "/api/v1/months/2026-11/fixed-payments";
    private final String valid = "{\"date\":\"2026-11-01\",\"time\":\"12:30\",\"title\":\"점심\",\"amount\":9000,\"budgetItemId\":\"51\"}";
    private PaymentResponse response() {
        return new PaymentResponse(new ApiId(91L),java.time.LocalDate.of(2026,11,1),java.time.LocalTime.of(12,30),"점심",9000L,new ApiId(51L));
    }
    @Test void createAndGetReturnDirectDtoAndNoStore() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.eq(7L),any(),any())).thenReturn(response());
        mvc.perform(post(path).session(session(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.id").value("91")).andExpect(jsonPath("$.budgetItemId").value("51"))
                .andExpect(jsonPath("$.time").value("12:30"));
        when(service.get(7L,java.time.YearMonth.of(2026,11),91L)).thenReturn(response());
        mvc.perform(get(path+"/91").session(session(7L))).andExpect(status().isOk()).andExpect(jsonPath("$.budgetItemId").value("51"));
    }
    @Test void strictInputsAndMalformedPathsReturnValidation() throws Exception {
        for(String body:List.of(valid.replace(",\"budgetItemId\":\"51\"",""),valid.replace("9000","0"),valid.replace("9000","\"9000\""),valid.replace("12:30","12:30:00"),valid.replace("\"51\"","51"))) {
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
