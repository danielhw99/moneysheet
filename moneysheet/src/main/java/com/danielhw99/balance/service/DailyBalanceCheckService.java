package com.danielhw99.balance.service;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import com.danielhw99.balance.entity.DailyBalanceCheck;
import com.danielhw99.balance.repository.DailyBalanceCheckRepository;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.auth.security.ActiveAccount;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DailyBalanceCheckService {
 public record Result(LocalDate date,Long actualBalance,boolean locked) {}
 private final DailyBalanceCheckRepository records;
 private final UserRepository users;
 private final Clock clock;
 public DailyBalanceCheckService(DailyBalanceCheckRepository records,UserRepository users,Clock clock) {this.records=records;this.users=users;this.clock=clock;}
 private void validate(LocalDate date) {
  if(date==null || date.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))))) throw new ApiException(ErrorCode.VALIDATION);
 }
 @Transactional(readOnly=true) public Result get(Long owner,LocalDate date) {
  validate(date);
  return records.findByUserIdAndDate(owner,date).map(this::result).orElse(new Result(date,null,true));
 }
 @Transactional public Result save(Long owner,LocalDate date,Long amount,boolean locked) {
  validate(date);
  if(amount!=null && (amount<0 || amount>9999999999L)) throw new ApiException(ErrorCode.VALIDATION);
  var user=ActiveAccount.requireCurrent(users.findWithLockingById(owner).orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED)));
  var record=records.findByUserIdAndDate(owner,date).orElseGet(()->new DailyBalanceCheck(user,date));
  record.update(amount,locked);
  return result(records.save(record));
 }
 private Result result(DailyBalanceCheck record) {return new Result(record.getDate(),record.getActualBalance(),record.isLocked());}
}
