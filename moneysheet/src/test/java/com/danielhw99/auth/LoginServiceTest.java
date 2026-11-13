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

 @Test void atMostFiveConcurrentPasswordChecksAreAdmitted() throws Exception {
  var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
  when(encoder.encode(anyString())).thenReturn("dummy");
  when(users.findByEmailKey(anyString())).thenReturn(Optional.empty());
  var release=new java.util.concurrent.CountDownLatch(1);
  var rejected=new java.util.concurrent.CountDownLatch(5);
  var entered=new java.util.concurrent.CountDownLatch(5);
  var checked=new java.util.concurrent.atomic.AtomicInteger();
  when(encoder.matches(anyString(),eq("dummy"))).thenAnswer(call->{
   checked.incrementAndGet();entered.countDown();
   if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
   return false;
  });
  var service=new LoginService(users,encoder,new com.danielhw99.auth.security.AuthRateLimiter(java.time.Clock.systemUTC()));
  try(var executor=java.util.concurrent.Executors.newFixedThreadPool(10)) {
   var futures=new java.util.ArrayList<java.util.concurrent.Future<com.danielhw99.common.error.ErrorCode>>();
   try {
    for(int i=0;i<10;i++)futures.add(executor.submit(()->{
     try { service.authenticate(new LoginInput("same@example.com","wrong"));throw new AssertionError("unexpected success"); }
     catch(ApiException error) {
      if(error.getErrorCode()==com.danielhw99.common.error.ErrorCode.RATE_LIMITED) rejected.countDown();
      return error.getErrorCode();
     }
    }));
    assertThat(rejected.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(checked.get()).isEqualTo(5);
   } finally { release.countDown(); }
   int invalid=0,limited=0;
   for(var future:futures) {
    var code=future.get(10,java.util.concurrent.TimeUnit.SECONDS);
    if(code==com.danielhw99.common.error.ErrorCode.INVALID_CREDENTIALS) invalid++;
    if(code==com.danielhw99.common.error.ErrorCode.RATE_LIMITED) limited++;
   }
   assertThat(invalid).isEqualTo(5);assertThat(limited).isEqualTo(5);
   assertThatThrownBy(()->service.authenticate(new LoginInput("same@example.com","wrong")))
    .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(com.danielhw99.common.error.ErrorCode.RATE_LIMITED));
   assertThat(checked.get()).isEqualTo(5);
  }
 }
 @Test void encoderAndRepositoryExceptionsDoNotConsumeAttemptSlots() {
  var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
  when(encoder.encode(anyString())).thenReturn("dummy");
  when(users.findByEmailKey(anyString())).thenThrow(new IllegalStateException("repository failure"));
  var service=new LoginService(users,encoder,new com.danielhw99.auth.security.AuthRateLimiter(java.time.Clock.systemUTC()));
  for(int i=0;i<6;i++)assertThatThrownBy(()->service.authenticate(new LoginInput("same@example.com","wrong"))).isInstanceOf(IllegalStateException.class);
  doReturn(Optional.empty()).when(users).findByEmailKey(anyString());
  when(encoder.matches(anyString(),eq("dummy"))).thenThrow(new IllegalStateException("encoder failure"));
  for(int i=0;i<6;i++)assertThatThrownBy(()->service.authenticate(new LoginInput("same@example.com","wrong"))).isInstanceOf(IllegalStateException.class);
  doReturn(false).when(encoder).matches(anyString(),eq("dummy"));
  for(int i=0;i<5;i++)assertThatThrownBy(()->service.authenticate(new LoginInput("same@example.com","wrong")))
    .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(com.danielhw99.common.error.ErrorCode.INVALID_CREDENTIALS));
 }
}
