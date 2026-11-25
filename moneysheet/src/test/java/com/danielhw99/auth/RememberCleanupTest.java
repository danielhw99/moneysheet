package com.danielhw99.auth;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.danielhw99.auth.remember.RememberCleanup;
import com.danielhw99.auth.remember.RememberTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
class RememberCleanupTest {
 final RememberTokenRepository tokens=mock(RememberTokenRepository.class);
 final Clock clock=Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"),ZoneOffset.UTC);
 final LocalDateTime cutoff=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
 @Test void fewerThanOneBatchRunsOnlyOnce(){
  for(int count:new int[]{0,12,499}){
   reset(tokens);when(tokens.removeInactiveBatch(cutoff)).thenReturn(count);
   new RememberCleanup(tokens,clock).removeInactive();verify(tokens,times(1)).removeInactiveBatch(cutoff);
  }
 }
 @Test void drainsFullBatchesUntilPartialBatch(){
  when(tokens.removeInactiveBatch(cutoff)).thenReturn(500,500,12);
  new RememberCleanup(tokens,clock).removeInactive();verify(tokens,times(3)).removeInactiveBatch(cutoff);
 }
 @Test void exactMultipleStopsAfterEmptyBatch(){
  when(tokens.removeInactiveBatch(cutoff)).thenReturn(500,0);
  new RememberCleanup(tokens,clock).removeInactive();verify(tokens,times(2)).removeInactiveBatch(cutoff);
 }
 @Test void eachRepositoryBatchRequiresItsOwnTransaction() throws Exception {
  var transaction=RememberTokenRepository.class.getMethod("removeInactiveBatch",LocalDateTime.class).getAnnotation(Transactional.class);
  assertThat(transaction).isNotNull();assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
 }
}
