package org.repository;

import org.entity.Email;
import org.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailRepository extends JpaRepository<Email, Long> {
    List<Email> findByUser(User user);
    
    Optional<Email> findByThreadId(String threadId);

    Optional<Email> findByUserAndMessageId(User user, String messageId);
    
    List<Email> findBySender(String sender);
}

