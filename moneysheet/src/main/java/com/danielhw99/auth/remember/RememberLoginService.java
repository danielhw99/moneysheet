package com.danielhw99.auth.remember;
import java.time.Clock;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class RememberLoginService {
 private final UserRepository users;private final RememberTokenRepository tokens;private final Clock clock;
 public RememberLoginService(UserRepository users,RememberTokenRepository tokens,Clock clock){this.users=users;this.tokens=tokens;this.clock=clock;}
 @Transactional public RememberGrant login(AuthPrincipal authenticated,boolean enabled,String previousCookie){
  // Password validation may have raced with a password change; recheck under the owner lock.
  var user=lockOwners(authenticated.userId(),previousCookie).get(authenticated.userId());
  if(user==null)throw new ApiException(ErrorCode.UNAUTHENTICATED);
  if(!user.isEmailVerified()||user.getSessionVersion()!=authenticated.sessionVersion())throw new ApiException(ErrorCode.UNAUTHENTICATED);
  revokePresented(previousCookie);
  var principal=AuthPrincipal.from(user);
  if(!enabled)return new RememberGrant(principal,null,null);
  var proof=RememberProof.create();var token=new RememberLoginToken(user,proof,clock.instant());tokens.saveAndFlush(token);
  return new RememberGrant(principal.withRememberSelector(proof.selectorText()),proof.cookie(),token.expiresAt());
 }
 @Transactional(readOnly=true) public boolean sessionValid(AuthPrincipal principal){
  if(principal.rememberSelector()==null)return true;
  var selector=RememberProof.selectorBytes(principal.rememberSelector());if(selector.isEmpty())return false;
  var token=tokens.findById(selector.get());if(token.isEmpty())return false;
  var user=users.findById(principal.userId());
  return user.isPresent()&&user.get().isEmailVerified()&&user.get().getSessionVersion()==principal.sessionVersion()
    &&token.get().getUser().getId().equals(principal.userId())&&token.get().active(clock.instant(),principal.sessionVersion());
 }
 @Transactional public void logout(AuthPrincipal principal,String cookie){
  var locked=lockOwners(principal==null?null:principal.userId(),cookie);
  if(principal!=null&&locked.containsKey(principal.userId()))
   RememberProof.selectorBytes(principal.rememberSelector()).flatMap(tokens::findLocked)
    .filter(t->t.getUser().getId().equals(principal.userId())).ifPresent(t->t.revoke(clock.instant()));
  revokePresented(cookie);
 }
 // AccountService already holds the owner lock; join its password-change transaction.
 @Transactional public void revokeAll(Long owner){tokens.revokeAll(owner,java.time.LocalDateTime.ofInstant(clock.instant(),java.time.ZoneOffset.UTC));}
 private java.util.Map<Long,com.danielhw99.user.entity.User> lockOwners(Long target,String cookie){
  var ids=new java.util.TreeSet<Long>();if(target!=null)ids.add(target);
  RememberProof.parse(cookie).flatMap(p->tokens.ownerId(p.selector())).ifPresent(ids::add);
  var result=new java.util.HashMap<Long,com.danielhw99.user.entity.User>();
  for(Long id:ids)users.findWithLockingById(id).ifPresent(u->result.put(id,u));
  return result;
 }
 private void revokePresented(String cookie){
  var proof=RememberProof.parse(cookie);if(proof.isEmpty())return;
  // A different account's old browser cookie is locked independently, never by guessed selector alone.
  tokens.findLocked(proof.get().selector()).filter(t->t.matches(proof.get())||t.previousMatches(proof.get())).ifPresent(t->t.revoke(clock.instant()));
 }

 @Transactional(readOnly=true) public AuthPrincipal current(AuthPrincipal principal){
  var user=users.findById(principal.userId()).orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED));
  if(!user.isEmailVerified()||user.getSessionVersion()!=principal.sessionVersion())throw new ApiException(ErrorCode.UNAUTHENTICATED);
  return AuthPrincipal.from(user).withRememberSelector(principal.rememberSelector());
 }
 @Transactional public RememberGrant restore(String cookie){
  var proof=RememberProof.parse(cookie).orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED));
  var locked=lockOwners(null,cookie);
  var token=tokens.findLocked(proof.selector()).orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED));
  var user=locked.get(token.getUser().getId());
  if(user==null||!user.isEmailVerified()||!token.active(clock.instant(),user.getSessionVersion()))throw new ApiException(ErrorCode.UNAUTHENTICATED);
  if(!token.matches(proof)){
   if(token.previousMatches(proof)&&token.previousWithinGrace(clock.instant()))throw new ApiException(ErrorCode.CONFLICT);
   throw new ApiException(ErrorCode.UNAUTHENTICATED);
  }
  var next=proof.next();token.rotate(next,clock.instant());tokens.flush();
  return new RememberGrant(AuthPrincipal.from(user).withRememberSelector(proof.selectorText()),next.cookie(),token.expiresAt());
 }
}
