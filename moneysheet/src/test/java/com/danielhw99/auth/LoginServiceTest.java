package com.danielhw99.auth;
import java.util.Optional;
import com.danielhw99.auth.dto.LoginInput;
import com.danielhw99.auth.service.LoginService;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class LoginServiceTest {
 @Test void missingUserStillVerifiesDummyHash() {
  var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
  when(encoder.encode(anyString())).thenReturn("dummy");
  when(users.findByEmailKey(anyString())).thenReturn(Optional.empty());
  var service=new LoginService(users,encoder,new com.danielhw99.auth.security.AuthRateLimiter(java.time.Clock.systemUTC()));
  assertThatThrownBy(()->service.authenticate(new LoginInput("none@example.com","wrong"))).isInstanceOf(ApiException.class);
  verify(encoder).matches("wrong","dummy");
 }
 @Test void doesNotApplySignupMinimumLengthToLogin() {
  var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
  var user=new User("u@example.com","u@example.com","n","hash","v1",java.time.LocalDateTime.now());
  org.springframework.test.util.ReflectionTestUtils.setField(user,"id",3L);
  when(users.findByEmailKey("u@example.com")).thenReturn(Optional.of(user));when(encoder.matches("short","hash")).thenReturn(true);
  var service=new LoginService(users,encoder,new com.danielhw99.auth.security.AuthRateLimiter(java.time.Clock.systemUTC()));
  assertThat(service.authenticate(new LoginInput(" U@EXAMPLE.COM ","short")).userId()).isEqualTo(3L);
  assertThatThrownBy(()->service.authenticate(new LoginInput("u@example.com","x".repeat(129)))).isInstanceOf(ApiException.class);
 }
}
