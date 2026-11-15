package com.danielhw99.user;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import com.danielhw99.auth.security.*;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class AccountSessionTest {
 @AfterEach void clean(){SecurityContextHolder.clearContext();}
 void auth(long version){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AuthPrincipal(7L,"a@example.com","A",version),null,List.of()));}
 @Test void changedPasswordAndDeletedUserCannotReuseOldSessions() throws Exception {
  for(boolean removed:new boolean[]{false,true}) {
   auth(0);var users=mock(UserRepository.class);var user=mock(User.class);when(user.getSessionVersion()).thenReturn(1L);when(users.findById(7L)).thenReturn(removed?Optional.empty():Optional.of(user));
   var request=new MockHttpServletRequest();var session=new MockHttpSession();request.setSession(session);
   new AccountSessionFilter(users).doFilter(request,new MockHttpServletResponse(),(req,res)->assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
   assertThat(session.isInvalid()).isTrue();
  }
 }
 @Test void currentVersionKeepsAuthentication() throws Exception {
  auth(3);var users=mock(UserRepository.class);var user=mock(User.class);when(user.getSessionVersion()).thenReturn(3L);when(users.findById(7L)).thenReturn(Optional.of(user));
  new AccountSessionFilter(users).doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(req,res)->assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull());
 }
 @Test void waitingWriteRechecksVersionAfterLock(){auth(0);var user=mock(User.class);when(user.getId()).thenReturn(7L);when(user.getSessionVersion()).thenReturn(1L);assertThatThrownBy(()->ActiveAccount.requireCurrent(user)).isInstanceOf(ApiException.class);}
 @Test void secretInputsAreRedacted(){assertThat(new com.danielhw99.user.api.AccountInputs.Password("secret-current","secret-new").toString()).doesNotContain("secret");assertThat(new com.danielhw99.user.api.AccountInputs.Deletion("secret-current").toString()).doesNotContain("secret");}
}
