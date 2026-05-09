package org.repository;

import org.entity.Draft;
import org.entity.Email;
import org.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface DraftRepository extends JpaRepository<Draft, Long> {

    List<Draft> findByStatus(String status);

    Optional<Draft> findByIdempotencyKey(String idempotencyKey);

}

