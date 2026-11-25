package com.danielhw99.auth.remember;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
/** Bounded maintenance; proof validity is always checked independently of cleanup. */
@Component
public class RememberCleanup {
 private final RememberTokenRepository tokens;
 private final Clock clock;
 public RememberCleanup(RememberTokenRepository tokens,Clock clock){this.tokens=tokens;this.clock=clock;}
 @Scheduled(initialDelay=3600000,fixedDelay=3600000)
 public void removeInactive(){
  var cutoff=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
  // The repository proxy commits each batch separately; do not wrap this loop in one transaction.
  while(tokens.removeInactiveBatch(cutoff)==500){ /* Continue until the last partial/empty batch. */ }
 }
}
