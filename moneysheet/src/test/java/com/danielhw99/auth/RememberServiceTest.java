package com.danielhw99.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.Optional;
import com.danielhw99.auth.remember.*;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.auth.security.*;
import com.danielhw99.common.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class RememberServiceTest {
 final Instant now=Instant.parse("2026-11-25T00:00:00Z");
 final UserRepository users=mock(UserRepository.class);final RememberTokenRepository tokens=mock(RememberTokenRepository.class);
 RememberLoginService service(Instant time){return new RememberLoginService(users,tokens,Clock.fixed(time,ZoneOffset.UTC));}
 User user(){var u=new User("a@example.com","a@example.com","A","hash","v1",LocalDateTime.now());u.verifyEmail();ReflectionTestUtils.setField(u,"id",1L);when(users.findWithLockingById(1L)).thenReturn(Optional.of(u));return u;}
 @Test void restoresAndRotatesWithoutExtendingThirtyDays(){
  var u=user();var proof=RememberProof.create();var row=new RememberLoginToken(u,proof,now);
  when(tokens.ownerId(any())).thenReturn(Optional.of(1L));when(tokens.findLocked(any())).thenReturn(Optional.of(row));
  var result=service(now.plusSeconds(100)).restore(proof.cookie());assertThat(result.cookieValue()).isNotEqualTo(proof.cookie());assertThat(result.principal().rememberSelector()).isEqualTo(proof.selectorText());assertThat(result.expiresAt()).isEqualTo(now.plusSeconds(30L*86400));assertThat(row.matches(RememberProof.parse(result.cookieValue()).orElseThrow())).isTrue();
  assertThatThrownBy(()->service(now.plusSeconds(109)).restore(proof.cookie())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
  assertThatThrownBy(()->service(now.plusSeconds(110)).restore(proof.cookie())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
 }
 @Test void rejectsMissingMalformedRevokedAndExpiredProof(){
  var u=user();var proof=RememberProof.create();var row=new RememberLoginToken(u,proof,now);when(tokens.ownerId(any())).thenReturn(Optional.of(1L));when(tokens.findLocked(any())).thenReturn(Optional.of(row));
  assertThatThrownBy(()->service(now).restore(null)).isInstanceOf(ApiException.class);assertThatThrownBy(()->service(now).restore("bad")).isInstanceOf(ApiException.class);
  assertThatThrownBy(()->service(row.expiresAt()).restore(proof.cookie())).isInstanceOf(ApiException.class);row.revoke(now);assertThatThrownBy(()->service(now).restore(proof.cookie())).isInstanceOf(ApiException.class);
 }
 @Test void requestLimitsAreIndependent(){var limiter=new AuthRateLimiter(Clock.fixed(now,ZoneOffset.UTC));for(int i=0;i<30;i++)limiter.restoreRequest("ip");assertThatThrownBy(()->limiter.restoreRequest("ip")).isInstanceOf(ApiException.class);assertThatCode(()->limiter.request("ip",false)).doesNotThrowAnyException();}
 @Test void omittingRememberMeClearsOnlyVerifiedOldProofAfterSuccessfulAuthentication(){var u=user();var result=service(now).login(AuthPrincipal.from(u),false,null);assertThat(result.cookieValue()).isNull();verify(tokens,never()).saveAndFlush(any());}
}
