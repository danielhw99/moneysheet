package com.danielhw99.auth.remember;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface RememberTokenRepository extends JpaRepository<RememberLoginToken,byte[]> {
 @Query("select t.user.id from RememberLoginToken t where t.selector=:selector")
 Optional<Long> ownerId(@Param("selector") byte[] selector);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from RememberLoginToken t where t.selector=:selector")
 Optional<RememberLoginToken> findLocked(@Param("selector") byte[] selector);
}
