package com.danielhw99.auth.remember;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
public interface RememberTokenRepository extends JpaRepository<RememberLoginToken,byte[]> {
 @Modifying(flushAutomatically=true)
 @Query("update RememberLoginToken t set t.revokedAt=:now where t.user.id=:owner and t.revokedAt is null")
 int revokeAll(@Param("owner") Long owner,@Param("now") java.time.LocalDateTime now);
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 @Modifying
 @Query(value="DELETE FROM remember_login_tokens WHERE expires_at<=:now OR revoked_at IS NOT NULL LIMIT 500",nativeQuery=true)
 int removeInactiveBatch(@Param("now") java.time.LocalDateTime now);
 @Query("select t.user.id from RememberLoginToken t where t.selector=:selector")
 Optional<Long> ownerId(@Param("selector") byte[] selector);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from RememberLoginToken t where t.selector=:selector")
 Optional<RememberLoginToken> findLocked(@Param("selector") byte[] selector);
}
