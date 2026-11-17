package com.danielhw99.user.service;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.security.ActiveAccount;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.auth.service.PasswordPolicy;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountService {
 private final UserRepository users;
 private final PasswordEncoder encoder;
 private final PasswordPolicy policy;
 private final AuthRateLimiter limiter;
 private final EntityManager em;
 public AccountService(UserRepository users,PasswordEncoder encoder,PasswordPolicy policy,AuthRateLimiter limiter,EntityManager em) {
  this.users=users;this.encoder=encoder;this.policy=policy;this.limiter=limiter;this.em=em;
 }
 private User lock(Long id) {return ActiveAccount.requireCurrent(users.findWithLockingById(id).orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED)));}
 @Transactional public Profile updateNickname(Long id,String nickname) {
  var user=lock(id);
  String value=nickname==null?"":nickname.strip();
  if(value.isEmpty() || value.codePointCount(0,value.length())>50) throw new ApiException(ErrorCode.VALIDATION);
  user.rename(value);return Profile.from(user);
 }
 @Transactional public void changePassword(Long id,String currentPassword,String newPassword) {
  var user=lock(id);confirm(user,currentPassword);policy.validate(newPassword);user.changePassword(encoder.encode(newPassword));
 }
 private void confirm(User user,String password) {
  if(password==null || password.isEmpty() || password.codePointCount(0,password.length())>128) throw new ApiException(ErrorCode.VALIDATION);
  try(var attempt=limiter.beginAttempt(user.getEmailKey())) {
   if(!encoder.matches(password,user.getPasswordHash())) {attempt.failed();throw new ApiException(ErrorCode.INVALID_CREDENTIALS);}
   attempt.succeeded();
  }
 }
 @Transactional public void deleteAccount(Long id,String currentPassword) {
  var user=lock(id);confirm(user,currentPassword);
  em.flush();
  delete("DELETE p FROM fixed_payments p JOIN fixed_budget_items i ON i.id=p.fixed_budget_item_id JOIN monthly_budgets b ON b.id=i.monthly_budget_id WHERE b.user_id=:owner",id);
  for(String table:new String[]{"main_transactions","money_memos","monthly_cash_flows","fixed_budget_items"})
   delete("DELETE c FROM "+table+" c JOIN monthly_budgets b ON b.id=c.monthly_budget_id WHERE b.user_id=:owner",id);
  delete("DELETE FROM daily_balance_checks WHERE user_id=:owner",id);
  delete("DELETE FROM monthly_budgets WHERE user_id=:owner",id);
  delete("DELETE FROM categories WHERE user_id=:owner",id);
  em.remove(user);em.flush();
 }
 private void delete(String sql,Long id) {em.createNativeQuery(sql).setParameter("owner",id).executeUpdate();}
}
