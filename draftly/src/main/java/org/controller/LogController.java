package org.controller;

import org.repository.LogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs")
public class LogController {

    @Autowired
    private LogRepository logRepository;

    @GetMapping("/draft/{draftId}")
    public Object getDraftLogs(@PathVariable("draftId") Long draftId) {
        return logRepository.findByDraftId(draftId);
    }
}

