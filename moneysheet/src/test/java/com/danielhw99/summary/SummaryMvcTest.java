package com.danielhw99.summary;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.auth.security.SecurityConfiguration;
import com.danielhw99.auth.security.SecurityErrorWriter;
import com.danielhw99.auth.security.SessionExpiryFilter;
import com.danielhw99.common.config.ApiJsonConfiguration;
import com.danielhw99.common.web.TraceIdFilter;
import com.danielhw99.summary.api.SummaryController;
import com.danielhw99.summary.api.SummaryDtos;
import com.danielhw99.summary.service.SummaryService;
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

@WebMvcTest(SummaryController.class)
@Import({SecurityConfiguration.class,SecurityErrorWriter.class,ApiJsonConfiguration.class,TraceIdFilter.class})
class SummaryMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean SummaryService service;
    @MockitoBean AuthRateLimiter limiter;
    final LocalDate date=LocalDate.of(2026,11,1);
    final YearMonth month=YearMonth.from(date);
    @Test void allRoutesReturnDirectDtosAndNoStore() throws Exception {
        when(service.daily(7L,date)).thenReturn(new SummaryDtos.Daily(date,110000,2000,11000,0,11000,108000,9000,0,List.of(),List.of(),List.of()));
        when(service.monthly(7L,month)).thenReturn(new SummaryDtos.Monthly(month,100,20,30,40,70,90,10));
        when(service.home(7L,month)).thenReturn(new SummaryDtos.Home(month,date,List.of(new SummaryDtos.CalendarDay(date,List.of(new SummaryDtos.AmountEntry("free","여유비",2000),new SummaryDtos.MemoEntry("memo","미완료 메모",1)))),90,2000));
        mvc.perform(get("/api/v1/summaries/daily").param("date",date.toString()).session(session())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.freeBalance").value(2000)).andExpect(jsonPath("$.transactions").isEmpty());
        mvc.perform(get("/api/v1/summaries/monthly").param("month",month.toString()).session(session())).andExpect(status().isOk()).andExpect(jsonPath("$.month").value("2026-11")).andExpect(jsonPath("$.net").value(10));
        mvc.perform(get("/api/v1/summaries/home").param("month",month.toString()).session(session())).andExpect(status().isOk()).andExpect(jsonPath("$.days[0].entries[0].amount").value(2000)).andExpect(jsonPath("$.days[0].entries[0].count").doesNotExist()).andExpect(jsonPath("$.days[0].entries[1].count").value(1)).andExpect(jsonPath("$.days[0].entries[1].amount").doesNotExist());
    }
    @Test void missingAndStrictlyInvalidDatesAreRejectedBeforeServiceCalls() throws Exception {
        mvc.perform(get("/api/v1/summaries/daily").session(session())).andExpect(status().isBadRequest());
        for(String date:List.of("2026-11-31","2026-1-01","2026-11-01T00:00:00")) mvc.perform(get("/api/v1/summaries/daily").param("date",date).session(session())).andExpect(status().isBadRequest());
        for(String route:List.of("monthly","home")) {
            mvc.perform(get("/api/v1/summaries/"+route).session(session())).andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/summaries/"+route).param("month","2026-13").session(session())).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void allSummaryRoutesRequireAuthentication() throws Exception {
        for(String route:List.of("daily?date=2026-11-01","monthly?month=2026-11","home?month=2026-11")) mvc.perform(get("/api/v1/summaries/"+route)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    MockHttpSession session() {
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AuthPrincipal(7L,"user@example.com","User"),null,List.of()));
        var session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);session.setAttribute(SessionExpiryFilter.LOGIN_AT,Instant.now());session.setAttribute(SessionExpiryFilter.LAST_SEEN,Instant.now());return session;
    }
}
