package com.danielhw99.auth;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AuthRateLimiterTest {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-11-13T00:00:00Z");
  public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
 }
 @Test void accountBlocksAfterFiveFailuresAndReopensAfterFifteenMinutes() {
  var clock=new MutableClock();var limiter=new AuthRateLimiter(clock);
  for(int i=0;i<5;i++){limiter.checkAccount("a");limiter.failed("a");}
  assertThatThrownBy(()->limiter.checkAccount("a")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getRetryAfterSeconds()).isEqualTo(900));
  clock.now=clock.now.plusSeconds(900);assertThatCode(()->limiter.checkAccount("a")).doesNotThrowAnyException();
 }
 @Test void ipLimitsAreSeparateAndSuccessfulLoginClearsFailures() {
  var clock=new MutableClock();var limiter=new AuthRateLimiter(clock);
  for(int i=0;i<30;i++)limiter.request("ip",false);
  assertThatThrownBy(()->limiter.request("ip",false)).isInstanceOf(ApiException.class);
  assertThatCode(()->limiter.request("other",false)).doesNotThrowAnyException();
  for(int i=0;i<10;i++)limiter.request("ip",true);
  assertThatThrownBy(()->limiter.request("ip",true)).isInstanceOf(ApiException.class);
  for(int i=0;i<4;i++)limiter.failed("a");limiter.succeeded("a");limiter.failed("a");
  assertThatCode(()->limiter.checkAccount("a")).doesNotThrowAnyException();
  clock.now=clock.now.plusSeconds(60);assertThatCode(()->limiter.request("ip",false)).doesNotThrowAnyException();
 }

 @Test void boundedStorageRejectsNewKeysUntilExpiredWithoutEvictingLiveLimits() {
  var clock=new MutableClock();var limiter=new AuthRateLimiter(clock);
  for(int i=0;i<10000;i++)limiter.request("ip"+i,true);
  assertThatThrownBy(()->limiter.request("overflow",true)).isInstanceOf(ApiException.class);
  assertThatCode(()->limiter.request("ip0",true)).doesNotThrowAnyException();
  clock.now=clock.now.plusSeconds(3600);
  assertThatCode(()->limiter.request("overflow",true)).doesNotThrowAnyException();
 }

 @Test void fullAccountStorageDoesNotBlockNewIpRequests() {
  var limiter=new AuthRateLimiter(new MutableClock());
  for(int i=0;i<10000;i++)limiter.failed("account"+i);
  assertThatThrownBy(()->limiter.failed("overflow")).isInstanceOf(ApiException.class);
  assertThatCode(()->limiter.request("new-ip",true)).doesNotThrowAnyException();
  assertThatCode(()->limiter.request("new-ip",false)).doesNotThrowAnyException();
 }
 @Test void fullIpStorageDoesNotBlockAccountOrOtherRequestType() {
  var limiter=new AuthRateLimiter(new MutableClock());
  for(int i=0;i<10000;i++)limiter.request("ip"+i,true);
  assertThatThrownBy(()->limiter.request("new-ip",true)).isInstanceOf(ApiException.class);
  assertThatCode(()->limiter.failed("new-account")).doesNotThrowAnyException();
  assertThatCode(()->limiter.request("new-ip",false)).doesNotThrowAnyException();
 }
}
