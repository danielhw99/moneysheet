package com.danielhw99.summary;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.Optional;
import com.danielhw99.balance.service.DailyBalanceCheckService;
import com.danielhw99.balance.repository.DailyBalanceCheckRepository;
import com.danielhw99.balance.entity.DailyBalanceCheck;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.Test;
class DailyBalanceCheckServiceTest {
 final DailyBalanceCheckRepository rows=mock(DailyBalanceCheckRepository.class);
 final UserRepository users=mock(UserRepository.class);
 final LocalDate date=LocalDate.of(2026,11,17);
 final DailyBalanceCheckService service=new DailyBalanceCheckService(rows,users,Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"),ZoneOffset.UTC));
 @Test void missingDateIsDistinctFromZero() {
  when(rows.findByUserIdAndDate(1L,date)).thenReturn(Optional.empty());
  assertThat(service.get(1L,date)).isEqualTo(new DailyBalanceCheckService.Result(date,null,true));
  var user=mock(User.class);
  when(users.findWithLockingById(1L)).thenReturn(Optional.of(user));
  when(rows.save(any())).thenAnswer(i->i.getArgument(0));
  assertThat(service.save(1L,date,0L,true).actualBalance()).isZero();
 }
 @Test void readsOnlyRequestedOwnerAndUpdatesExistingRow() {
  var row=new DailyBalanceCheck(mock(User.class),date);
  when(rows.findByUserIdAndDate(2L,date)).thenReturn(Optional.of(row));
  when(users.findWithLockingById(2L)).thenReturn(Optional.of(mock(User.class)));
  when(rows.save(row)).thenReturn(row);
  service.save(2L,date,100L,false);
  assertThat(service.get(2L,date).actualBalance()).isEqualTo(100L);
  verify(rows,never()).findByUserIdAndDate(1L,date);
 }
 @Test void rejectsFutureAndInvalidAmountBeforeWrite() {
  assertThatThrownBy(()->service.get(1L,date.plusDays(1))).isInstanceOf(ApiException.class);
  for(long amount:new long[]{-1,10000000000L}) assertThatThrownBy(()->service.save(1L,date,amount,true)).isInstanceOf(ApiException.class);
  verifyNoInteractions(users);
 }
}
