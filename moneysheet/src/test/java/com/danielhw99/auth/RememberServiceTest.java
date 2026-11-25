package com.danielhw99.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import com.danielhw99.auth.remember.RememberProof;
import com.danielhw99.auth.remember.RememberLoginToken;
import com.danielhw99.auth.remember.RememberLoginService;
import com.danielhw99.auth.remember.RememberTokenRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
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

 @Test void logoutRevokesBoundFamilyEvenWhenPresentedCookieIsMissing(){
  var u=user();var proof=RememberProof.create();var row=new RememberLoginToken(u,proof,now);
  when(tokens.findLocked(any())).thenReturn(Optional.of(row));
  service(now).logout(AuthPrincipal.from(u).withRememberSelector(proof.selectorText()),null);
  assertThat(row.active(now,0)).isFalse();
 }
 @Test void guessedSelectorCannotRevokeAnotherBrowsersProof(){
  user();var proof=RememberProof.create();var other=RememberProof.create();var row=new RememberLoginToken(user(),proof,now);
  when(tokens.ownerId(any())).thenReturn(Optional.of(1L));when(tokens.findLocked(any())).thenReturn(Optional.of(row));
  service(now).logout(null,other.cookie());assertThat(row.active(now,0)).isTrue();
 }
 @Test void rememberBoundSessionRejectsRevocationExpiryVersionAndOwnerMismatch(){
  var u=user();when(users.findById(1L)).thenReturn(Optional.of(u));
  var proof=RememberProof.create();var row=new RememberLoginToken(u,proof,now);
  when(tokens.findById(any())).thenReturn(Optional.of(row));
  var principal=AuthPrincipal.from(u).withRememberSelector(proof.selectorText());
  assertThat(service(now).sessionValid(principal)).isTrue();
  assertThat(service(row.expiresAt()).sessionValid(principal)).isFalse();
  u.changePassword("new");assertThat(service(now).sessionValid(principal)).isFalse();
  row.revoke(now);assertThat(service(now).sessionValid(principal)).isFalse();
  assertThat(service(now).sessionValid(new AuthPrincipal(9L,"x","x",0,null,proof.selectorText()))).isFalse();
 }

 @Test void crossAccountCookieUsesSortedOwnerLocksAndClearsOnlyVerifiedProof(){
  var target=user();var previousUser=new User("b","b","B","hash","v1",LocalDateTime.now());ReflectionTestUtils.setField(previousUser,"id",2L);
  var previous=RememberProof.create();var row=new RememberLoginToken(previousUser,previous,now);
  when(tokens.ownerId(any())).thenReturn(Optional.of(2L));when(users.findWithLockingById(2L)).thenReturn(Optional.of(previousUser));when(tokens.findLocked(any())).thenReturn(Optional.of(row));
  service(now).login(AuthPrincipal.from(target),false,previous.cookie());
  var order=inOrder(users);order.verify(users).findWithLockingById(1L);order.verify(users).findWithLockingById(2L);
  assertThat(row.active(now,0)).isFalse();verify(tokens,never()).saveAndFlush(any());
 }
}
