package com.danielhw99.auth;
import java.time.*;
import com.danielhw99.auth.remember.*;
import com.danielhw99.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
class RememberProofTest {
 @Test void proofsAreRandomCanonicalAndRedacted(){
  var a=RememberProof.create();var b=a.next();assertThat(a.selector()).hasSize(16);assertThat(a.hash()).hasSize(32);assertThat(a.cookie()).hasSize(66);assertThat(RememberProof.parse(a.cookie())).isPresent();assertThat(b.selectorText()).isEqualTo(a.selectorText());assertThat(b.hash()).isNotEqualTo(a.hash());assertThat(a.toString()).doesNotContain(a.cookie());
  for(String invalid:new String[]{"","bad","A".repeat(22)+"."+"B".repeat(43)})assertThat(RememberProof.parse(invalid)).isEmpty();
 }
 @Test void rotationPreservesAbsoluteExpiryAndOnlyOnePreviousGeneration(){
  var now=Instant.parse("2026-11-25T00:00:00Z");var user=new User("a@example.com","a@example.com","A","hash","v1",LocalDateTime.now());var first=RememberProof.create();var token=new RememberLoginToken(user,first,now);var next=first.next();token.rotate(next,now.plusSeconds(100));assertThat(token.expiresAt()).isEqualTo(now.plusSeconds(30L*86400));assertThat(token.matches(next)).isTrue();assertThat(token.previousMatches(first)).isTrue();assertThat(token.previousWithinGrace(now.plusSeconds(109))).isTrue();assertThat(token.previousWithinGrace(now.plusSeconds(110))).isFalse();assertThat(token.active(token.expiresAt(),0)).isFalse();token.revoke(now);assertThat(token.active(now,0)).isFalse();
 }
 @Test void cookieIsHttpOnlyAndHasRemainingExpiry(){
  var now=Instant.parse("2026-11-25T00:00:00Z");var response=new MockHttpServletResponse();new RememberCookies(true,Clock.fixed(now,ZoneOffset.UTC)).write(response,new RememberGrant(null,"opaque",now.plusSeconds(123)));assertThat(response.getHeader("Set-Cookie")).contains("MS_REMEMBER=opaque","Max-Age=123","Secure","HttpOnly","SameSite=Lax","Path=/");
 }
}
