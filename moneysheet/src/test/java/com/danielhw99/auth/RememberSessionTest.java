package com.danielhw99.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.danielhw99.auth.remember.RememberProof;
import com.danielhw99.auth.remember.RememberLoginToken;
import com.danielhw99.auth.remember.RememberLoginService;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.ActiveAccount;
import com.danielhw99.auth.security.RememberSessionFilter;
import com.danielhw99.user.entity.User;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
class RememberSessionTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void authenticate(AuthPrincipal p){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,List.of()));}
 @Test void revokedRememberFamilyClearsSessionAndContinuesChain() throws Exception {
  var remember=mock(RememberLoginService.class);authenticate(new AuthPrincipal(1L,"a","A",0,null,"selector"));
  var req=new MockHttpServletRequest();var session=new MockHttpSession();req.setSession(session);
  new RememberSessionFilter(remember).doFilter(req,new MockHttpServletResponse(),(r,s)->assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
  assertThat(session.isInvalid()).isTrue();
 }
 @Test void ordinarySessionDoesNotQueryProofs() throws Exception {
  var remember=mock(RememberLoginService.class);authenticate(new AuthPrincipal(1L,"a","A"));
  new RememberSessionFilter(remember).doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(r,s)->assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull());
  verifyNoInteractions(remember);
 }
 @Test void queuedBusinessWriteRechecksRememberFamilyAfterOwnerLock(){
  var now=Instant.now();var user=new User("a","a","A","hash","v1",LocalDateTime.now());ReflectionTestUtils.setField(user,"id",1L);
  var proof=RememberProof.create();var token=new RememberLoginToken(user,proof,now);
  ReflectionTestUtils.setField(user,"rememberLogins",Set.of(token));authenticate(AuthPrincipal.from(user).withRememberSelector(proof.selectorText()));
  assertThat(ActiveAccount.requireCurrent(user)).isSameAs(user);
  token.revoke(now);assertThatThrownBy(()->ActiveAccount.requireCurrent(user)).isInstanceOf(ApiException.class);
 }
}
