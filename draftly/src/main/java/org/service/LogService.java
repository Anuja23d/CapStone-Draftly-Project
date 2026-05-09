package org.service;

import org.entity.Log;
import org.repository.LogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogService {

    @Autowired
    private LogRepository logRepository;

    public void log(Long draftId, String status, String message) {
        Log l = new Log();
        l.setDraftId(draftId);
        l.setStatus(status);
        l.setMessage(message);
        l.setTimestamp(LocalDateTime.now());
        logRepository.save(l);
    }
}

