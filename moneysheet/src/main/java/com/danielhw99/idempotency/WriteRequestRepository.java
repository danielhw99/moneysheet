package com.danielhw99.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WriteRequestRepository extends JpaRepository<WriteRequest,Long> {
    Optional<WriteRequest> findByUser_IdAndRequestKey(Long owner,String key);
}
