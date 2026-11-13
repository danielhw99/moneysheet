package com.danielhw99.auth;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.danielhw99.auth.dto.SignupInput;
import com.danielhw99.auth.service.PasswordPolicy;
import com.danielhw99.auth.service.SignupService;
import com.danielhw99.auth.security.SecurityConfiguration;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SignupServiceTest {
 @Test void normalizesAndHashesWithoutTrimmingPassword() {
  var users=mock(UserRepository.class);var encoder=new SecurityConfiguration().passwordEncoder();
  when(users.saveAndFlush(any())).thenAnswer(call->{User u=call.getArgument(0);ReflectionTestUtils.setField(u,"id",1L);return u;});
  var service=new SignupService(users,encoder,new PasswordPolicy(),Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"),ZoneOffset.UTC),"v1");
  var input=new SignupInput(" USER@Example.COM "," Nick ","  secure password for test  ","v1",true);
  var profile=service.signup(input);
  assertThat(profile.email()).isEqualTo("user@example.com");assertThat(profile.nickname()).isEqualTo("Nick");
  var saved=org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(saved.capture());
  assertThat(saved.getValue().getPasswordHash()).startsWith("{argon2id}");
  assertThat(encoder.matches(input.password(),saved.getValue().getPasswordHash())).isTrue();
  assertThat(encoder.matches(input.password().strip(),saved.getValue().getPasswordHash())).isFalse();
  assertThat(saved.getValue().getPrivacyAgreedDate()).isEqualTo(LocalDateTime.parse("2026-11-13T00:00:00"));
 }
 @Test void rejectsDuplicateAndWrongConsentBeforeInsert() {
  var users=mock(UserRepository.class);var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);
  var service=new SignupService(users,encoder,new PasswordPolicy(),Clock.systemUTC(),"v1");
  when(users.existsByEmailKey("user@example.com")).thenReturn(true);
  assertThatThrownBy(()->service.signup(new SignupInput("user@example.com","n","valid long password!","v1",true)))
    .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
  assertThatThrownBy(()->service.signup(new SignupInput("user@example.com","n","valid long password!","v1",false))).isInstanceOf(ApiException.class);
  verify(users,never()).saveAndFlush(any());verifyNoInteractions(encoder);
 }
}
