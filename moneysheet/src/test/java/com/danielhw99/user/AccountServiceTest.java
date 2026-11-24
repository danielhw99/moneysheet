package com.danielhw99.user;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Optional;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.user.service.AccountService;
import com.danielhw99.auth.service.PasswordPolicy;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
class AccountServiceTest {
 final UserRepository users=mock(UserRepository.class);
 final PasswordEncoder encoder=mock(PasswordEncoder.class);
 final EntityManager em=mock(EntityManager.class);
 final AccountService service=new AccountService(users,encoder,new PasswordPolicy(),new AuthRateLimiter(java.time.Clock.systemUTC()),em);
 User user(){var u=new User("u@example.com","u@example.com","Nick","hash","v1",LocalDateTime.now());ReflectionTestUtils.setField(u,"id",7L);when(users.findWithLockingById(7L)).thenReturn(Optional.of(u));return u;}
 @Test void nicknameChangesWithoutRevokingSession(){var u=user();assertThat(service.updateNickname(7L," New ").nickname()).isEqualTo("New");assertThat(u.getSessionVersion()).isZero();}
 @Test void emptyOrLongNicknameIsRejected(){user();for(String value:new String[]{"  ","a".repeat(51)})assertThatThrownBy(()->service.updateNickname(7L,value)).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);}
 @Test void wrongPasswordDoesNotModifyAccount(){var u=user();assertThatThrownBy(()->service.changePassword(7L,"wrong","a unique new password!")).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);assertThat(u.getPasswordHash()).isEqualTo("hash");assertThat(u.getSessionVersion()).isZero();verify(encoder,never()).encode(any());}
 @Test void passwordChangeEncodesAndRevokesAllOldVersions(){var u=user();when(encoder.matches(" current ","hash")).thenReturn(true);when(encoder.encode(" new secure phrase! ")).thenReturn("new-hash");service.changePassword(7L," current "," new secure phrase! ");assertThat(u.getPasswordHash()).isEqualTo("new-hash");assertThat(u.getSessionVersion()).isEqualTo(1);}
 @Test void weakNewPasswordDoesNotRevoke(){var u=user();when(encoder.matches("current","hash")).thenReturn(true);assertThatThrownBy(()->service.changePassword(7L,"current","short")).isInstanceOf(ApiException.class);assertThat(u.getSessionVersion()).isZero();}
 @Test void wrongPasswordCannotDelete(){user();assertThatThrownBy(()->service.deleteAccount(7L,"wrong")).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);verifyNoInteractions(em);}

 @Test void concurrentNicknameCollisionReturnsFieldConflict(){
  user();doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate",new java.sql.SQLException("Duplicate entry for key 'users.uq_users_nickname_key'","23000",1062))).when(users).flush();
  assertThatThrownBy(()->service.updateNickname(7L,"Other"))
   .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);assertThat(e.getFieldErrors()).extracting("field").containsExactly("nickname");});
 }
 @Test void legacyLongCurrentPasswordCanBeChanged(){
  var u=user();String old="legacy password longer than twenty";
  when(encoder.matches(old,"hash")).thenReturn(true);when(encoder.encode("new secure phrase!")).thenReturn("new-hash");
  service.changePassword(7L,old,"new secure phrase!");assertThat(u.getPasswordHash()).isEqualTo("new-hash");
 }

 @Test void duplicateNicknameDoesNotModifyProfile(){
  var u=user();when(users.countNicknameConflicts("Other",7L)).thenReturn(1L);
  assertThatThrownBy(()->service.updateNickname(7L," Other ")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
  assertThat(u.getNickname()).isEqualTo("Nick");assertThat(u.getSessionVersion()).isZero();verify(users,never()).flush();
 }
}
