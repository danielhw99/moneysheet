package com.danielhw99.auth.security;

import java.time.Clock;
import java.util.Map;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods=false)
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean public Clock authClock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("argon2id", Map.of("argon2id",
                new Argon2PasswordEncoder(16, 32, 1, 19456, 2)));
    }
    @Bean public HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }
    @Bean public SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorWriter errors,
            Environment environment, HttpSessionSecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrfTokens, Clock clock) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(contexts))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request,response,error) -> errors.write(request,response,ErrorCode.UNAUTHENTICATED))
                    .accessDeniedHandler((request,response,error) -> errors.write(request,response,
                            error instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/signup", "/api/v1/auth/login").permitAll();
                    if (environment.matchesProfiles("local")) {
                        authorize.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                });
        http.addFilterAfter(new SessionExpiryFilter(clock), SecurityContextHolderFilter.class);
        return http.build();
    }
}
