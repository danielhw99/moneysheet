package com.danielhw99.auth.remember;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
/** Bounded maintenance; proof validity is always checked independently of cleanup. */
@Component
public class RememberCleanup {
 private final RememberTokenRepository tokens;
 private final Clock clock;
 public RememberCleanup(RememberTokenRepository tokens,Clock clock){this.tokens=tokens;this.clock=clock;}
 @Scheduled(initialDelay=3600000,fixedDelay=3600000)
 @Transactional public void removeInactive(){tokens.removeInactiveBatch(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));}
}
