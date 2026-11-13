package com.danielhw99.auth;
import java.time.Instant;
import com.danielhw99.auth.security.SessionWindow;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SessionWindowTest {
 @Test void idleExpiresAtThirtyMinutesAndAbsoluteAtTwelveHours() {
  var login=Instant.parse("2026-11-13T00:00:00Z");
  assertThat(SessionWindow.expired(login,login,login.plusSeconds(1799))).isFalse();
  assertThat(SessionWindow.expired(login,login,login.plusSeconds(1800))).isTrue();
  assertThat(SessionWindow.expired(login,login.plusSeconds(43100),login.plusSeconds(43200))).isTrue();
  assertThat(SessionWindow.expired(login,login.plusSeconds(43100),login.plusSeconds(43199))).isFalse();
 }
 @Test void missingOrFutureTimestampsFailClosed() {
  var now=Instant.now();
  assertThat(SessionWindow.expired(null,now,now)).isTrue();
  assertThat(SessionWindow.expired(now,null,now)).isTrue();
  assertThat(SessionWindow.expired(now.plusSeconds(1),now,now)).isTrue();
 }
}
