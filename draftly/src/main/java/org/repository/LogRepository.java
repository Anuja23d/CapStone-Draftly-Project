package org.repository;

import org.entity.Log;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {
    List<Log> findByDraftId(Long draftId);
    
    List<Log> findByStatus(String status);
}

